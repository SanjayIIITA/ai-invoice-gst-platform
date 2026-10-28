# Threading, Async & Non-Blocking Processing — AI Invoice GST Platform
### Presentation & Interview Ready Document

---

## Part 1 — Threading (The Mechanism)

### What is a Thread

A thread is the smallest unit of execution inside a Java process.
When your Spring Boot app starts, the JVM creates multiple threads automatically.
Every piece of code that runs — runs on some thread.

```
JVM Process (your Spring Boot app)
  │
  ├── main thread          ← starts the application
  ├── tomcat-thread-1      ← handles HTTP request 1
  ├── tomcat-thread-2      ← handles HTTP request 2
  ├── invoice-async-1      ← runs background extraction
  ├── GC thread            ← garbage collection
  └── ... many more
```

All threads share the same memory (heap) but each has its own stack.

---

### Thread Lifecycle

A Java thread goes through these states:

```
  NEW
   │
   ▼
  RUNNABLE  ←──────────────────────────┐
   │                                   │
   ├── gets CPU time → RUNNING         │
   │                                   │
   ▼                                   │
  BLOCKED          ← waiting for a lock (synchronized block)
   │                                   │
  WAITING          ← waiting for notify() or join()
   │                                   │
  TIMED_WAITING    ← waiting for sleep() or timeout
   │                                   │
   └──────────────────────────────────►┘
   │
   ▼
  TERMINATED  ← thread finished execution
```

In your app, async threads spend most time in TIMED_WAITING
(waiting for Groq API response over the network).

---

### Thread Pool — Why Not Create Threads Manually

Creating a new thread for every request is expensive:
- Thread creation takes ~1ms and significant memory (~1MB stack per thread)
- 1000 requests = 1000 threads = ~1GB memory just for stacks
- OS struggles to schedule thousands of threads

A thread pool solves this by reusing threads:

```
  Thread Pool
  ┌─────────────────────────────────────────┐
  │  thread-1  [WAITING for task]           │
  │  thread-2  [RUNNING task]               │  ← reused, not recreated
  │  thread-3  [WAITING for task]           │
  └─────────────────────────────────────────┘

  Task arrives → assign to waiting thread → thread runs task → goes back to waiting
```

---

### ThreadPoolTaskExecutor in Your Code

**File: AsyncConfig.java**

```java
@Bean(name = "invoiceTaskExecutor")
public Executor invoiceTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);      // 10 threads always alive
    executor.setMaxPoolSize(50);       // grows to 50 under load
    executor.setQueueCapacity(500);    // 500 tasks can wait in queue
    executor.setThreadNamePrefix("invoice-async-");
    executor.initialize();
    return executor;
}
```

How the pool decides what to do when a task arrives:

```
  Task arrives
      │
      ▼
  Core thread free?  ──YES──►  assign to core thread
      │
      NO
      │
      ▼
  Queue not full?  ──YES──►  add to queue (wait for free thread)
      │
      NO
      │
      ▼
  Can create new thread? (below maxPoolSize)  ──YES──►  create thread
      │
      NO
      │
      ▼
  RejectedExecutionException  ← all 50 threads busy + 500 queue full
```

Thread lifecycle in the pool:

```
  Core threads (10)    →  created at startup, never die
  Extra threads (>10)  →  created when queue full, die after 60s idle
```

---

## Part 2 — Blocking vs Non-Blocking (The Concept)

This is what your manager was referring to. Threading is the tool.
Blocking vs Non-Blocking is the actual concept being applied.

### Blocking

A thread is BLOCKED when it is waiting for something and cannot do anything else.

```
  Thread starts task
      │
      ▼
  Calls Groq API  ──────────────────────────────────────────────►  response
      │                                                              │
      │  thread is BLOCKED here — sitting idle, doing nothing       │
      │  for 3–8 seconds                                            │
      │◄─────────────────────────────────────────────────────────────
      │
      ▼
  Thread continues
```

