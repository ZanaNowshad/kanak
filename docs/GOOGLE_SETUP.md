# Kanak — Google Cloud, Sheets & Apps Script Setup Guide

Follow this guide to connect Kanak to your authorized Google Sheets backend.

---

## 1. Google Sheets Setup

1. Create a new Google Spreadsheet in Google Drive named **Kanak Van Sales Datastore**.
2. Note the **Spreadsheet ID** from the URL:
   `https://docs.google.com/spreadsheets/d/{SPREADSHEET_ID}/edit`
3. In the top menu, navigate to **Extensions → Apps Script**.

---

## 2. Deploying the Apps Script Gateway

1. In the Apps Script editor, copy the contents of the files in `/apps-script/`:
   - `Schema.gs`
   - `Auth.gs`
   - `SheetsRepository.gs`
   - `Inventory.gs`
   - `Invoices.gs`
   - `Images.gs`
   - `Code.gs`
   - Update `appsscript.json` (via Project Settings → Show "appsscript.json" manifest file in editor).
2. Run the `initializeSpreadsheet` function once from the editor.
   This will automatically create all eight required worksheets with their exact headers and provision your email as the initial `admin`.
3. Click **Deploy → New deployment**.
4. Select type **Web app**.
   - **Description**: `Kanak Gateway Production`
   - **Execute as**: `Me (your Google account)`
   - **Who has access**: `Anyone` (Access is authenticated and role-checked via Google ID tokens on every request).
5. Click **Deploy** and copy the **Web App URL**:
   `https://script.google.com/macros/s/{DEPLOYMENT_ID}/exec`

---

## 3. Configuring the Android Application

1. Open **Kanak** on the Android device.
2. Tap the **Settings** icon (top-right of Dashboard or bottom navigation).
3. Paste your Web App URL into the **Gateway Web App URL** field.
4. Tap **Save Gateway URL**.
5. All invoice creations, purchases, and inventory updates will now synchronize directly with your authoritative Google Spreadsheet!
