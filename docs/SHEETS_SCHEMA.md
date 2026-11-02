# Kanak — Google Sheets Datastore Schema

Google Sheets is the sole authoritative business datastore for **Kanak**.

The spreadsheet contains exactly eight worksheets:

| Worksheet | Purpose | Authority |
|---|---|---|
| `Products` | Master product catalogue, prices, VAT, and van stock | Authoritative SKU definitions |
| `Customers` | Retail customer registry (cold stores, supermarkets, groceries) | Authoritative accounts |
| `Suppliers` | Wholesale FMCG distributors and suppliers | Authoritative vendors |
| `Purchases` | Stock replenishment log (admin only) | Inventory-increase contributions |
| `Invoices` | Completed van sales transactions | Immutable posted financial records |
| `InvoiceLines` | Line items captured at the moment of invoice creation | Historical tax snapshot |
| `StockAdjustments` | Manual inventory corrections with required reasons (admin only) | Signed inventory deltas |
| `Users` | Access control and role assignment (`admin` or `standard`) | Server-side authorization |

---

## 1. Products Worksheet

Exact column order:
1. `product_id` (UUID string) — Primary key
2. `barcode` (String) — Preserves leading zeros; unique across SKUs
3. `name` (String) — Full product description
4. `cost_price` (Decimal BHD) — Wholesale purchase cost
5. `selling_price` (Decimal BHD) — VAT-inclusive customer selling price
6. `vat_rate` (Decimal) — Default `0.10` (10% Bahrain standard) or `0.00` (zero-rated)
7. `vat_amount` (Decimal BHD) — Derived VAT per selling unit
8. `photo_url` (String) — Direct HTTPS image reference
9. `stock_qty` (Decimal) — Current available van inventory

---

## 2. Customers Worksheet

Exact column order:
1. `customer_id` (UUID string)
2. `business_name` (String)
3. `contact` (String)
4. `address` (String)
5. `phone` (String)

---

## 3. Suppliers Worksheet

Exact column order:
1. `supplier_id` (UUID string)
2. `name` (String)
3. `contact` (String)

---

## 4. Purchases Worksheet

Exact column order:
1. `purchase_id` (UUID string)
2. `supplier_id` (UUID string)
3. `product_id` (UUID string)
4. `qty` (Decimal)
5. `cost` (Decimal BHD)
6. `date` (ISO UTC timestamp)
7. `created_by` (Verified user subject ID)

---

## 5. Invoices Worksheet

Exact column order:
1. `invoice_id` (UUID string)
2. `invoice_number` (String, e.g. `INV-2026-0001`)
3. `customer_id` (UUID string)
4. `customer_name` (String) — Historical snapshot
5. `date` (ISO timestamp in `Asia/Bahrain`)
6. `subtotal` (Decimal BHD) — Net sum
7. `vat_total` (Decimal BHD) — 10% VAT sum
8. `total` (Decimal BHD) — Gross total
9. `status` (`POSTED` or `VOID`)
10. `created_by` (Verified user ID)
11. `voided_at` (Optional ISO timestamp)

---

## 6. InvoiceLines Worksheet

Exact column order:
1. `line_id` (UUID string)
2. `invoice_id` (UUID string)
3. `product_id` (UUID string)
4. `product_name` (String) — Historical snapshot
5. `qty` (Decimal)
6. `unit_price` (Decimal BHD) — VAT-inclusive price
7. `vat_rate` (Decimal)
8. `vat_amount` (Decimal BHD)
9. `line_total` (Decimal BHD)

---

## 7. StockAdjustments Worksheet

Exact column order:
1. `adjustment_id` (UUID string)
2. `product_id` (UUID string)
3. `delta_qty` (Signed decimal)
4. `reason` (String)
5. `date` (ISO UTC timestamp)
6. `created_by` (Verified user ID)

---

## 8. Users Worksheet

Exact column order:
1. `user_id` (String) — Google account subject (`sub`)
2. `email` (String) — Informational
3. `role` (`admin` or `standard`)
4. `active` (Boolean)
