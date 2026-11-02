/**
 * KANAK - Inventory & Purchase Mutations Layer
 *
 * Implements Section 7, 13, and 16 of the Kanak Autonomous Android Engineering Directive:
 * - Script locking via LockService
 * - Purchases atomically increase stock
 * - Stock adjustments (admin-only) with reasons
 * - Purchase corrections apply inventory deltas
 * - Purchase reversals safely restore stock contribution
 * - Invariant: stock_qty >= 0
 * - Rejection of adjustments, reversals, or purchases causing negative stock
 */

function handleCreatePurchase(ss, payload, user) {
  if (user.role !== 'admin') {
    throw new Error('Access denied: Creating purchases requires admin privileges');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);

  try {
    const products = getSheetData(ss, 'Products');
    const product = products.find(p => p.product_id === payload.product_id);
    if (!product) throw new Error('Product not found: ' + payload.product_id);

    const qty = Number(payload.qty);
    if (isNaN(qty) || qty <= 0) throw new Error('Invalid purchase quantity');

    const cost = Number(payload.cost);
    if (isNaN(cost) || cost < 0) throw new Error('Invalid purchase unit cost');

    // Check duplicate purchase UUID
    const existingPurchases = getSheetData(ss, 'Purchases');
    if (existingPurchases.some(p => p.purchase_id === payload.purchase_id)) {
      return { status: 'already_committed', purchase_id: payload.purchase_id };
    }

    // Append purchase row
    appendSheetRow(ss, 'Purchases', {
      purchase_id: payload.purchase_id,
      supplier_id: payload.supplier_id,
      product_id: payload.product_id,
      qty: qty,
      cost: cost,
      date: payload.date || new Date().toISOString(),
      created_by: user.userId
    });

    // Increase product stock atomically
    const newStock = Number(product.stock_qty || 0) + qty;
    updateSheetRowByUuid(ss, 'Products', 'product_id', payload.product_id, {
      stock_qty: newStock
    });

    return { success: true, new_stock: newStock };
  } finally {
    lock.releaseLock();
  }
}

function handleCorrectPurchase(ss, payload, user) {
  if (user.role !== 'admin') {
    throw new Error('Access denied: Correcting purchases requires admin privileges');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);

  try {
    const purchases = getSheetData(ss, 'Purchases');
    const purchase = purchases.find(p => p.purchase_id === payload.purchase_id);
    if (!purchase) throw new Error('Purchase not found: ' + payload.purchase_id);

    const products = getSheetData(ss, 'Products');
    const product = products.find(p => p.product_id === purchase.product_id);
    if (!product) throw new Error('Product not found for purchase: ' + purchase.product_id);

    const oldQty = Number(purchase.qty);
    const newQty = Number(payload.new_qty);
    if (isNaN(newQty) || newQty <= 0) throw new Error('New purchase quantity must be positive');

    const newCost = Number(payload.new_cost);
    if (isNaN(newCost) || newCost < 0) throw new Error('New purchase unit cost cannot be negative');

    // Delta between new and old quantity
    const delta = newQty - oldQty;
    const currentStock = Number(product.stock_qty || 0);
    const resultingStock = currentStock + delta;

    if (resultingStock < 0) {
      throw new Error('Purchase correction rejected: Would cause negative stock (' + resultingStock + ')');
    }

    // Update purchase record
    updateSheetRowByUuid(ss, 'Purchases', 'purchase_id', payload.purchase_id, {
      qty: newQty,
      cost: newCost
    });

    // Apply delta to product stock
    updateSheetRowByUuid(ss, 'Products', 'product_id', purchase.product_id, {
      stock_qty: resultingStock
    });

    return { success: true, new_stock: resultingStock, delta: delta };
  } finally {
    lock.releaseLock();
  }
}

function handleReversePurchase(ss, payload, user) {
  if (user.role !== 'admin') {
    throw new Error('Access denied: Reversing purchases requires admin privileges');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);

  try {
    const purchases = getSheetData(ss, 'Purchases');
    const purchase = purchases.find(p => p.purchase_id === payload.purchase_id);
    if (!purchase) throw new Error('Purchase not found: ' + payload.purchase_id);

    const products = getSheetData(ss, 'Products');
    const product = products.find(p => p.product_id === purchase.product_id);
    if (!product) throw new Error('Product not found for purchase: ' + purchase.product_id);

    const qty = Number(purchase.qty);
    const currentStock = Number(product.stock_qty || 0);
    const resultingStock = currentStock - qty;

    if (resultingStock < 0) {
      throw new Error('Purchase reversal rejected: Would cause negative stock (' + resultingStock + ')');
    }

    // Compensating stock adjustment for full auditability
    appendSheetRow(ss, 'StockAdjustments', {
      adjustment_id: Utilities.getUuid(),
      product_id: purchase.product_id,
      delta_qty: -qty,
      reason: 'Purchase reversal: ' + payload.purchase_id,
      date: new Date().toISOString(),
      created_by: user.userId
    });

    // Decrease product stock by reversed purchase quantity
    updateSheetRowByUuid(ss, 'Products', 'product_id', purchase.product_id, {
      stock_qty: resultingStock
    });

    return { success: true, new_stock: resultingStock };
  } finally {
    lock.releaseLock();
  }
}

function handleAdjustStock(ss, payload, user) {
  if (user.role !== 'admin') {
    throw new Error('Access denied: Manual stock adjustments require admin privileges');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);

  try {
    const products = getSheetData(ss, 'Products');
    const product = products.find(p => p.product_id === payload.product_id);
    if (!product) throw new Error('Product not found: ' + payload.product_id);

    const delta = Number(payload.delta_qty);
    if (isNaN(delta) || delta === 0) throw new Error('Adjustment quantity must be non-zero');

    const currentStock = Number(product.stock_qty || 0);
    const resultingStock = currentStock + delta;
    if (resultingStock < 0) {
      throw new Error('Adjustment rejected: Resulting stock cannot be negative (' + resultingStock + ')');
    }

    if (!payload.reason || String(payload.reason).trim() === '') {
      throw new Error('Adjustment reason is required');
    }

    appendSheetRow(ss, 'StockAdjustments', {
      adjustment_id: payload.adjustment_id,
      product_id: payload.product_id,
      delta_qty: delta,
      reason: payload.reason,
      date: payload.date || new Date().toISOString(),
      created_by: user.userId
    });

    updateSheetRowByUuid(ss, 'Products', 'product_id', payload.product_id, {
      stock_qty: resultingStock
    });

    return { success: true, new_stock: resultingStock };
  } finally {
    lock.releaseLock();
  }
}