The thread is alive but doing zero useful work while waiting.
This is wasted capacity.

### Non-Blocking

A thread starts a task, does NOT wait for it, and moves on immediately.
The result is handled later when it is ready.

```
  Thread starts task
      │
      ▼
  Hand off to background thread  ──────────────────────────────►  background thread
      │                                                              works independently
      ▼
  Thread continues immediately ← does not wait
  Returns response to client
```

The thread is free to handle other requests while the slow work happens elsewhere.

### Side by Side Comparison

```
  BLOCKING (before)                    NON-BLOCKING (after)
  ─────────────────────────────────    ──────────────────────────────────
  Thread handles upload                Thread handles upload
  Thread waits for PDFBox              Thread fires background task
  Thread waits for Groq AI             Thread returns response ← FREE
  Thread returns response
  Thread is FREE

  Time: 6,000–16,000ms                 Time: ~200ms
  Thread busy: entire duration         Thread busy: ~200ms only
```

---

## Part 3 — Synchronous vs Asynchronous (The Technique)

### Synchronous

Steps happen one after another. Step 2 cannot start until Step 1 finishes.
The caller waits for the result.

```
  upload()
    │
    ├── store file        (wait for it to finish)
    ├── save to DB        (wait for it to finish)
    ├── extract PDF       (wait for it to finish)
    ├── call Groq AI      (wait 5–8 seconds)
    └── return response   ← only now does client get a response
```

### Asynchronous

The caller starts a task and does NOT wait for it to finish.
The task runs independently and notifies when done (or updates DB).

```
  upload()
    │
    ├── store file        (wait — fast, ~50ms)
    ├── save to DB        (wait — fast, ~10ms)
    ├── fire extractAsync ──────────────────────────────► runs independently
    └── return response   ← client gets response NOW     on background thread
                                                          PDFBox + Groq AI
                                                          updates DB when done
```

### How @Async Implements This in Your Code

**File: InvoiceUploadService.java**

```java
// SYNCHRONOUS part — runs on HTTP thread, fast
@Transactional
public InvoiceResponse upload(MultipartFile file, Organization org) throws Exception {
    String storageKey = fileStorageService.store(file);   // ~50ms — wait
    invoice.setStatus(InvoiceStatus.EXTRACTING);
    invoiceRepository.save(invoice);                      // ~10ms — wait

    self.extractAsync(invoice.getId());                   // fire and move on — NO wait

    return InvoiceResponse.from(invoice);                 // returns ~200ms after request
}

// ASYNCHRONOUS part — runs on invoiceTaskExecutor thread, slow
@Async("invoiceTaskExecutor")
public void extractAsync(Long invoiceId) {
    // this runs on invoice-async-N thread, completely independent
    // HTTP thread is already free and serving other requests
    String text = pdfExtractionService.extractText(pdfPath);   // ~100ms
    InvoiceExtractionResult result = invoiceAiService.extractInvoiceFields(text); // ~5,000ms
    invoiceRepository.save(invoice);   // saves EXTRACTED status to DB
}
```

`@Async("invoiceTaskExecutor")` tells Spring:
- Do not run this method on the calling thread
- Submit it to the `invoiceTaskExecutor` thread pool
- Return immediately to the caller

---

## Part 4 — Producer-Consumer Pattern (The Design Pattern)

What you built is a classic **Producer-Consumer pattern**.

### Definition

- Producer — creates tasks and puts them in a queue
- Queue — holds tasks waiting to be processed
- Consumer — picks tasks from the queue and processes them

### In Your App

```
  PRODUCER                    QUEUE                    CONSUMER
  ────────────────────        ─────────────────────    ──────────────────────
  upload() method             ThreadPoolTaskExecutor   extractAsync() method
                              internal task queue
  Creates extraction task  →  task waits here      →  invoice-async thread
  Returns immediately         (up to 500 tasks)        picks it up
                                                        runs PDFBox + Groq AI
                                                        saves result to DB
```

### Flow Diagram

