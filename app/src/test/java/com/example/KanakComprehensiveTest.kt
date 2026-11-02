package com.example

import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Invoice
import com.example.kanak.domain.models.InvoiceLine
import com.example.kanak.images.BingThumbnailUrlBuilder
import com.example.kanak.printing.EscPosReceiptRenderer
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests verifying financial calculations, image URL builder, and receipt rendering
 * per Section 27 of the Kanak Autonomous Android Engineering Directive.
 */
class KanakComprehensiveTest {

    @Test
    fun testBahrainVatCalculation_StandardExample() {
        // Example from Section 8.5:
        // Product: Selling Price BHD 1.100 (VAT-inclusive), 10% VAT, Qty 2
        // Expected: Net 2.000, VAT 0.200, Total 2.200
        val qty = BigDecimal("2")
        val unitPrice = BigDecimal("1.100")
        val vatRate = BigDecimal("0.10")

        val line = BahrainFinancials.calculateLine(qty, unitPrice, vatRate)

        assertEquals("Total should be 2.200", BigDecimal("2.200"), line.gross)
        assertEquals("Net should be 2.000", BigDecimal("2.000"), line.net)
        assertEquals("VAT should be 0.200", BigDecimal("0.200"), line.vatAmount)
        assertEquals("Net + VAT must equal Gross", line.gross, line.net.add(line.vatAmount))
    }

    @Test
    fun testBahrainVatCalculation_ZeroRatedProduct() {
        val qty = BigDecimal("5")
        val unitPrice = BigDecimal("1.100")
        val vatRate = BigDecimal("0.00")

        val line = BahrainFinancials.calculateLine(qty, unitPrice, vatRate)

        assertEquals(BigDecimal("5.500"), line.gross)
        assertEquals(BigDecimal("5.500"), line.net)
        assertEquals(BigDecimal("0.000"), line.vatAmount)
    }

    @Test
    fun testBahrainInvoiceTotalsReconciliation() {
        // Multi-line invoice with mixed standard and zero rates
        val line1 = BahrainFinancials.calculateLine(BigDecimal("3"), BigDecimal("1.250"), BigDecimal("0.10"))
        val line2 = BahrainFinancials.calculateLine(BigDecimal("2"), BigDecimal("0.800"), BigDecimal("0.00"))

        val totals = BahrainFinancials.calculateInvoiceTotals(listOf(line1, line2))

        assertEquals("Total must match sum of line gross totals", line1.gross.add(line2.gross), totals.total)
        assertEquals("Subtotal + VAT must equal Grand Total", totals.total, totals.subtotal.add(totals.vatTotal))
    }

    @Test
    fun testBhdFormatting() {
        val amount = BigDecimal("1.2")
        val formatted = BahrainFinancials.formatBhd(amount)
        assertEquals("BHD 1.200", formatted)

        val parsed = BahrainFinancials.parseBhd("BHD 12.500")
        assertEquals(BigDecimal("12.500"), parsed)
    }

    @Test
    fun testBingThumbnailUrlBuilder_ScreenshotExample() {
        // Test case matching Section 10.1 of specification:
        // Barcode: 6767647641365
        // Product Name: 10 Colour Flame Candles
        // Expected: https://tse1.mm.bing.net/th?q=6767647641365+10+Colour+Flame+Candles
        val barcode = "6767647641365"
        val name = "10 Colour Flame Candles"

        val url = BingThumbnailUrlBuilder.buildDefaultProductImageUrl(barcode, name)
        assertNotNull(url)
        assertEquals("https://tse1.mm.bing.net/th?q=6767647641365+10+Colour+Flame+Candles", url)
    }

    @Test
    fun testBingThumbnailUrlBuilder_WhitespaceNormalization() {
        val barcode = "  6281007000018  "
        val name = "Almarai   Fresh   Milk   2L"

        val url = BingThumbnailUrlBuilder.buildDefaultProductImageUrl(barcode, name)
        assertNotNull(url)
        assertEquals("https://tse1.mm.bing.net/th?q=6281007000018+Almarai+Fresh+Milk+2L", url)
    }

    @Test
    fun testBingThumbnailUrlBuilder_EmptyInputs() {
        assertNull(BingThumbnailUrlBuilder.buildDefaultProductImageUrl("", ""))
        assertNull(BingThumbnailUrlBuilder.buildDefaultProductImageUrl("   ", "   "))
    }

    @Test
    fun testEscPosReceiptRenderer() {
        val invoice = Invoice(
            invoiceId = "inv_test_001",
            invoiceNumber = "INV-2026-0001",
            customerId = "cust_001",
            customerName = "Al-Hilal Cold Store",
            date = "2026-10-30 14:30",
            subtotal = BigDecimal("3.136"),
            vatTotal = BigDecimal("0.314"),
            total = BigDecimal("3.450"),
            status = Invoice.STATUS_POSTED,
            createdBy = "usr_001",
            lines = listOf(
                InvoiceLine(
                    lineId = "line_1",
                    invoiceId = "inv_test_001",
                    productId = "prod_1",
                    productName = "Almarai Fresh Milk 2L",
                    qty = BigDecimal("2.000"),
                    unitPrice = BigDecimal("1.100"),
                    vatRate = BigDecimal("0.10"),
                    vatAmount = BigDecimal("0.200"),
                    lineTotal = BigDecimal("2.200")
                ),
                InvoiceLine(
                    lineId = "line_2",
                    invoiceId = "inv_test_001",
                    productId = "prod_2",
                    productName = "Lurpak Salted Butter",
                    qty = BigDecimal("1.000"),
                    unitPrice = BigDecimal("1.250"),
                    vatRate = BigDecimal("0.10"),
                    vatAmount = BigDecimal("0.114"),
                    lineTotal = BigDecimal("1.250")
                )
            )
        )

        val renderer = EscPosReceiptRenderer(columnWidth = 32)
        val plainText = renderer.renderPlainText(invoice)

        assertTrue(plainText.contains("KANAK"))
        assertTrue(plainText.contains("INV-2026-0001"))
        assertTrue(plainText.contains("Al-Hilal Cold Store"))
        assertTrue(plainText.contains("TOTAL (BHD)"))
        assertTrue(plainText.contains("3.450"))

        val bytes = renderer.renderEscPosBytes(invoice)
        assertTrue(bytes.isNotEmpty())
        // Verify ESC @ (0x1B, 0x40) initialization is at start
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
    }
}
