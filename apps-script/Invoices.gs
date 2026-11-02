/**
 * KANAK - Invoices & Sales Transaction Layer
 *
 * Implements Section 7 and 14 of the Kanak Autonomous Android Engineering Directive:
 * - Script locking
 * - Stock availability validation
 * - Authoritative recalculation of net, VAT, and gross totals
 * - Decreases stock atomically
 * - Invariant: stock_qty >= 0
 * - Admin void workflow with single stock restoration
 */

function handleCreateInvoice(ss, payload, user) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);

  try {
    // Idempotency check: if invoice already exists, return committed record
    const existingInvoices = getSheetData(ss, 'Invoices');
    const existing = existingInvoices.find(inv => inv.invoice_id === payload.invoice_id);
    if (existing) {
      return { status: 'already_committed', invoice: existing };
    }

    const products = getSheetData(ss, 'Products');
    const lines = payload.lines || [];
    if (lines.length === 0) throw new Error('Invoice must contain at least one line item');

    // Check stock availability for all items
    const stockDeltas = {};
    lines.forEach(line => {
      const prod = products.find(p => p.product_id === line.product_id);
      if (!prod) throw new Error('Product not found: ' + line.product_id);

      const qty = Number(line.qty);
      if (isNaN(qty) || qty <= 0) throw new Error('Invalid line quantity for ' + prod.name);

      const currentStock = Number(prod.stock_qty || 0);
      const requestedTotal = (stockDeltas[line.product_id] || 0) + qty;
      if (currentStock < requestedTotal) {
        throw new Error("Insufficient stock for '" + prod.name + "'. Available: " + currentStock + ", Requested: " + requestedTotal);
      }
      stockDeltas[line.product_id] = requestedTotal;
    });

    // Authoritative VAT and total calculations
    let subtotal = 0;
    let vatTotal = 0;
    let grandTotal = 0;

    const validatedLines = lines.map(line => {
      const prod = products.find(p => p.product_id === line.product_id);
      const qty = Number(line.qty);
      const unitPrice = Number(prod.selling_price);
      const vatRate = Number(prod.vat_rate);

      const gross = Math.round(qty * unitPrice * 1000) / 1000;
      const net = vatRate > 0 ? Math.round((gross / (1 + vatRate)) * 1000) / 1000 : gross;
      const vatAmount = Math.round((gross - net) * 1000) / 1000;

      subtotal += net;
      vatTotal += vatAmount;
      grandTotal += gross;

      return {
        line_id: line.line_id,
        invoice_id: payload.invoice_id,
        product_id: prod.product_id,
        product_name: prod.name,
        qty: qty,
        unit_price: unitPrice,
        vat_rate: vatRate,
        vat_amount: vatAmount,
        line_total: gross
      };
    });

    subtotal = Math.round(subtotal * 1000) / 1000;
    vatTotal = Math.round(vatTotal * 1000) / 1000;
    grandTotal = Math.round(grandTotal * 1000) / 1000;

    // Deduct stock for all products
    Object.keys(stockDeltas).forEach(prodId => {
      const prod = products.find(p => p.product_id === prodId);
      const newStock = Number(prod.stock_qty || 0) - stockDeltas[prodId];
      updateSheetRowByUuid(ss, 'Products', 'product_id', prodId, { stock_qty: newStock });
    });

    // Append Invoices header
    appendSheetRow(ss, 'Invoices', {
      invoice_id: payload.invoice_id,
      invoice_number: payload.invoice_number,
      customer_id: payload.customer_id,
      customer_name: payload.customer_name,
      date: payload.date || new Date().toISOString(),
      subtotal: subtotal,
      vat_total: vatTotal,
      total: grandTotal,
      status: 'POSTED',
      created_by: user.userId,
      voided_at: ''
    });

    // Append InvoiceLines
    validatedLines.forEach(l => {
      appendSheetRow(ss, 'InvoiceLines', l);
    });

    return {
      success: true,
      invoice_number: payload.invoice_number,
      total: grandTotal
    };
  } finally {
    lock.releaseLock();
  }
}

function handleVoidInvoice(ss, payload, user) {
  if (user.role !== 'admin') {
    throw new Error('Access denied: Voiding invoices requires admin privileges');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);

  try {
    const invoices = getSheetData(ss, 'Invoices');
    const invoice = invoices.find(inv => inv.invoice_id === payload.invoice_id);
    if (!invoice) throw new Error('Invoice not found: ' + payload.invoice_id);

    if (String(invoice.status).toUpperCase() === 'VOID') {
      return { status: 'already_void', invoice_id: payload.invoice_id };
    }

    // Restore stock from InvoiceLines
    const allLines = getSheetData(ss, 'InvoiceLines');
    const targetLines = allLines.filter(l => l.invoice_id === payload.invoice_id);
    const products = getSheetData(ss, 'Products');

    targetLines.forEach(l => {
      const prod = products.find(p => p.product_id === l.product_id);
      if (prod) {
        const restored = Number(prod.stock_qty || 0) + Number(l.qty || 0);
        updateSheetRowByUuid(ss, 'Products', 'product_id', l.product_id, { stock_qty: restored });
      }
    });

    // Mark VOID in Invoices sheet
    updateSheetRowByUuid(ss, 'Invoices', 'invoice_id', payload.invoice_id, {
      status: 'VOID',
      voided_at: payload.voided_at || new Date().toISOString()
    });

    return { success: true, message: 'Invoice voided and inventory restored' };
  } finally {
    lock.releaseLock();
  }
}
