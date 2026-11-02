package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.Invoice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KanakRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: KanakRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before test
        context.getSharedPreferences("kanak_repo_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = KanakRepository(context)
    }

    @Test
    fun testInitialSeededInventory() {
        val products = repository.products.value
        assertTrue("Repository should start with pre-seeded Bahrain FMCG products", products.isNotEmpty())
        assertTrue("Should include Almarai milk", products.any { it.name.contains("Almarai") })

        val customers = repository.customers.value
        assertTrue("Repository should start with pre-seeded customers", customers.isNotEmpty())

        val suppliers = repository.suppliers.value
        assertTrue("Repository should start with pre-seeded suppliers", suppliers.isNotEmpty())
    }

    @Test
    fun testCreateInvoice_DeductsStockAtomically() = runTest {
        val product = repository.products.value.first()
        val customer = repository.customers.value.first()
        val initialStock = product.stockQty
        val saleQty = BigDecimal("2.000")

        val res = repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = saleQty
                )
            ),
            createdBy = "test_driver"
        )

        assertTrue("Invoice creation must succeed", res.isSuccess)
        val invoice = res.getOrThrow()
        assertEquals(Invoice.STATUS_POSTED, invoice.status)
        assertEquals(1, invoice.lines.size)

        // Verify stock deducted
        val updatedProduct = repository.products.value.first { it.productId == product.productId }
        assertEquals(
            "Stock must be reduced by sale quantity",
            initialStock.subtract(saleQty),
            updatedProduct.stockQty
        )
    }

    @Test
    fun testCreateInvoice_RejectsInsufficientStock() = runTest {
        val product = repository.products.value.first()
        val customer = repository.customers.value.first()
        val excessiveQty = product.stockQty.add(BigDecimal("9999.000"))

        val res = repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = excessiveQty
                )
            ),
            createdBy = "test_driver"
        )

        assertTrue("Invoice creation must fail when stock is insufficient", res.isFailure)
        assertTrue(
            "Error message should mention insufficient stock",
            res.exceptionOrNull()?.message?.contains("Insufficient stock", ignoreCase = true) == true
        )
    }

    @Test
    fun testVoidInvoice_RestoresStockOnce() = runTest {
        val product = repository.products.value.first()
        val customer = repository.customers.value.first()
        val initialStock = product.stockQty
        val saleQty = BigDecimal("3.000")

        val createRes = repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = saleQty
                )
            ),
            createdBy = "test_driver"
        )
        val invoice = createRes.getOrThrow()

        // Stock is now initial - 3
        val stockAfterSale = repository.products.value.first { it.productId == product.productId }.stockQty
        assertEquals(initialStock.subtract(saleQty), stockAfterSale)

        // Void invoice (Admin action)
        val voidRes = repository.voidInvoice(invoice.invoiceId, "admin_user")
        assertTrue("Voiding invoice must succeed", voidRes.isSuccess)
        val voidedInvoice = voidRes.getOrThrow()
        assertEquals(Invoice.STATUS_VOID, voidedInvoice.status)
        assertNotNull(voidedInvoice.voidedAt)

        // Stock must be restored to initial
        val stockAfterVoid = repository.products.value.first { it.productId == product.productId }.stockQty
        assertEquals("Stock must be restored after void", initialStock, stockAfterVoid)

        // Attempting to void again must fail
        val duplicateVoid = repository.voidInvoice(invoice.invoiceId, "admin_user")
        assertTrue("Double void must be rejected", duplicateVoid.isFailure)
    }

    @Test
    fun testCreatePurchase_IncreasesStock() = runTest {
        val product = repository.products.value.first()
        val supplier = repository.suppliers.value.first()
        val initialStock = product.stockQty
        val purchaseQty = BigDecimal("50.000")
        val unitCost = BigDecimal("0.750")

        val res = repository.createPurchase(
            supplierId = supplier.supplierId,
            productId = product.productId,
            qty = purchaseQty,
            cost = unitCost,
            createdBy = "admin_user"
        )

        assertTrue("Purchase creation must succeed", res.isSuccess)
        val purchase = res.getOrThrow()
        assertEquals(purchaseQty, purchase.qty)

        val updatedProduct = repository.products.value.first { it.productId == product.productId }
        assertEquals(
            "Stock must increase by purchased quantity",
            initialStock.add(purchaseQty),
            updatedProduct.stockQty
        )
    }

    @Test
    fun testCorrectPurchase_AppliesStockDelta() = runTest {
        val product = repository.products.value.first()
        val supplier = repository.suppliers.value.first()
        val initialStock = product.stockQty

        val purchaseRes = repository.createPurchase(
            supplierId = supplier.supplierId,
            productId = product.productId,
            qty = BigDecimal("10.000"),
            cost = BigDecimal("0.500"),
            createdBy = "admin_user"
        )
        val purchase = purchaseRes.getOrThrow()
        assertEquals(initialStock.add(BigDecimal("10.000")), repository.products.value.first { it.productId == product.productId }.stockQty)

        // Correct purchase to 15 (delta = +5)
        val correctUp = repository.correctPurchase(
            purchaseId = purchase.purchaseId,
            newQty = BigDecimal("15.000"),
            newCost = BigDecimal("0.500"),
            adminUserId = "admin_user"
        )
        assertTrue(correctUp.isSuccess)
        assertEquals(initialStock.add(BigDecimal("15.000")), repository.products.value.first { it.productId == product.productId }.stockQty)

        // Correct purchase to 6 (delta = -9)
        val correctDown = repository.correctPurchase(
            purchaseId = purchase.purchaseId,
            newQty = BigDecimal("6.000"),
            newCost = BigDecimal("0.500"),
            adminUserId = "admin_user"
        )
        assertTrue(correctDown.isSuccess)
        assertEquals(initialStock.add(BigDecimal("6.000")), repository.products.value.first { it.productId == product.productId }.stockQty)
    }

    @Test
    fun testReversePurchase_ReducesStockAndRejectsNegative() = runTest {
        val product = repository.products.value.first()
        val supplier = repository.suppliers.value.first()
        val initialStock = product.stockQty

        val purchaseRes = repository.createPurchase(
            supplierId = supplier.supplierId,
            productId = product.productId,
            qty = BigDecimal("20.000"),
            cost = BigDecimal("0.800"),
            createdBy = "admin_user"
        )
        val purchase = purchaseRes.getOrThrow()

        val revRes = repository.reversePurchase(purchase.purchaseId, "admin_user")
        assertTrue("Reversal must succeed", revRes.isSuccess)

        val updatedStock = repository.products.value.first { it.productId == product.productId }.stockQty
        assertEquals("Stock must be restored to initial", initialStock, updatedStock)

        // Verify purchase row is preserved with isReversed flag for full auditability
        val retainedPurchase = repository.purchases.value.find { it.purchaseId == purchase.purchaseId }
        assertNotNull("Historical purchase row must be preserved", retainedPurchase)
        assertTrue("Historical purchase row must be marked isReversed", retainedPurchase!!.isReversed)

        // Verify compensating stock adjustment exists in StockAdjustments linked to purchaseId
        val compensatingAdj = repository.stockAdjustments.value.find { it.reason.contains(purchase.purchaseId) }
        assertNotNull("Compensating adjustment must be recorded in StockAdjustments", compensatingAdj)
        assertEquals(
            "Compensating adjustment must negate the purchased quantity",
            purchase.qty.negate(),
            compensatingAdj!!.deltaQty
        )

        // Attempting to reverse an already reversed purchase must fail
        val secondRev = repository.reversePurchase(purchase.purchaseId, "admin_user")
        assertTrue("Double reversal must be rejected", secondRev.isFailure)

        // Attempting to correct an already reversed purchase must fail
        val correctReversed = repository.correctPurchase(
            purchaseId = purchase.purchaseId,
            newQty = BigDecimal("10.000"),
            newCost = BigDecimal("0.800"),
            adminUserId = "admin_user"
        )
        assertTrue("Correcting a reversed purchase must be rejected", correctReversed.isFailure)
    }

    @Test
    fun testAdjustStock_SignedDeltaAndReasonRequired() = runTest {
        val product = repository.products.value.first()
        val initialStock = product.stockQty

        // Positive adjustment (found extra items during physical count)
        val posRes = repository.adjustStock(
            productId = product.productId,
            deltaQty = BigDecimal("5.000"),
            reason = "Physical audit found surplus",
            createdBy = "admin_user"
        )
        assertTrue(posRes.isSuccess)
        assertEquals(initialStock.add(BigDecimal("5.000")), repository.products.value.first { it.productId == product.productId }.stockQty)

        // Negative adjustment (damaged item)
        val negRes = repository.adjustStock(
            productId = product.productId,
            deltaQty = BigDecimal("-2.000"),
            reason = "Damaged during transit",
            createdBy = "admin_user"
        )
        assertTrue(negRes.isSuccess)
        assertEquals(initialStock.add(BigDecimal("3.000")), repository.products.value.first { it.productId == product.productId }.stockQty)

        // Empty reason must fail
        val noReasonRes = repository.adjustStock(
            productId = product.productId,
            deltaQty = BigDecimal("1.000"),
            reason = "   ",
            createdBy = "admin_user"
        )
        assertTrue("Empty reason must be rejected", noReasonRes.isFailure)

        // Excessive negative adjustment causing negative stock must fail
        val excessiveNeg = repository.adjustStock(
            productId = product.productId,
            deltaQty = BigDecimal("-99999.000"),
            reason = "Major loss",
            createdBy = "admin_user"
        )
        assertTrue("Adjustment causing negative stock must be rejected", excessiveNeg.isFailure)
    }

    @Test
    fun testReferentialIntegrity_DeleteProductProtected() = runTest {
        val product = repository.products.value.first()
        val customer = repository.customers.value.first()

        // Create an invoice with this product
        repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = BigDecimal("1.000")
                )
            ),
            createdBy = "test_driver"
        )

        // Try to delete the product
        val deleteRes = repository.deleteProduct(product.productId)
        assertTrue("Cannot delete product referenced in posted invoice", deleteRes.isFailure)
        assertTrue(deleteRes.exceptionOrNull()?.message?.contains("invoices reference", ignoreCase = true) == true)
    }

    @Test
    fun testReferentialIntegrity_DeleteCustomerProtected() = runTest {
        val product = repository.products.value.first()
        val customer = repository.customers.value.first()

        // Create an invoice with this customer
        repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = BigDecimal("1.000")
                )
            ),
            createdBy = "test_driver"
        )

        // Try to delete the customer
        val deleteRes = repository.deleteCustomer(customer.customerId)
        assertTrue("Cannot delete customer with historical invoices", deleteRes.isFailure)
        assertTrue(deleteRes.exceptionOrNull()?.message?.contains("Invoices exist", ignoreCase = true) == true)
    }
}
