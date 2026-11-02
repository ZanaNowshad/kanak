# Kanak — Bluetooth ESC/POS Thermal Printing Setup

Implements Section 15 of the Master Autonomous Android Engineering Directive.

---

## 1. Supported Thermal Hardware

- Standard 58mm mobile Bluetooth thermal printers (32 columns per line).
- Standard 80mm mobile Bluetooth thermal printers (48 columns per line).
- Standard Bluetooth Serial Port Profile (SPP) with UUID:
  `00001101-0000-1000-8000-00805F9B34FB`

---

## 2. Receipt Layout Format

```text
KANAK
KANAK FMCG WHOLESALE W.L.L.
Building 844, Road 2415, Block 324, Manama, Bahrain
VAT TRN: BH200098765400
--------------------------------
Invoice: INV-2026-0001
Date: 2026-10-30 14:30
Customer: Al-Hilal Cold Store
--------------------------------
Product         Qty      Total
--------------------------------
Almarai Milk 2L   2      2.200
Lurpak Butter     1      1.250
--------------------------------
Subtotal                 3.136
VAT Total (10%)          0.314
TOTAL (BHD)              3.450
--------------------------------
Thank you for your business!
```

---

## 3. Pairing and Printing Steps

1. Turn on your Bluetooth thermal printer.
2. In Android **Settings → Connected devices → Pair new device**, select your printer and complete pairing (default PINs are usually `0000` or `1234`).
3. Open **Kanak → Settings → Bluetooth Thermal Printer**.
4. Tap **Scan** to refresh paired devices.
5. Select your printer from the list.
6. Tap **Send Test Print to Thermal Printer** to verify ESC/POS communication.
7. Upon completing a sale in **New Invoice**, tap **Print Receipt** or **Reprint Receipt** on the invoice details screen.
