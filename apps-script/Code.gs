/**
 * KANAK - Master Google Apps Script Gateway Entry Point
 *
 * Implements Section 6 & 25 of the Kanak Autonomous Android Engineering Directive:
 * - Authenticated Web App endpoint (doPost and doGet)
 * - Script locking & transaction coordination
 * - Single source of truth: Google Sheets
 */

function doGet(e) {
  return ContentService.createTextOutput(JSON.stringify({
    status: 'online',
    app: 'Kanak FMCG Van Sales Gateway',
    timestamp: new Date().toISOString(),
    timezone: 'Asia/Bahrain'
  })).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  try {
    const rawContent = e.postData.contents;
    const request = JSON.parse(rawContent);

    const action = request.action;
    const idToken = request.idToken;
    const payload = request.payload || {};

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    if (!ss) {
      throw new Error('Spreadsheet container not attached');
    }

    // Role requirement mapping per Permission Matrix (Section 6.4)
    const adminOnlyActions = [
      'create_purchase',
      'correct_purchase',
      'reverse_purchase',
      'adjust_stock',
      'void_invoice'
    ];
    const requiredRole = adminOnlyActions.indexOf(action) !== -1 ? 'admin' : 'standard';

    // Verify token and role
    const user = authenticateAndAuthorize(ss, idToken, requiredRole);

    let result = null;

    switch (action) {
      case 'get_all':
        result = {
          products: getSheetData(ss, 'Products'),
          customers: getSheetData(ss, 'Customers'),
          suppliers: getSheetData(ss, 'Suppliers'),
          purchases: getSheetData(ss, 'Purchases'),
          invoices: getSheetData(ss, 'Invoices'),
          invoice_lines: getSheetData(ss, 'InvoiceLines'),
          stock_adjustments: getSheetData(ss, 'StockAdjustments'),
          vans: getSheetData(ss, 'Vans'),
          staff: getSheetData(ss, 'Staff')
        };
        break;

      case 'create_product':
        appendSheetRow(ss, 'Products', payload);
        result = { success: true };
        break;

      case 'update_product':
        updateSheetRowByUuid(ss, 'Products', 'product_id', payload.product_id, payload);
        result = { success: true };
        break;

      case 'delete_product':
        // Refuse if invoices, purchases, or adjustments exist referencing this product
        const lines = getSheetData(ss, 'InvoiceLines');
        if (lines.some(l => l.product_id === payload.product_id)) {
          throw new Error('Cannot delete product: Historical invoices reference this SKU');
        }
        const purchases = getSheetData(ss, 'Purchases');
        if (purchases.some(p => p.product_id === payload.product_id)) {
          throw new Error('Cannot delete product: Historical purchases reference this SKU');
        }
        deleteRowByUuid(ss, 'Products', 'product_id', payload.product_id);
        result = { success: true };
        break;

      case 'create_customer':
        appendSheetRow(ss, 'Customers', payload);
        result = { success: true };
        break;

      case 'update_customer':
        updateSheetRowByUuid(ss, 'Customers', 'customer_id', payload.customer_id, payload);
        result = { success: true };
        break;

      case 'delete_customer':
        const invs = getSheetData(ss, 'Invoices');
        if (invs.some(i => i.customer_id === payload.customer_id)) {
          throw new Error('Cannot delete customer: Invoices exist for this account');
        }
        deleteRowByUuid(ss, 'Customers', 'customer_id', payload.customer_id);
        result = { success: true };
        break;

      case 'create_supplier':
        appendSheetRow(ss, 'Suppliers', payload);
        result = { success: true };
        break;

      case 'update_supplier':
        updateSheetRowByUuid(ss, 'Suppliers', 'supplier_id', payload.supplier_id, payload);
        result = { success: true };
        break;

      case 'delete_supplier':
        const allPurchases = getSheetData(ss, 'Purchases');
        if (allPurchases.some(p => p.supplier_id === payload.supplier_id)) {
          throw new Error('Cannot delete supplier: Purchases exist from this supplier');
        }
        deleteRowByUuid(ss, 'Suppliers', 'supplier_id', payload.supplier_id);
        result = { success: true };
        break;

      case 'create_purchase':
        result = handleCreatePurchase(ss, payload, user);
        break;

      case 'correct_purchase':
        result = handleCorrectPurchase(ss, payload, user);
        break;

      case 'reverse_purchase':
        result = handleReversePurchase(ss, payload, user);
        break;

      case 'create_invoice':
        result = handleCreateInvoice(ss, payload, user);
        break;

      case 'void_invoice':
        result = handleVoidInvoice(ss, payload, user);
        break;

      case 'adjust_stock':
        result = handleAdjustStock(ss, payload, user);
        break;

      case 'upload_image':
        result = handleImageUpload(payload, user);
        break;

      default:
        throw new Error("Unsupported gateway action: '" + action + "'");
    }

    return ContentService.createTextOutput(JSON.stringify({
      success: true,
      data: result
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      success: false,
      error: err.message || String(err)
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function deleteRowByUuid(ss, sheetName, idColName, idValue) {
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) return;
  const values = sheet.getDataRange().getValues();
  const headers = values[0];
  const idx = headers.indexOf(idColName);
  if (idx === -1) return;

  for (let i = 1; i < values.length; i++) {
    if (String(values[i][idx]) === String(idValue)) {
      sheet.deleteRow(i + 1);
      return;
    }
  }
}
