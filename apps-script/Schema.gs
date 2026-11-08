/**
 * KANAK - Google Sheets Schema Definition & Initializer
 *
 * Implements Section 5 of the Kanak Autonomous Android Engineering Directive:
 * Exactly eight worksheets with exact column headers:
 * 1. Products
 * 2. Customers
 * 3. Suppliers
 * 4. Purchases
 * 5. Invoices
 * 6. InvoiceLines
 * 7. StockAdjustments
 * 8. Users
 */

const SCHEMAS = {
  Products: [
    'product_id',
    'barcode',
    'name',
    'cost_price',
    'selling_price',
    'vat_rate',
    'vat_amount',
    'photo_url',
    'stock_qty'
  ],
  Customers: [
    'customer_id',
    'business_name',
    'contact',
    'address',
    'phone'
  ],
  Suppliers: [
    'supplier_id',
    'name',
    'contact'
  ],
  Purchases: [
    'purchase_id',
    'supplier_id',
    'product_id',
    'qty',
    'cost',
    'date',
    'created_by'
  ],
  Invoices: [
    'invoice_id',
    'invoice_number',
    'customer_id',
    'customer_name',
    'date',
    'subtotal',
    'vat_total',
    'total',
    'status',
    'created_by',
    'voided_at'
  ],
  InvoiceLines: [
    'line_id',
    'invoice_id',
    'product_id',
    'product_name',
    'qty',
    'unit_price',
    'vat_rate',
    'vat_amount',
    'line_total'
  ],
  StockAdjustments: [
    'adjustment_id',
    'product_id',
    'delta_qty',
    'reason',
    'date',
    'created_by'
  ],
  Users: [
    'user_id',
    'email',
    'role',
    'active'
  ],
  Vans: [
    'van_id',
    'business_id',
    'van_code',
    'van_name',
    'registration_no',
    'salesperson_id',
    'salesperson_name',
    'status',
    'notes'
  ],
  Staff: [
    'membership_id',
    'business_id',
    'user_id',
    'email',
    'display_name',
    'role',
    'assigned_van_code',
    'status',
    'joined_at'
  ]
};

/**
 * Creates or updates all 8 worksheets with exact headers.
 * Run this function once from the Apps Script editor to initialize the spreadsheet.
 */
function initializeSpreadsheet(spreadsheetId) {
  const ss = spreadsheetId ? SpreadsheetApp.openById(spreadsheetId) : SpreadsheetApp.getActiveSpreadsheet();
  if (!ss) {
    throw new Error('Spreadsheet not found');
  }

  Object.keys(SCHEMAS).forEach(sheetName => {
    let sheet = ss.getSheetByName(sheetName);
    if (!sheet) {
      sheet = ss.insertSheet(sheetName);
    }
    
    // Set headers if empty
    const headers = SCHEMAS[sheetName];
    const currentFirstRow = sheet.getRange(1, 1, 1, headers.length).getValues()[0];
    const isEmpty = currentFirstRow.every(cell => cell === '');
    
    if (isEmpty) {
      sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
      sheet.getRange(1, 1, 1, headers.length).setFontWeight('bold');
      sheet.setFrozenRows(1);
    }
  });

  // Provision initial administrator in Users sheet if empty
  const usersSheet = ss.getSheetByName('Users');
  if (usersSheet && usersSheet.getLastRow() <= 1) {
    const ownerEmail = Session.getEffectiveUser().getEmail();
    usersSheet.appendRow([
      'owner_admin_id',
      ownerEmail,
      'admin',
      true
    ]);
  }

  Logger.log('Spreadsheet initialized successfully with 8 required worksheets.');
}
