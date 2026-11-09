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
@Config(sdk = [34])
class KanakRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: KanakRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("kanak_local_cache.db")
        context.getSharedPreferences("kanak_repo_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = KanakRepository(context)
        repository.clearAllData()
    }

    @Test
    fun testProductionDatabaseStartsEmpty() {
        val products = repository.products.value
        assertTrue("Production database should start with zero mock products", products.isEmpty())

        val customers = repository.customers.value
        assertTrue("Production database should start with zero mock customers", customers.isEmpty())

        val suppliers = repository.suppliers.value
        assertTrue("Production database should start with zero mock suppliers", suppliers.isEmpty())
    }

    private suspend fun setupTestEntities(): Triple<com.example.kanak.domain.models.Product, com.example.kanak.domain.models.Customer, com.example.kanak.domain.models.Supplier> {
        val productRes = repository.createProduct(
            barcode = "6084001201111",
            name = "Test FMCG Juice 1L",
            costPrice = BigDecimal("0.400"),
            sellingPrice = BigDecimal("0.650"),
            vatRate = BigDecimal("0.10"),
            photoUrl = null
        )
        val product = productRes.getOrThrow()

        // Give product starting stock
        repository.adjustStock(
            productId = product.productId,
            deltaQty = BigDecimal("100.000"),
            reason = "Initial warehouse intake for testing",
            createdBy = "tester"
        )
        val refreshedProduct = repository.products.value.first { it.productId == product.productId }

        val customerRes = repository.createCustomer(
            businessName = "Manama Coldstore Test",
            contact = "Abu Ali",
            phone = "+97333000111"
        )
        val customer = customerRes.getOrThrow()

        val supplierRes = repository.createSupplier(
            name = "Gulf Distribution LLC",
            contact = "+97317000222"
        )
        val supplier = supplierRes.getOrThrow()

        return Triple(refreshedProduct, customer, supplier)
    }

    @Test
    fun testCreateInvoice_DeductsStockAtomically() = runTest {
        val (product, customer, _) = setupTestEntities()
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
        val (product, customer, _) = setupTestEntities()
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
        val (product, customer, _) = setupTestEntities()
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
        val (product, _, supplier) = setupTestEntities()
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
        val (product, _, supplier) = setupTestEntities()
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
        val (product, _, supplier) = setupTestEntities()
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
        val (product, _, _) = setupTestEntities()
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
        val (product, customer, _) = setupTestEntities()

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
        val (product, customer, _) = setupTestEntities()

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

    @Test
    fun testRoomDatabaseCaching_SurvivesRepositoryReload() = runTest {
        val (product, customer, _) = setupTestEntities()
        val invoiceRes = repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = BigDecimal("3.000")
                )
            ),
            createdBy = "driver_van_101"
        )
        assertTrue("Invoice creation must succeed", invoiceRes.isSuccess)
        val originalInvoice = invoiceRes.getOrThrow()

        // Simulate closing app and reopening with fresh repository instance accessing the same Room database
        val reloadedRepository = KanakRepository(context)
        val loadedInvoices = reloadedRepository.invoices.value
        assertEquals("Cached invoice count in Room must match", 1, loadedInvoices.size)
        assertEquals("Invoice ID must match", originalInvoice.invoiceId, loadedInvoices.first().invoiceId)
        assertEquals("Lines count must match", 1, loadedInvoices.first().lines.size)
        assertEquals("Deducted stock must persist in Room", BigDecimal("97.000"), reloadedRepository.products.value.first { it.productId == product.productId }.stockQty)
    }

    @Test
    fun testBahrainVatConfiguration_AdminConfiguresRate() = runTest {
        // Default should be 10.00%
        assertEquals(BigDecimal("10.00"), repository.getConfiguredVatPercentage())
        assertEquals(BigDecimal("0.1000"), repository.getConfiguredVatRate())

        // Admin configures 5.00% (concessional)
        repository.setConfiguredVatPercentage(BigDecimal("5.00"))
        assertEquals(BigDecimal("5.00"), repository.getConfiguredVatPercentage())
        assertEquals(BigDecimal("0.0500"), repository.getConfiguredVatRate())

        // Calculation check
        val line = com.example.kanak.domain.financial.BahrainFinancials.calculateLine(
            qty = BigDecimal("2.000"),
            unitPrice = BigDecimal("1.050"),
            vatRate = repository.getConfiguredVatRate()
        )
        // Gross: 2 * 1.050 = 2.100
        // Divisor: 1.050 -> Net = 2.100 / 1.050 = 2.000
        // VAT = 2.100 - 2.000 = 0.100
        assertEquals(BigDecimal("2.100"), line.gross)
        assertEquals(BigDecimal("2.000"), line.net)
        assertEquals(BigDecimal("0.100"), line.vatAmount)
    }

    @Test
    fun testBluetoothPrinterManager_DiscoveryAndConnection() = runTest {
        val testTransport = com.example.kanak.printing.SimulatedPrinterTransport()
        val printerManager = com.example.kanak.printing.BluetoothPrinterManager(context, testTransport = testTransport)

        val targetPrinter = com.example.kanak.printing.PrinterDevice(name = "Sunmi V2 Thermal", address = "00:11:22:33:44:55", isBonded = true)
        val printRes = printerManager.connectAndTest(targetPrinter)

        assertTrue("connectAndTest must succeed on simulated transport", printRes is com.example.kanak.printing.PrintResult.Success)
        assertNotNull("Bytes should have been dispatched to printer transport", testTransport.lastPrintedBytes)
        assertEquals(targetPrinter.address, printerManager.selectedPrinter.value?.address)
    }

    @Test
    fun testFleetManagement_VanCreationAndDriverAssignment() = runTest {
        val createRes = repository.createVan(
            vanCode = "VAN-009",
            vanName = "Toyota HiAce Riffa Express",
            registrationNo = "998811",
            assignedSalespersonId = null,
            assignedSalespersonName = null,
            notes = "Test Route Van"
        )
        assertTrue(createRes.isSuccess)
        val createdVan = createRes.getOrThrow()
        assertEquals("VAN-009", createdVan.vanCode)

        // Assign driver
        val assignRes = repository.assignSalespersonToVan(
            vanId = createdVan.vanId,
            salespersonId = "usr_sales_test",
            salespersonName = "Ali Salesperson"
        )
        assertTrue(assignRes.isSuccess)
        val assignedVan = repository.vans.value.first { it.vanId == createdVan.vanId }
        assertEquals("usr_sales_test", assignedVan.assignedSalespersonId)
        assertEquals("Ali Salesperson", assignedVan.assignedSalespersonName)
    }

    @Test
    fun testStockTransfer_WarehouseToVanAndAtomicDeduction() = runTest {
        val (product, _, _) = setupTestEntities()
        val initialWarehouseStock = product.stockQty // 100.000

        val vanRes = repository.createVan(
            vanCode = "VAN-010",
            vanName = "Muharraq Delivery Van",
            registrationNo = "776655",
            assignedSalespersonId = "driver_1",
            assignedSalespersonName = "Hassan"
        )
        val van = vanRes.getOrThrow()

        // Transfer 20.000 units from warehouse to van
        val transferRes = repository.transferStock(
            sourceType = "WAREHOUSE",
            sourceId = "central_warehouse",
            destinationVanId = van.vanId,
            productId = product.productId,
            qty = BigDecimal("20.000"),
            transferredBy = "supervisor"
        )
        assertTrue(transferRes.isSuccess)

        // Check van stock has 20.000
        val vanStock = repository.getVanStock(van.vanId, product.productId)
        assertEquals(BigDecimal("20.000"), vanStock)

        // Check warehouse stock was decremented to 80.000
        val updatedProd = repository.products.value.first { it.productId == product.productId }
        assertEquals(BigDecimal("80.000"), updatedProd.stockQty)
    }

    @Test
    fun testEmployeeInvitation_InviteAndAcceptanceWorkflow() = runTest {
        val vanRes = repository.createVan(
            vanCode = "VAN-011",
            vanName = "Isa Town Route Van",
            registrationNo = "443322",
            assignedSalespersonId = null,
            assignedSalespersonName = null
        )
        val van = vanRes.getOrThrow()

        // Owner invites employee
        val inviteRes = repository.inviteEmployee(
            recipientEmail = "newdriver@kanak.bh",
            role = com.example.kanak.domain.models.User.ROLE_SALESPERSON,
            assignedVanId = van.vanId,
            invitedBy = "Owner Admin"
        )
        assertTrue(inviteRes.isSuccess)
        val invitation = inviteRes.getOrThrow()
        assertTrue(invitation.inviteCode.isNotBlank())
        assertEquals("newdriver@kanak.bh", invitation.recipientEmail)

        // Employee joins and accepts invitation code
        val testJoiningUser = com.example.kanak.domain.models.User(
            userId = "usr_driver_joined",
            email = "newdriver@kanak.bh"
        )
        val acceptRes = repository.acceptInvitation(
            inviteCode = invitation.inviteCode,
            user = testJoiningUser
        )
        assertTrue("Accepting invitation with matching code must succeed", acceptRes.isSuccess)
        val membership = acceptRes.getOrThrow()
        assertEquals(van.vanId, membership.assignedVanId)
        assertEquals("VAN-011", membership.assignedVanCode)

        // Van should now have assigned salesperson
        val updatedVan = repository.vans.value.first { it.vanId == van.vanId }
        assertEquals("usr_driver_joined", updatedVan.assignedSalespersonId)
    }

    @Test
    fun testDailySalesSummary_AggregationAndVatBreakdown() = runTest {
        val (product, customer, _) = setupTestEntities()

        val vanRes = repository.createVan(
            vanCode = "VAN-SUMMARY",
            vanName = "Summary Test Van",
            registrationNo = "123123",
            assignedSalespersonId = "sales_rep",
            assignedSalespersonName = "Sales Rep"
        )
        val van = vanRes.getOrThrow()

        // Stock the van from warehouse first
        val transferRes = repository.transferStock(
            sourceType = "WAREHOUSE",
            sourceId = "central_warehouse",
            destinationVanId = van.vanId,
            productId = product.productId,
            qty = BigDecimal("20.000"),
            transferredBy = "supervisor"
        )
        assertTrue(transferRes.isSuccess)

        // Cash sale
        val cashInv = repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = BigDecimal("2.000") // 2 * 0.650 = 1.300 gross
                )
            ),
            createdBy = "sales_rep",
            paymentType = com.example.kanak.domain.models.Invoice.PAYMENT_CASH,
            vanId = van.vanId,
            vanCode = van.vanCode
        ).getOrThrow()

        // Credit sale
        val creditInv = repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = BigDecimal("4.000") // 4 * 0.650 = 2.600 gross
                )
            ),
            createdBy = "sales_rep",
            paymentType = com.example.kanak.domain.models.Invoice.PAYMENT_CREDIT,
            vanId = van.vanId,
            vanCode = van.vanCode
        ).getOrThrow()

        val summary = repository.getDailySalesSummary(vanIdFilter = van.vanId)
        assertEquals(2, summary.totalInvoicesCount)
        assertEquals(BigDecimal("3.900"), summary.totalGrossSales)
        assertEquals(BigDecimal("1.300"), summary.totalCashSales)
        assertEquals(BigDecimal("2.600"), summary.totalCreditSales)
        assertTrue(summary.vatAmountCollected > BigDecimal.ZERO)
        assertEquals(summary.totalGrossSales, summary.taxableSubtotal.add(summary.vatAmountCollected))
    }

    @Test
    fun testDeleteVan_ValidationAndSuccess() = runTest {
        val (product, customer, _) = setupTestEntities()
        val van = repository.createVan(
            vanCode = "VAN-DEL-TEST",
            vanName = "Test Deletion Van",
            registrationNo = "123456",
            assignedSalespersonId = null,
            assignedSalespersonName = null
        ).getOrThrow()

        // Transfer stock to van -> delete should fail due to allocated inventory
        repository.transferStock(
            sourceType = "WAREHOUSE",
            sourceId = "central_warehouse",
            destinationVanId = van.vanId,
            productId = product.productId,
            qty = BigDecimal("5.000"),
            transferredBy = "supervisor"
        ).getOrThrow()

        val deleteWithStockRes = repository.deleteVan(van.vanId)
        assertTrue("Cannot delete van with active stock", deleteWithStockRes.isFailure)
        assertTrue(deleteWithStockRes.exceptionOrNull()?.message?.contains("inventory", ignoreCase = true) == true)

        // Transfer stock back to warehouse
        repository.transferStock(
            sourceType = "VAN",
            sourceId = van.vanId,
            destinationVanId = "central_warehouse",
            productId = product.productId,
            qty = BigDecimal("5.000"),
            transferredBy = "supervisor"
        ).getOrThrow()

        // Transfer 1 unit to van so it can sell, then record an invoice against this van
        repository.transferStock(
            sourceType = "WAREHOUSE",
            sourceId = "central_warehouse",
            destinationVanId = van.vanId,
            productId = product.productId,
            qty = BigDecimal("1.000"),
            transferredBy = "supervisor"
        ).getOrThrow()

        // Now record an invoice against this van (which depletes the 1 unit to 0)
        repository.createInvoice(
            customerId = customer.customerId,
            proposedLines = listOf(
                KanakRepository.ProposedLine(
                    productId = product.productId,
                    qty = BigDecimal("1.000")
                )
            ),
            createdBy = "driver",
            vanId = van.vanId,
            vanCode = van.vanCode
        ).getOrThrow()

        val deleteWithInvoiceRes = repository.deleteVan(van.vanId)
        assertTrue("Cannot delete van with attached invoices", deleteWithInvoiceRes.isFailure)
        assertTrue(deleteWithInvoiceRes.exceptionOrNull()?.message?.contains("invoices are recorded", ignoreCase = true) == true)

        // Create a pristine empty van and verify clean deletion
        val cleanVan = repository.createVan(
            vanCode = "VAN-CLEAN",
            vanName = "Pristine Van",
            registrationNo = "999888",
            assignedSalespersonId = null,
            assignedSalespersonName = null
        ).getOrThrow()

        assertTrue(repository.vans.value.any { it.vanId == cleanVan.vanId })
        val cleanDeleteRes = repository.deleteVan(cleanVan.vanId)
        assertTrue("Clean van deletion should succeed", cleanDeleteRes.isSuccess)
        assertFalse(repository.vans.value.any { it.vanId == cleanVan.vanId })
    }

    @Test
    fun testResolveUserWithMembership() = runTest {
        // An email address alone must never grant owner permissions.
        val ownerUser = com.example.kanak.domain.models.User(
            userId = "unassigned_uid",
            email = "zanabalmuhamed@gmail.com"
        )
        val resolvedOwner = repository.resolveUserWithMembership(ownerUser)
        assertEquals(com.example.kanak.domain.models.User.ROLE_STANDARD, resolvedOwner.role)
        assertFalse(resolvedOwner.isOwner)
        assertFalse(resolvedOwner.permissions.containsAll(com.example.kanak.domain.models.KanakPermissions.ALL_PERMISSIONS))

        // Create a van first
        val van = repository.createVan(
            vanCode = "VAN-01",
            vanName = "Van One",
            registrationNo = "111222",
            assignedSalespersonId = null,
            assignedSalespersonName = null
        ).getOrThrow()

        // Create a salesperson invite and accept
        val invite = repository.inviteEmployee(
            recipientEmail = "salesdriver@kanak-fmcg.bh",
            role = com.example.kanak.domain.models.User.ROLE_SALESPERSON,
            assignedVanId = van.vanId,
            permissions = setOf(
                com.example.kanak.domain.models.KanakPermissions.SALES_CREATE,
                com.example.kanak.domain.models.KanakPermissions.INVENTORY_READ_ASSIGNED
            ),
            invitedBy = "zanabalmuhamed@gmail.com"
        ).getOrThrow()

        val employeeUser = com.example.kanak.domain.models.User(
            userId = "emp_uid_456",
            email = "salesdriver@kanak-fmcg.bh"
        )

        repository.acceptInvitation(invite.inviteCode, employeeUser).getOrThrow()

        val resolvedEmployee = repository.resolveUserWithMembership(employeeUser)
        assertEquals(com.example.kanak.domain.models.User.ROLE_SALESPERSON, resolvedEmployee.role)
        assertEquals(van.vanId, resolvedEmployee.assignedVanId)
        assertEquals("VAN-01", resolvedEmployee.assignedVanCode)
        assertTrue(resolvedEmployee.hasPermission(com.example.kanak.domain.models.KanakPermissions.SALES_CREATE))
        assertFalse(resolvedEmployee.hasPermission(com.example.kanak.domain.models.KanakPermissions.SETTINGS_UPDATE))
    }

    @Test
    fun testBusinessSetupCompletionGate() = runTest {
        val superAdmin = "zanabalmuhamed@gmail.com"
        assertFalse("No account bypasses company setup without membership", repository.isBusinessSetupCompleted(superAdmin))

        val newUser = "newowner@fmcgdistribution.bh"
        assertFalse("New user with no registered business must fail the gate", repository.isBusinessSetupCompleted(newUser))

        val setupRes = repository.completeBusinessSetup(
            userId = "google_uid_owner_001",
            userEmail = newUser,
            companyName = "Manama FMCG Distribution",
            businessType = "Wholesale FMCG",
            address = "Building 100, Road 20, Block 300, Manama",
            phone = "+973 3300 9988",
            crNumber = "998877-1",
            vatNumber = "BH200099887700",
            spreadsheetId = "1TestSpreadsheetId_998877",
            createInitialVan = true,
            vanCode = "VAN-01",
            vanName = "Capital Fleet 01",
            registrationNo = "BH 55443",
            initialStaffName = "Hassan Al-Majid"
        )

        assertTrue("completeBusinessSetup must succeed", setupRes.isSuccess)
        assertTrue("After completing setup, user must pass the gate", repository.isBusinessSetupCompleted(newUser))

        // Verify that business details and initial van and owner were persisted
        assertEquals("Manama FMCG Distribution", repository.getBusinessName())
        assertEquals("Building 100, Road 20, Block 300, Manama", repository.getBusinessAddress())
        val vans = repository.vans.value
        assertTrue("Initial van must be present in repository", vans.any { it.vanCode == "VAN-01" })
        val staff = repository.teamMemberships.value
        assertTrue("Authenticated creator must receive admin membership", staff.any {
            it.userId == "google_uid_owner_001" &&
                it.email == newUser && it.role == com.example.kanak.domain.models.User.ROLE_ADMIN
        })
        assertFalse("Do not invent a staff member from a template name", staff.any { it.displayName == "Hassan Al-Majid" })
    }
}