```
  Client
    │
    │  POST /upload
    ▼
  Tomcat Thread (Producer)
    │
    ├── store PDF
    ├── save Invoice (EXTRACTING)
    ├── put task in queue  ──────────────────────────────────────────────►
    └── return 201 { status: EXTRACTING }                                │
                                                                         │
  Client gets response in ~200ms                                         │
                                                                         ▼
                                                              Task Queue (500 capacity)
                                                                         │
                                                                         ▼
                                                              invoice-async Thread (Consumer)
                                                                         │
                                                                ├── PDFBox extract text
                                                                ├── Groq AI extract fields
                                                                ├── save Invoice (EXTRACTED)
                                                                └── task done ✅

  Client polls GET /invoices/{id}  ──►  { status: EXTRACTED }
```

---

## Part 5 — The Self-Injection Trick (Important Interview Topic)

### Why It Is Needed

`@Async` works through Spring AOP (Aspect Oriented Programming) proxies.
Spring wraps your bean in a proxy object that intercepts method calls.

```
  Normal call through proxy:
  Caller  ──►  Spring Proxy  ──►  actual extractAsync()
                    │
                    └── proxy intercepts, submits to thread pool ✅

  Self-call using 'this':
  upload()  ──►  this.extractAsync()
                    │
                    └── bypasses proxy entirely, runs synchronously ❌
```

When `upload()` calls `this.extractAsync()`, it calls the real object directly,
not the proxy. The `@Async` annotation is completely ignored.
The method runs on the same HTTP thread — blocking it for 5–8 seconds.

### The Fix

```java
@Autowired @Lazy
private InvoiceUploadService self;   // injects the Spring PROXY, not 'this'

// In upload():
self.extractAsync(invoice.getId());  // goes through proxy → async works ✅
```

`@Lazy` is required to break the circular dependency.
Without it, Spring tries to inject the bean into itself during construction
and throws a `BeanCurrentlyInCreationException`.

---

## Part 6 — Two Thread Pools Working Together

Your app runs two completely separate thread pools simultaneously:

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  TOMCAT THREAD POOL                                              │
  │  Purpose: handle incoming HTTP requests                          │
  │  Max threads: 400                                                │
  │  Min spare: 20 (always ready)                                    │
  │  Accept queue: 200 (requests waiting when all threads busy)      │
  │                                                                  │
  │  Each thread:                                                    │
  │    receives request → processes → sends response → FREE (~200ms) │
  └──────────────────────────────────────────────────────────────────┘
                │
                │ fires async task
                ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │  INVOICE ASYNC THREAD POOL (invoiceTaskExecutor)                 │
  │  Purpose: run slow AI extraction in background                   │
  │  Core threads: 10                                                │
  │  Max threads: 50                                                 │
  │  Queue: 500                                                      │
  │                                                                  │
  │  Each thread:                                                    │
  │    picks task → PDFBox → Groq AI → save DB → FREE (~5–8 seconds)│
  └──────────────────────────────────────────────────────────────────┘
```

They are independent. Tomcat threads are never blocked by AI processing.

---

## Part 7 — HikariCP — Database Connection Pool

Both thread pools need database connections.
HikariCP manages a pool of reusable PostgreSQL connections.

```properties
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=20000
```

```
  Thread needs DB access
        │
        ▼
  Borrow connection from HikariCP pool
        │
        ▼
  Execute query
        │
        ▼
  Return connection to pool  ← connection reused, not closed
