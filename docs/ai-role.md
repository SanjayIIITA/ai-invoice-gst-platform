# What AI Does in This Platform

> Written for a non-technical reviewer.
> No jargon. Just what the problem was, what AI does, and why it matters.

---

## The Short Answer

AI does **two things** in this platform:

```
  1.  Reads a PDF invoice and pulls out the important data fields
  2.  Explains validation errors in plain English to the reviewer
```

That's it. Everything else — checking tax math, verifying formats, approving, rejecting — is done by code, not AI.

---

## Why AI Is Needed At All

When a company sends an invoice, it comes as a **PDF file**.

A PDF is not a spreadsheet. There is no column called "Invoice Number" that code can just read. It's a document — text laid out visually for humans to read.

The problem is every supplier designs their invoice differently:

```
  Supplier A                    Supplier B                    Supplier C
  ─────────────────────         ─────────────────────         ─────────────────────
  Invoice No: INV-001           Inv# INV-001                  Bill Number: INV-001
  Date: 15-Oct-2024             Invoice Date: 2024/10/15      Dt. 15.10.2024
  Total: Rs 11,800              Amount Due: ₹11800            Grand Total: 11800.00
```

Same information. Three completely different layouts.

A traditional program would need a custom rule written for every single supplier. The moment a supplier changes their template — the rule breaks. This is not scalable.

**AI solves this.** It reads the text the way a human would — understanding meaning, not just matching patterns.

---

## AI Role 1 — Reading the Invoice (Extraction)

### What happens step by step

```
  ┌─────────────────────────────────────────────────────────────────────┐
  │                                                                     │
  │   PDF uploaded                                                      │
  │       │                                                             │
  │       ▼                                                             │
  │   PDFBox reads the PDF → extracts raw text                         │
  │       │                                                             │
  │       ▼                                                             │
  │   Raw text sent to AI with a prompt                                 │
  │       │                                                             │
  │       ▼                                                             │
  │   AI reads the text, understands it, returns structured data        │
  │       │                                                             │
  │       ▼                                                             │
  │   Platform saves the clean data to the database                     │
  │                                                                     │
  └─────────────────────────────────────────────────────────────────────┘
```

### Before AI — what the system sees

```
  Raw text from PDF (messy, unstructured):
  ─────────────────────────────────────────────────────────────────────
  ABC Technologies Pvt Ltd
  GSTIN: 29ABCDE1234F1Z5
  Invoice No: INV-2024-001   Date: 15-Oct-2024
  Description: Software Services
  Taxable Amount: Rs 10,000
  GST @18%: Rs 1,800
  Total Amount: Rs 11,800
  ─────────────────────────────────────────────────────────────────────
```

### After AI — what the database gets

```
  Clean structured data:
  ─────────────────────────────────────────────────────────────────────
  Invoice Number  :  INV-2024-001
  Supplier Name   :  ABC Technologies Pvt Ltd
  Supplier GSTIN  :  29ABCDE1234F1Z5
  Invoice Date    :  2024-10-15
  Taxable Amount  :  10000.00
  Tax Amount      :  1800.00
  Total Amount    :  11800.00
  ─────────────────────────────────────────────────────────────────────
```

### What AI is actually doing here

The platform sends the raw text to the AI with clear instructions:

> *"You are an invoice data extraction assistant. Extract these fields: invoice number, supplier name, supplier GSTIN, invoice date, taxable amount, tax amount, total amount. Return only the data. If a field is not found, return null."*

The AI reads the text, understands what each piece of information means, and returns the data in a clean format that the platform can save to the database.

**No human needed. No custom rules per supplier. Works on any invoice layout.**

---

## AI Role 2 — Explaining Errors (Explanation)

After the invoice data is extracted, the platform runs 6 automated checks on it — things like:
- Is the tax math correct?
- Is the GSTIN in the right format?
- Has this invoice been submitted before?

When a check fails, it creates a **finding** — a technical error record that looks like this:

```
  Technical finding (what the system generates):
  ─────────────────────────────────────────────────────────────────────
  [CRITICAL]  DUPLICATE_INVOICE    — invoice number already exists
  [LOW]       INVOICE_DATE_TOO_OLD — invoice date is more than 1 year old
  ─────────────────────────────────────────────────────────────────────
```

A finance reviewer looking at this would need to know what `DUPLICATE_INVOICE` means and what to do about it. That's not their job — they shouldn't need to decode system codes.

**This is where AI's second role comes in.**

The platform sends these findings to AI and asks it to explain them in plain English:

```
  AI explanation (what the reviewer sees):
  ─────────────────────────────────────────────────────────────────────
  "There are 2 issues with this invoice:

   1. Duplicate Invoice (Critical)
      An invoice with the same number already exists from this supplier.
      This may have been submitted twice. Please verify with the supplier
      before approving.

   2. Old Invoice Date (Low)
      This invoice is dated more than a year ago. Confirm it is still
      valid for your current accounting period before approving."
  ─────────────────────────────────────────────────────────────────────
```

**Same information. But now a finance reviewer can act on it immediately — no technical knowledge needed.**

---

## Summary — What AI Does vs What Code Does

```
  ┌─────────────────────────────────┬──────────────────────────────────┐
  │  AI handles                     │  Code (Rules) handles            │
  ├─────────────────────────────────┼──────────────────────────────────┤
  │  Reading unstructured PDF text  │  Checking tax math               │
  │  Understanding different layouts│  Verifying GSTIN format          │
  │  Extracting invoice fields      │  Checking GST rate is valid      │
  │  Explaining errors in English   │  Detecting duplicate invoices    │
  │                                 │  Checking invoice date           │
  │                                 │  Approving / Rejecting           │
  └─────────────────────────────────┴──────────────────────────────────┘
```

### Why not use AI for everything?

Tax math and format checks need to be **exact and auditable**. If AI says a tax calculation "looks correct" and it's wrong — that's a GST compliance issue. Rules are deterministic: 10,000 + 1,800 = 11,800, always, with no room for error.

AI is used only where **human-like understanding** is needed:
- Understanding messy, unstructured text → Extraction
- Communicating technical results in plain language → Explanation

---

## Business Value

```
  Without AI                           With AI
  ──────────────────────────────────────────────────────────────────
  Someone manually reads each PDF      Platform reads it automatically
  Types data into a system             Data is extracted in seconds
  Checks tax math by hand              6 rules check it instantly
  Writes rejection notes manually      AI explains issues in plain English
  Misses duplicates sometimes          Duplicate check never misses

  Time per invoice:  15–20 minutes     Time per invoice:  under 1 minute
  Error rate:        human error risk  Error rate:        deterministic rules
```

---

## One Line for Each AI Call

| When | What AI receives | What AI returns |
|------|-----------------|-----------------|
| After PDF is uploaded | Raw text from the PDF | 7 structured data fields |
| After validation runs | List of technical error codes | Plain English explanation |
