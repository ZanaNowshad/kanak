# Kanak — Security Architecture & Role Enforcement

Implements Section 6 of the Master Autonomous Android Engineering Directive.

---

## 1. Zero Direct Credential Exposure

- The Android application never contains or stores privileged Google Sheets owner credentials, service account private keys, or refresh tokens.
- All mutating business operations flow through a trusted Google Apps Script gateway that executes as the authorized spreadsheet owner.

---

## 2. Server-Enforced Role Matrix

| Operation | Standard Driver | Administrator |
|---|---|---|
| View Dashboard & Sales | Yes | Yes |
| View Product Catalogue & Van Stock | Yes | Yes |
| Create & Edit Products | Yes | Yes |
| Delete Unreferenced Products | Yes | Yes |
| Customer CRUD | Yes | Yes |
| Supplier CRUD | Yes | Yes |
| Create Van Invoices | Yes | Yes |
| Print & Reprint Receipts | Yes | Yes |
| Void Invoices (Restore Stock) | **No** (Denied) | **Yes** |
| Record Purchases (Stock In) | **No** (Denied) | **Yes** |
| Manual Stock Adjustments | **No** (Denied) | **Yes** |
| Change User Roles | **No** (Spreadsheet only) | **No** (Spreadsheet only) |

---

## 3. Invariant & Concurrency Protections

- **Script Locking (`LockService.getScriptLock()`):** Prevents concurrent races during simultaneous sales or purchase writes.
- **Strict Invariant (`stock_qty >= 0`):** The server rejects any transaction or manual adjustment that would cause inventory to become negative.
- **Idempotency:** UUID-based mutation tracking ensures network timeouts and retry requests do not cause duplicate stock deductions or duplicate invoice rows.