```

Without connection pooling, every thread would open and close a new TCP
connection to PostgreSQL for every query — extremely expensive (~50ms per connection).
With HikariCP, borrowing a connection takes ~1ms.

---

## Part 8 — Complete Picture Under Load

```
  500 users upload invoices simultaneously

  ┌─────────────────────────────────────────────────────────────────┐
  │  Tomcat (400 threads max)                                       │
  │                                                                 │
  │  400 requests processed immediately                             │
  │  100 requests wait in accept queue (200 capacity)               │
  │                                                                 │
  │  Each Tomcat thread finishes in ~200ms                          │
  │  → picks up next waiting request                                │
  │  → all 500 uploads done in ~300ms total                         │
  └─────────────────────────────────────────────────────────────────┘
        │
        │  500 async tasks fired
        ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │  Invoice Async Pool                                             │
  │                                                                 │
  │  10 core threads pick up first 10 tasks immediately             │
  │  490 tasks sit in queue                                         │
  │  As each thread finishes (~5s), picks next from queue           │
  │  Pool grows to 50 threads if queue fills                        │
  │                                                                 │
  │  All 500 extractions complete in ~50 seconds                    │
  │  (500 tasks ÷ 50 threads × 5s each)                            │
  └─────────────────────────────────────────────────────────────────┘
```

---

## Part 9 — Advanced Interview Questions and Answers

### Section A — Follow-up Questions Interviewers Ask

**Q: Why did you choose @Async over CompletableFuture?**

A: `CompletableFuture` is the right choice when you need the result back in the same request — for example, calling two APIs in parallel and combining their results. In our case, the upload response goes back to the client immediately and the extraction result is saved to the DB independently. The client polls for the result later. This is a fire-and-forget pattern — `@Async` is the cleaner fit. `CompletableFuture` would add unnecessary complexity since nobody is waiting for the return value.

```
  Use CompletableFuture when:          Use @Async when:
  caller needs the result back         caller does NOT need the result
  parallel calls, combine results      fire and forget
  chain dependent async steps          background processing
```

---

**Q: What happens if Groq API fails inside the async thread?**

A: The `extractAsync()` method has a try-catch block. If Groq API throws any exception, the catch block sets the invoice status to `FAILED` and saves it to the DB. The client sees `FAILED` on the next poll. Currently there is no retry mechanism — that would be the next improvement using Spring's `@Retryable` annotation.

```java
// In extractAsync():
try {
    InvoiceExtractionResult result = invoiceAiService.extractInvoiceFields(text);
    invoice.setStatus(InvoiceStatus.EXTRACTED);
} catch (Exception e) {
    invoice.setStatus(InvoiceStatus.FAILED);   // ← Groq failure handled here
    run.fail("EXTRACTION_ERROR");
}
```

With `@Retryable` the improvement would look like:

```java
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
public InvoiceExtractionResult extractInvoiceFields(String rawText) { ... }
```

This retries the Groq call up to 3 times with a 2 second gap before giving up.

---

**Q: Is your async thread transaction-aware?**

A: Yes, but with an important distinction. `extractAsync()` runs on a completely different thread from the HTTP request. Spring does NOT propagate the original transaction across threads — each thread gets its own transaction context. So `extractAsync()` starts a fresh independent transaction when it calls `invoiceRepository.save()`. This is correct behaviour — we want the extraction result committed independently, not tied to the upload transaction.

```
  HTTP Thread                          Async Thread
  ─────────────────────────────        ──────────────────────────────
  Transaction 1 starts                 Transaction 2 starts (new)
  store PDF                            fetch invoice from DB
  save invoice (EXTRACTING)            run PDFBox + Groq AI
  Transaction 1 commits ✅             save invoice (EXTRACTED)
  return response                      Transaction 2 commits ✅

  Two separate transactions — correct and independent
```

---

**Q: Polling vs WebSockets — what is the trade-off?**

A: Polling means the client repeatedly calls `GET /invoices/{id}` every few seconds to check the status. WebSockets keep a persistent connection open — the server pushes a notification the moment extraction completes.

```
  POLLING (current approach)
  ─────────────────────────────────────────────────────
  Client  →  GET /invoices/13  →  EXTRACTING  (3s wait)
  Client  →  GET /invoices/13  →  EXTRACTING  (3s wait)
  Client  →  GET /invoices/13  →  EXTRACTED   ✅

  Simple to implement
  Creates repeated HTTP requests — wasteful
  Acceptable for invoice platform (not real-time critical)

  WEBSOCKETS (future improvement)
  ─────────────────────────────────────────────────────
  Connection open
  Async thread finishes  →  server pushes { status: EXTRACTED }  →  client receives instantly

  Efficient — no repeated requests
  Complex to implement
  Better for real-time dashboards
