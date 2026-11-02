# KANAK — Bahrain FMCG Van Sales Application

**Kanak** is a production-grade native Android application designed specifically for wholesale FMCG van sales operations in the Kingdom of Bahrain.

---

## Key Highlights

- **Authoritative Google Sheets Datastore:** 8 worksheets (`Products`, `Customers`, `Suppliers`, `Purchases`, `Invoices`, `InvoiceLines`, `StockAdjustments`, `Users`) connected via a trusted Google Apps Script Gateway.
- **Bahrain Currency & VAT:** Exact 3-decimal BHD arithmetic using `BigDecimal`. Automatic 10% standard VAT derivation from VAT-inclusive pricing, zero-rated product support, and guaranteed line-to-header reconciliation.
- **Primary Van Sales Workflow:** One fluid sequence:
  `Select Customer → Add Products → Set Quantities → Review Live Totals → Save → Print Thermal Receipt`.
- **Automatic Product Photography:** Canonical Bing thumbnail candidate URL generator (`barcode + " " + product_name`), with manual URL paste and Android Photo Picker image upload.
- **ESC/POS Bluetooth Thermal Printing:** Mobile receipt printing on 58mm/80mm thermal printers via standard Bluetooth SPP, with monospace receipt preview, retry capabilities, and reprint actions.
- **Role Enforcement:** Server-enforced permissions distinguishing `admin` (purchases, stock adjustments, invoice voiding) from `standard` van sales drivers.
- **Apple-Inspired Design:** Clean system typography, restrained color palette, generous spacing, and touch-down feedback.

---

## Project Structure

```text
Kanak/
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt
│   │   ├── auth/                # Credential Manager Google Sign-In & Session
│   │   ├── domain/              # Product, Customer, Invoice, BHD Financials
│   │   ├── data/                # Remote API, Repository, and Offline Caching
│   │   ├── images/              # Canonical Bing Thumbnail Builder & Picker
│   │   ├── printing/            # Bluetooth SPP Manager & ESC/POS Renderer
│   │   └── ui/                  # Compose UI (Dashboard, Invoices, Catalogue, Stock)
│   └── src/test/java/           # Financial, Image, and Printing Unit Tests
├── apps-script/                 # Production Google Apps Script Web App Gateway
└── docs/                        # Complete setup, schema, and security documentation
```

---

## Quick Start

1. **Build & Run:** Open the project in Android Studio or compile with Gradle:
   ```bash
   gradle assembleDebug
   ```
2. **Demo Accounts:** On first launch, tap **Admin Role** or **Sales Driver** to explore all features immediately with pre-loaded Bahrain FMCG inventory (Almarai milk, Tiffany biscuits, Lurpak butter, Rani juice).
3. **Connect Google Sheets:** Follow `/docs/GOOGLE_SETUP.md` to deploy your Apps Script Web App and enter the URL in **Settings**.
