# KANAK FMCG Bahrain — Authoritative Cloud Backend

## 1. Overview
This is the authoritative multi-tenant cloud backend for **KANAK**, an enterprise FMCG wholesale and multi-van distribution platform designed for the Kingdom of Bahrain.

### Technology Stack
- **Framework:** FastAPI (Python 3.11)
- **Database:** PostgreSQL 16 with SQLAlchemy 2.0 ORM
- **Migration Engine:** Alembic
- **Financial Engine:** Exact 3-decimal precision (`Decimal("0.001")`) adhering to Central Bank of Bahrain & National Bureau for Revenue (NBR) regulations
- **Containerization:** Multi-stage Dockerfile and Docker Compose
- **Invoice PDF Engine:** Vector A4 ReportLab rendering compliant with NBR Tax Invoice requirements

---

## 2. Master Platform Administrator
- **Owner Email:** `zanabalmuhamed@gmail.com`
- **Role:** Elevated automatically to `SUPER_ADMIN` with platform-wide administrative privileges across all registered tenant companies.

---

## 3. Financial Invariants (Kingdom of Bahrain)
- **Currency:** Bahraini Dinar (BHD), 1 BHD = 1,000 fils.
- **VAT Rate:** 10.0% standard (`Decimal("0.100")`) or 0.0% zero-rated staple commodities.
- **Selling Price:** Input prices are VAT-inclusive standard wholesale prices.
- **Line Formula:**
  - `Gross = Quantize(Quantity × UnitPrice)`
  - `Net = Quantize(Gross / 1.100)`
  - `VAT = Gross - Net`
  - `Grand Total = Sum(Gross) == Sum(Net) + Sum(VAT)`

---

## 4. Multi-Van Stock & Ledger Tracking
Stock movements are strictly append-only in `stock_ledger`:
- `PURCHASE_INTAKE`: Supplier intake into main warehouse.
- `VAN_LOAD`: Transfer from warehouse custody to van driver custody.
- `VAN_SALE`: Sale made by van representative to wholesale/retail customer.
- `VAN_UNLOAD`: End-of-shift reconciliation and return of stock to warehouse.
- `AUDIT_ADJUSTMENT`: Management stock count adjustment with reason audit.

---

## 5. API Endpoints
- `POST /api/v1/auth/verify`: Google Sign-In / Firebase token validation and tenant resolution.
- `GET /api/v1/products`: Filterable product catalog with automatic Bing image fallback.
- `POST /api/v1/products`: Create wholesale product with barcode.
- `GET /api/v1/invoices`: List posted invoices by tenant/van.
- `POST /api/v1/invoices`: Post sales invoice with stock deduction and customer ledger posting.
- `GET /api/v1/invoices/{id}/pdf`: Render downloadable vector A4 Bahrain Tax Invoice.
- `GET /api/v1/fleet/vans`: List fleet delivery vans.
- `POST /api/v1/fleet/vans/transfer`: Execute stock loading/unloading between warehouse and vans.
- `GET /api/v1/customers`: Customer directory with credit limit and balance tracking.
- `GET /api/v1/suppliers`: Supplier directory and purchasing ledger.
- `POST /api/v1/sync/outbox/flush`: Idempotent batch sync handler for Android Room Outbox.
- `GET /api/v1/admin/telemetry`: Platform health, sync logs, and multi-tenant performance metrics.

---

## 6. Deployment & Execution
```bash
# Start PostgreSQL and API service
docker compose up -d --build

# Run financial and ledger tests
python3 -m unittest discover -s backend/tests
```
