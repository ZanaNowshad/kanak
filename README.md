# KANAK — Bahrain FMCG Van Sales & Fleet POS Application

**'Kanak' by zedosai.com** is a native Android application designed specifically for wholesale FMCG van sales and distribution operations in the Kingdom of Bahrain.

---

## Production Features

- **Multi-Tenant Fleet Architecture:** Independent business isolation with Central Warehouse inventory, individual delivery van tracking (`VAN-001`, `VAN-002`, etc.), salesperson driver assignment, and van stock replenishment/reconciliation.
- **Enterprise Employee Access:** Role-Based Access Control (`ROLE_OWNER`, `ROLE_MANAGER`, `ROLE_SALESPERSON`) with cryptographic employee invitation codes (`KNK-XXXX`) allowing team members to join existing licensed businesses without duplicate licensing fees.
- **Dual-Layer Persistence & Offline Capability:** Full on-device SQLite caching powered by Android Jetpack Room with automatic offline sale recording, coupled with real-time Firebase Firestore cloud backup and Google Sheets sync.
- **Authoritative Google Sheets Datastore:** 8 worksheets (`Products`, `Customers`, `Suppliers`, `Purchases`, `Invoices`, `InvoiceLines`, `StockAdjustments`, `Users`) connected via a trusted Google Apps Script Web App Gateway.
- **Bahrain Currency & VAT:** Exact 3-decimal BHD arithmetic using `BigDecimal`. Automatic 10% standard Bahrain VAT derivation with zero-rated and credit invoice support, line-to-header balance reconciliation, and live daily sales summary reporting.
- **Primary Van Sales Workflow:** Fluid single-flow sequence:
  `Select Customer → Add Products → Set Quantities → Review Live Totals (Cash/Credit) → Save → Print ESC/POS Thermal Receipt`.
- **Customer Credit & Debt Ledger:** Customer detail view with historical purchases, credit balances, and inline bill payment processing for unpaid invoices.
- **Hardware Integration:**
  - Real Bluetooth RFCOMM Serial Port Profile (SPP) connection to standard 58mm and 80mm ESC/POS thermal printers.
  - Native Android PDF Print Framework (`PrintManager`) for wireless network/office receipt printing.
  - Zero-permission Android Photo Picker for product images with automated Bing thumbnail URL resolution.
- **Licensing & Verification:** Company registration with CR/TRN validation, SHA-256 activation key generation, offline key storage, and master admin WhatsApp (`+97333050666`) / Email (`zanabalmuhamed@gmail.com`) direct approval integration.

---

## Configuration & Environment Variables

| Variable | Description | Location |
| :--- | :--- | :--- |
| `APPS_SCRIPT_GATEWAY_URL` | Google Apps Script Web App URL for Google Sheets datastore sync. | Configured via `.env`, AI Studio Secrets, or in-app **Settings > Datastore Gateway**. |
| `GEMINI_API_KEY` | (Optional) Gemini API key for smart product cataloging. | Configured via AI Studio Secrets. |

---

## Building the Application

Compile and build the release and debug APKs:
```bash
# Debug APK
gradle assembleDebug

# Release APK
gradle assembleRelease
```
Outputs are generated in `app/build/outputs/apk/` and `/.build-outputs/`.