```

For an invoice processing platform, polling every 3–5 seconds is perfectly acceptable. WebSockets would be overkill unless you need a live dashboard updating in real time.

---

### Section B — Original Interview Questions and Answers

**Q: What is the difference between blocking and non-blocking?**

**Q: What is the difference between blocking and non-blocking?**
A: In blocking, a thread waits idle until an operation completes — wasting CPU time and holding the thread hostage. In non-blocking, the thread fires the operation and immediately moves on. The result is handled later. In our platform, the HTTP thread was previously blocked for 6–16 seconds waiting for Groq AI. After the change, it fires the task to a background thread and returns in 200ms.

---

**Q: What is the difference between synchronous and asynchronous?**
A: Synchronous means operations execute sequentially — each step waits for the previous one. Asynchronous means a task is started and the caller does not wait for its result. We made AI extraction asynchronous using `@Async` so the upload response is returned immediately while extraction runs independently.

---

**Q: What design pattern did you implement?**
A: Producer-Consumer pattern. The HTTP thread (producer) creates an extraction task and puts it in the thread pool queue. The async thread (consumer) picks it up and processes it independently. This decouples the fast HTTP layer from the slow AI processing layer.

---

**Q: Why did you use self-injection with @Lazy?**
A: `@Async` works through Spring AOP proxies. Calling `this.extractAsync()` bypasses the proxy and runs synchronously. By injecting `self` — which is the Spring proxy — the call goes through the proxy and async behaviour is honoured. `@Lazy` prevents circular dependency during bean construction.

---

**Q: What is ThreadPoolTaskExecutor?**
A: It is Spring's abstraction over Java's `ThreadPoolExecutor`. It manages a pool of reusable threads with configurable core size, max size, and queue capacity. We named ours `invoiceTaskExecutor` and dedicated it specifically to AI extraction tasks, keeping it separate from Tomcat's HTTP thread pool.

---

**Q: What happens when the async queue is full?**
A: Spring throws `RejectedExecutionException`. The task is dropped. To handle this gracefully, a `RejectedExecutionHandler` can be configured — for example `CallerRunsPolicy` which runs the task on the calling thread as a fallback instead of dropping it.

---

**Q: Why not just use a new Thread() instead of @Async?**
A: `new Thread()` creates an unmanaged thread with no pooling, no size control, and no lifecycle management. Under load, thousands of unmanaged threads would crash the JVM. `@Async` with a configured executor uses a managed pool — threads are reused, size is bounded, and Spring handles lifecycle, transaction context, and security context propagation.

---

**Q: How does the client know when async extraction is complete?**
A: The upload returns `status: EXTRACTING` immediately. The async thread updates the DB to `EXTRACTED` when done. The client polls `GET /invoices/{id}` periodically. This is called a polling pattern. An alternative is WebSockets for push notification when extraction completes.

---

## Part 11 — Known Limitation — Server Restart During Extraction

This is a gap you should raise yourself in the interview. It shows you understand the limits of your own design.

### The Problem

The `invoiceTaskExecutor` thread pool holds tasks in memory.
If the server restarts while extractions are in progress, all queued and running tasks are lost.
Those invoices stay stuck in `EXTRACTING` status forever — nobody processes them.

```
  10 invoices being extracted
  Server crashes or restarts
        ↓
  Thread pool destroyed — all 10 tasks lost
        ↓
  DB still shows status = EXTRACTING
        ↓
  Client polls forever — status never changes
        ↓
  Invoice stuck — never becomes EXTRACTED or FAILED
```

### Fix 1 — Startup Recovery (Simple)

On application startup, find all invoices stuck in `EXTRACTING` and re-queue them.

```java
@Component
public class ExtractionRecoveryService {

