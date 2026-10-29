# Scalability Guide — AI Invoice GST Platform

---

## The Core Problem

Every invoice upload currently does this **inside a single HTTP request thread**:

```
Client sends PDF
      │
      ▼
Thread is LOCKED until all of this finishes:
      │
      ├── Store PDF to disk              ~50ms
      ├── PDFBox extract text            ~100ms
      ├── Groq AI call #1 (raw)          ~3,000–8,000ms   ← blocks thread
      └── Groq AI call #2 (entity)       ~3,000–8,000ms   ← blocks thread again (bug)
      │
      ▼
Thread is finally FREE  (total: ~6–16 seconds per request)
```

Tomcat gives you **200 threads** by default.
At 16 seconds per request, you can handle at most **200 ÷ 16 = ~12 requests/second**.
At 1000 concurrent users, 800 of them are waiting or getting errors.

---

## Bottleneck Map — Ranked by Impact

```
  ┌──────────────────────────────┬──────────────┬────────────────────────────────┐
  │ Bottleneck                   │ Impact       │ Location                       │
  ├──────────────────────────────┼──────────────┼────────────────────────────────┤
  │ Groq AI called TWICE per req │ CRITICAL     │ InvoiceAiService.java L36–44   │
  │ AI calls are synchronous     │ CRITICAL     │ InvoiceUploadService.java L29  │
  │ HikariCP pool = 10 (default) │ HIGH         │ application.properties         │
  │ Tomcat threads = 200         │ HIGH         │ application.properties         │
  │ Files stored on local disk   │ MEDIUM       │ FileStorageService.java        │
  │ No caching on repeated reads │ MEDIUM       │ InvoiceService.java            │
  │ JWT validated on every req   │ LOW          │ JwtAuthFilter.java             │
  └──────────────────────────────┴──────────────┴────────────────────────────────┘
```

---

## Level 1 — Fix the Bugs First (Zero Infrastructure Cost)

These are code-level fixes that give immediate gains before touching any infrastructure.

### Fix 1 — Remove the Duplicate AI Call

`InvoiceAiService.java` calls Groq **twice** for the same prompt. The first call result is logged and thrown away.

```
Current (broken):
  chatClient.prompt().call().content()    ← calls Groq, result discarded
  chatClient.prompt().call().entity()     ← calls Groq again for same thing

Fix:
  chatClient.prompt().call().entity()     ← one call only
```

This alone **halves your AI cost and halves your response time** with zero infrastructure change.

### Fix 2 — Tune the DB Connection Pool

Default HikariCP gives 10 connections. With 200 Tomcat threads all trying to hit the DB, 190 threads are waiting.

Add to `application.properties`:

```properties
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=20000
```

### Fix 3 — Tune Tomcat Threads

```properties
server.tomcat.threads.max=400
server.tomcat.threads.min-spare=20
server.tomcat.accept-count=200
```

---

## Level 2 — Async Processing (The Real Fix)

This is the most impactful architectural change. The idea is simple:

```
BEFORE (synchronous — thread blocked for 16 seconds):
  POST /upload  ──wait 16s──►  200 OK { status: EXTRACTED }

AFTER (async — thread free in 200ms):
  POST /upload  ──200ms──►  201 Created { status: EXTRACTING }
                             AI runs in background thread
  GET /invoices/{id}  ──►  { status: EXTRACTED }  ← client polls
```

Your `InvoiceStatus` enum already has `EXTRACTING` and `EXTRACTED` — the design is ready for this.

### How It Works

```
  ┌─────────┐   POST /upload    ┌─────────────────┐
  │ Client  │ ───────────────►  │  Upload Thread  │
  └─────────┘                   │                 │
       │                        │  1. Save PDF    │
       │   201 EXTRACTING       │  2. Save Invoice│
       │ ◄─────────────────     │  3. Return ✅   │
       │                        └────────┬────────┘
       │                                 │ fires async task
       │                        ┌────────▼────────┐
       │                        │  Worker Thread  │
       │                        │                 │
       │                        │  4. PDFBox      │
       │                        │  5. Groq AI     │
       │                        │  6. Save result │
       │                        └─────────────────┘
       │
       │   GET /invoices/{id}
       │ ───────────────────►  { status: EXTRACTED }
```

