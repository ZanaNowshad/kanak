# Kanak — Verification Evidence & Testing Report

This document reports verification results for the Kanak Van Sales application.

---

## 1. Compilation & Build Verification

- **Gradle Build Task:** `:app:assembleDebug`
- **Kotlin Compiler:** Kotlin 2.2.10 with Jetpack Compose Compiler Plugin
- **Android Gradle Plugin:** AGP 9.1.1, compileSdk 36
- **Result:** Build succeeded.

---

## 2. Unit & Financial Test Coverage

The test suite in `/app/src/test/java/com/example/` validates:
- Exact 3-decimal BHD financial calculations (`BigDecimal`).
- Bahrain 10% VAT calculation from VAT-inclusive unit selling prices.
- Reconciliation: `subtotal + vat_total == total`.
- Canonical Bing Thumbnail URL generation (special characters, whitespace normalization, UTF-8 form encoding).
- Idempotency and inventory mutation rules.
- ESC/POS byte sequence generation and line wrapping.

---

## 3. Hardware & External Dependencies Status

- **Android Bluetooth Printing:** Implemented with RFCOMM SPP socket connection and fallback `SimulatedPrinterTransport`. Verified byte rendering and layout. Physical printer verification requires pairing with physical hardware on site.
- **Google Sheets Gateway:** Fully written and ready to deploy in `/apps-script/`. Seed data bundled in the application allows full offline and immediate out-of-the-box operation.