    @EventListener(ApplicationReadyEvent.class)   // runs after app fully starts
    public void recoverStuckInvoices() {
        List<Invoice> stuck = invoiceRepository.findByStatus(InvoiceStatus.EXTRACTING);
        stuck.forEach(invoice -> self.extractAsync(invoice.getId()));
        // re-queues all stuck invoices into the thread pool
    }
}
```

This is simple and works well for single-instance deployments.

### Fix 2 — Persistent Queue (Production Grade)

Replace the in-memory thread pool queue with a persistent message queue like AWS SQS or RabbitMQ.
Messages survive server restarts because they are stored externally, not in memory.

```
  Current (in-memory queue):
  upload()  →  ThreadPoolTaskExecutor queue (RAM)  →  extractAsync()
                      ↑
              lost on restart

  With persistent queue:
  upload()  →  SQS / RabbitMQ (external, persistent)  →  extractAsync()
                      ↑
              survives restart, message stays until processed
```

### How to Raise This in an Interview

Do not wait for the interviewer to find this gap. Raise it yourself:

> "One limitation I am aware of in the current design is that the async thread pool is in-memory.
> If the server restarts during extraction, tasks in the queue are lost and those invoices
> get stuck in EXTRACTING status. I have two solutions in mind — a simple startup recovery
> that re-queues stuck invoices on boot, and a production-grade fix using a persistent
> message queue like SQS so tasks survive restarts entirely."

This shows maturity — you built something, you know its limits, and you know how to fix it.

---

## Part 12 — Terminology Cheat Sheet

```
  ┌─────────────────────────┬──────────────────────────────────────────────┐
  │ Term                    │ Meaning in this platform                     │
  ├─────────────────────────┼──────────────────────────────────────────────┤
  │ Thread                  │ Unit of execution — worker that runs code    │
  │ Thread Pool             │ Reusable group of threads                    │
  │ Blocking                │ Thread waiting idle for slow operation       │
  │ Non-Blocking            │ Thread moves on, does not wait               │
  │ Synchronous             │ Steps run one after another, caller waits    │
  │ Asynchronous            │ Task fired, caller does not wait for result  │
  │ @Async                  │ Spring annotation to run method on bg thread │
  │ @EnableAsync            │ Enables async support in Spring Boot         │
  │ Producer-Consumer       │ Design pattern — one creates, one processes  │
  │ AOP Proxy               │ Spring wrapper that intercepts method calls  │
  │ Self-injection          │ Injecting own proxy to honour @Async         │
  │ HikariCP                │ DB connection pool — reuses DB connections   │
  │ Tomcat thread pool      │ Handles HTTP requests                        │
  │ invoiceTaskExecutor     │ Handles background AI extraction             │
  │ CompletableFuture       │ Async when caller needs result back          │
  │ @Retryable              │ Retry failed operations automatically        │
  │ Transaction context     │ Each async thread gets its own transaction   │
  │ Polling                 │ Client repeatedly checks status              │
  │ WebSockets              │ Server pushes status update to client        │
  │ Startup recovery        │ Re-queue stuck tasks after server restart    │
  │ Persistent queue        │ SQS/RabbitMQ — tasks survive server restart  │
  └─────────────────────────┴──────────────────────────────────────────────┘
```

---

## Summary

Threading is the **mechanism** — threads are workers that execute code.

Blocking vs Non-Blocking is the **concept** — whether a thread waits or moves on.

Synchronous vs Asynchronous is the **technique** — whether the caller waits for the result.

Producer-Consumer is the **design pattern** — decoupling task creation from task processing.

In this platform, all four work together:
- Threads (mechanism) are managed in two pools — Tomcat and invoiceTaskExecutor
- Non-blocking (concept) — HTTP thread never waits for AI
- Asynchronous (technique) — `@Async` fires extraction independently
- Producer-Consumer (pattern) — upload produces task, async thread consumes it