### Implementation — Spring @Async

**Step 1** — Enable async in your main application class:

```java
@SpringBootApplication
@EnableAsync
public class AiInvoiceGstPlatformApplication { ... }
```

**Step 2** — Define a thread pool bean:

```java
@Bean
public Executor invoiceTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("invoice-async-");
    executor.initialize();
    return executor;
}
```

**Step 3** — Make the AI extraction async:

```java
@Async("invoiceTaskExecutor")
public CompletableFuture<Void> extractAsync(Long invoiceId, Long orgId) {
    // PDFBox + Groq AI runs here in background thread
    // saves result back to DB when done
}
```

**Step 4** — Upload returns immediately, client polls status:

```
POST /upload          → 201 { id: 42, status: "EXTRACTING" }
GET  /invoices/42     → 200 { id: 42, status: "EXTRACTING" }  (still processing)
GET  /invoices/42     → 200 { id: 42, status: "EXTRACTED"  }  (done)
```

---

## Level 3 — Caching with Redis

Once async is in place, the next win is caching repeated reads.

### What to Cache

```
  ┌──────────────────────────────┬──────────────────────────────────────┐
  │ What                         │ Why                                  │
  ├──────────────────────────────┼──────────────────────────────────────┤
  │ GET /invoices/{id}           │ Same invoice read many times by      │
  │                              │ multiple reviewers                   │
  ├──────────────────────────────┼──────────────────────────────────────┤
  │ GET /invoices/{id}/findings  │ Findings don't change after validate │
  ├──────────────────────────────┼──────────────────────────────────────┤
  │ GET /reports/summary         │ Expensive aggregation query,         │
  │                              │ acceptable to be 60s stale           │
  ├──────────────────────────────┼──────────────────────────────────────┤
  │ JWT validation result        │ Same token validated on every req,   │
  │                              │ DB hit each time                     │
  └──────────────────────────────┴──────────────────────────────────────┘
```

### How It Works

```
  Request for invoice #42
        │
        ▼
  Redis has it? ──YES──► return cached response (< 1ms)
        │
        NO
        │
        ▼
  Hit PostgreSQL (~10ms)
        │
        ▼
  Store in Redis with TTL
        │
        ▼
  Return response
```

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Add to `application.properties`:

```properties
spring.cache.type=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

---

## Level 4 — Horizontal Scaling (Multiple Instances)

Your JWT design is already stateless — tokens carry all user/org info, no server-side session.
This means you can run **multiple instances** behind a load balancer with zero code changes.

```
                    ┌─────────────────┐
                    │  Load Balancer  │
                    │  (Nginx / ALB)  │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │Instance 1│  │Instance 2│  │Instance 3│
        │ port 8080│  │ port 8081│  │ port 8082│
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │              │              │
             └──────────────┼──────────────┘
                            │
                   ┌────────▼────────┐
                   │   PostgreSQL    │
                   │  (shared DB)    │
                   └─────────────────┘
```

### One Problem — Local File Storage

`app.storage.upload-dir=uploads` stores files on the local disk of whichever instance handled the upload.
Instance 2 cannot read a file stored by Instance 1.

```
  BROKEN with multiple instances:
  Client ──► Instance 1 ──► saves to /uploads/abc.pdf
  Client ──► Instance 2 ──► tries to read /uploads/abc.pdf  ← FILE NOT FOUND
```

Fix: move file storage to **MinIO** — a self-hosted, S3-compatible object store that runs in Docker.
All instances read/write to the same MinIO container. No instance owns the files.

```
Instance 1 ──┐
Instance 2 ──┼──► MinIO container (shared, S3-compatible)
Instance 3 ──┘
```

MinIO is already added to `docker-compose.yml` and `FileStorageService.java` has been updated.
Start it with:

```bash
docker compose up -d minio
```

MinIO web UI is available at `http://localhost:9001` (user: minioadmin / pass: minioadmin).

Config in `application.properties`:

```properties
app.storage.endpoint=http://localhost:9000
app.storage.access-key=minioadmin
app.storage.secret-key=minioadmin
app.storage.bucket=invoices
```

If you ever move to AWS later, only the config changes — `FileStorageService.java` code stays identical since MinIO uses the same S3 API.

---

## Level 5 — Queue-Based Processing (Maximum Scale)

For very high volume (tens of thousands of invoices/day), replace `@Async` with a proper message queue.

```
  POST /upload
       │
       ▼
  Save PDF to S3
  Save Invoice record (status: EXTRACTING)
  Publish message to SQS queue ──► return 201 immediately
       │
       │  (completely decoupled)
       │
       ▼
  SQS Consumer (separate service or Lambda)
       │
       ├── Read PDF from S3
       ├── PDFBox extract text
       ├── Groq AI extract fields
       └── Update Invoice in DB (status: EXTRACTED)
```

### Why This Is Better Than @Async

```
  ┌──────────────────────────┬──────────────────────────────────────────┐
  │ @Async                   │ SQS Queue                                │
  ├──────────────────────────┼──────────────────────────────────────────┤
  │ Task lost if app crashes │ Message stays in queue until processed   │
  │ Bounded by app memory    │ Queue can hold millions of messages      │
  │ Scales with app instance │ Consumer scales independently            │
  │ Simple to implement      │ More infrastructure to set up            │
  └──────────────────────────┴──────────────────────────────────────────┘
```

---

## Scaling Roadmap — What to Do in Order

```
  ┌─────┬──────────────────────────────────────┬──────────────┬──────────────────┐
  │Step │ Action                               │ Effort       │ Gain             │
  ├─────┼──────────────────────────────────────┼──────────────┼──────────────────┤
  │  1  │ Remove duplicate Groq AI call        │ 5 minutes    │ 2x speed, 2x     │
  │     │ InvoiceAiService.java L36            │              │ cost saving      │
  ├─────┼──────────────────────────────────────┼──────────────┼──────────────────┤
  │  2  │ Tune HikariCP + Tomcat threads       │ 5 minutes    │ 3x DB throughput │
  │     │ application.properties               │              │                  │
  ├─────┼──────────────────────────────────────┼──────────────┼──────────────────┤
  │  3  │ Make AI extraction @Async            │ 1–2 days     │ 10x concurrency  │
  │     │ InvoiceUploadService + @EnableAsync  │              │                  │
  ├─────┼──────────────────────────────────────┼──────────────┼──────────────────┤
  │  4  │ Add Redis cache for reads            │ 1 day        │ 5x read speed    │
  │     │ InvoiceService + ReportService       │              │                  │
  ├─────┼──────────────────────────────────────┼──────────────┼──────────────────┤
  │  5  │ Move file storage to S3              │ 1–2 days     │ enables multi-   │
  │     │ FileStorageService                   │              │ instance deploy  │
  ├─────┼──────────────────────────────────────┼──────────────┼──────────────────┤
  │  6  │ Run 2–3 instances behind load        │ 1 day        │ linear scale     │
  │     │ balancer (needs S3 first)            │              │ with instances   │
  ├─────┼──────────────────────────────────────┼──────────────┼──────────────────┤
  │  7  │ Replace @Async with SQS queue        │ 3–5 days     │ unlimited scale, │
  │     │ for AI extraction                    │              │ fault tolerant   │
  └─────┴──────────────────────────────────────┴──────────────┴──────────────────┘
```

Steps 1 and 2 cost nothing and take 10 minutes.
Steps 3 and 4 handle thousands of concurrent users.
Steps 5, 6, 7 are for production-grade scale.

---

## Capacity Estimate After Each Step

```
  Current (no changes)          ~12  requests/second
  After Step 1 + 2              ~40  requests/second
  After Step 3 (async)          ~200 requests/second
  After Step 4 (Redis)          ~500 requests/second
  After Step 6 (3 instances)    ~1500 requests/second
  After Step 7 (SQS)            ~unlimited (queue absorbs all spikes)
```

---

## Key Principle

> The AI call is the slowest part of your system (3–8 seconds).
> Everything else (DB, PDFBox, JWT) is fast (< 100ms).
> All scaling efforts should focus on making the AI call non-blocking.
> Once it is async, the rest of the system scales naturally.
