package com.example.kanak.printing

import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Invoice
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * ESC/POS Thermal Receipt Renderer.
 * Implements Section 15 of the Kanak Autonomous Android Engineering Directive:
 * - 58mm (32 chars per line default) and 80mm compatible thermal layouts.
 * - Exact BHD 3-decimal formatting.
 * - Tabular alignment and clean line wrapping for long product names.
 * - Standard ESC/POS command byte sequences.
 */
class EscPosReceiptRenderer(
    private val columnWidth: Int = 32,
    private val businessName: String = "KANAK TRADING W.L.L.",
    private val businessAddress: String = "Road 2415, Block 324, Manama, Bahrain",
    private val vatNumber: String = "BH200012345600"
) {

    companion object {
        // ESC/POS Command Constants
        val ESC_INIT = byteArrayOf(0x1B, 0x40) // ESC @
        val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
        val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
        val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
        val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
        val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
        val ESC_DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)
        val ESC_DOUBLE_OFF = byteArrayOf(0x1B, 0x21, 0x00)
        val GS_CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0
        const val LF = 0x0A.toByte()
    }

    /**
     * Renders a human-readable text receipt (useful for previews, verification, and logs).
     */
    fun renderPlainText(invoice: Invoice): String {
        val sep = "-".repeat(columnWidth)
        val sb = StringBuilder()

        sb.append(centerText("KANAK", columnWidth)).append("\n")
        if (businessName.isNotBlank()) sb.append(centerText(businessName, columnWidth)).append("\n")
        if (businessAddress.isNotBlank()) sb.append(centerText(businessAddress, columnWidth)).append("\n")
        if (vatNumber.isNotBlank()) sb.append(centerText("VAT TRN: $vatNumber", columnWidth)).append("\n")
        sb.append(sep).append("\n")

        sb.append("Invoice: ${invoice.invoiceNumber}\n")
        sb.append("Date: ${invoice.date}\n")
        sb.append("Customer: ${invoice.customerName}\n")
        sb.append(sep).append("\n")

        // Table header: 16 chars for name, 5 for qty, 11 for total in 32 col
        val nameWidth = columnWidth - 16
        sb.append(padRight("Product", nameWidth))
            .append(padLeft("Qty", 5))
            .append(padLeft("Total", 11))
            .append("\n")
        sb.append(sep).append("\n")

        for (line in invoice.lines) {
            val totalStr = BahrainFinancials.formatBhdPlain(line.lineTotal)
            val qtyStr = line.qty.stripTrailingZeros().toPlainString()

            val name = line.productName
            if (name.length <= nameWidth) {
                sb.append(padRight(name, nameWidth))
                    .append(padLeft(qtyStr, 5))
                    .append(padLeft(totalStr, 11))
                    .append("\n")
            } else {
                // Wrap product name across multiple lines
                val firstPart = name.take(nameWidth)
                sb.append(padRight(firstPart, nameWidth))
                    .append(padLeft(qtyStr, 5))
                    .append(padLeft(totalStr, 11))
                    .append("\n")
                var remaining = name.drop(nameWidth)
                while (remaining.isNotEmpty()) {
                    val chunk = remaining.take(nameWidth)
                    sb.append(padRight(chunk, nameWidth)).append("\n")
                    remaining = remaining.drop(nameWidth)
                }
            }
        }

        sb.append(sep).append("\n")
        sb.append(padRight("Subtotal", columnWidth - 11)).append(padLeft(BahrainFinancials.formatBhdPlain(invoice.subtotal), 11)).append("\n")
        sb.append(padRight("VAT Total (10%)", columnWidth - 11)).append(padLeft(BahrainFinancials.formatBhdPlain(invoice.vatTotal), 11)).append("\n")
        sb.append(padRight("TOTAL (BHD)", columnWidth - 11)).append(padLeft(BahrainFinancials.formatBhdPlain(invoice.total), 11)).append("\n")
        sb.append(sep).append("\n")

        sb.append(centerText("Thank you for your business!", columnWidth)).append("\n")
        sb.append(centerText("Powered by 'Kanak' by zedosai.com", columnWidth)).append("\n")
        return sb.toString()
    }

    /**
     * Generates complete binary ESC/POS command stream for direct transmission to thermal printer.
     */
    fun renderEscPosBytes(invoice: Invoice): ByteArray {
        val out = ByteArrayOutputStream()

        out.write(ESC_INIT)

        // Header (Centered, bold title)
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write(ESC_DOUBLE_HEIGHT_ON)
        out.write("KANAK\n".toByteArray(StandardCharsets.US_ASCII))
        out.write(ESC_DOUBLE_OFF)
        out.write("by zedosai.com\n".toByteArray(StandardCharsets.US_ASCII))
        out.write(ESC_BOLD_OFF)

        if (businessName.isNotBlank()) {
            out.write("$businessName\n".toByteArray(StandardCharsets.US_ASCII))
        }
        if (businessAddress.isNotBlank()) {
            out.write("$businessAddress\n".toByteArray(StandardCharsets.US_ASCII))
        }
        if (vatNumber.isNotBlank()) {
            out.write("VAT TRN: $vatNumber\n".toByteArray(StandardCharsets.US_ASCII))
        }

        val sep = ("-".repeat(columnWidth) + "\n").toByteArray(StandardCharsets.US_ASCII)
        out.write(sep)

        // Invoice Meta (Left aligned)
        out.write(ESC_ALIGN_LEFT)
        out.write("Invoice: ${invoice.invoiceNumber}\n".toByteArray(StandardCharsets.US_ASCII))
        out.write("Date: ${invoice.date}\n".toByteArray(StandardCharsets.US_ASCII))
        out.write("Customer: ${invoice.customerName}\n".toByteArray(StandardCharsets.US_ASCII))
        out.write(sep)

        // Column Header
        val nameWidth = columnWidth - 16
        val colHeader = padRight("Product", nameWidth) + padLeft("Qty", 5) + padLeft("Total", 11) + "\n"
        out.write(ESC_BOLD_ON)
        out.write(colHeader.toByteArray(StandardCharsets.US_ASCII))
        out.write(ESC_BOLD_OFF)
        out.write(sep)

        // Lines
        for (line in invoice.lines) {
            val totalStr = BahrainFinancials.formatBhdPlain(line.lineTotal)
            val qtyStr = line.qty.stripTrailingZeros().toPlainString()
            val name = line.productName

            if (name.length <= nameWidth) {
                val row = padRight(name, nameWidth) + padLeft(qtyStr, 5) + padLeft(totalStr, 11) + "\n"
                out.write(row.toByteArray(StandardCharsets.US_ASCII))
            } else {
                val firstPart = name.take(nameWidth)
                val row = padRight(firstPart, nameWidth) + padLeft(qtyStr, 5) + padLeft(totalStr, 11) + "\n"
                out.write(row.toByteArray(StandardCharsets.US_ASCII))
                var remaining = name.drop(nameWidth)
                while (remaining.isNotEmpty()) {
                    val chunk = remaining.take(nameWidth)
                    out.write((padRight(chunk, nameWidth) + "\n").toByteArray(StandardCharsets.US_ASCII))
                    remaining = remaining.drop(nameWidth)
                }
            }
        }

        out.write(sep)

        // Totals
        val subtotalRow = padRight("Subtotal", columnWidth - 11) + padLeft(BahrainFinancials.formatBhdPlain(invoice.subtotal), 11) + "\n"
        val vatRow = padRight("VAT Total (10%)", columnWidth - 11) + padLeft(BahrainFinancials.formatBhdPlain(invoice.vatTotal), 11) + "\n"
        out.write(subtotalRow.toByteArray(StandardCharsets.US_ASCII))
        out.write(vatRow.toByteArray(StandardCharsets.US_ASCII))

        out.write(ESC_BOLD_ON)
        val grandTotalRow = padRight("TOTAL (BHD)", columnWidth - 11) + padLeft(BahrainFinancials.formatBhdPlain(invoice.total), 11) + "\n"
        out.write(grandTotalRow.toByteArray(StandardCharsets.US_ASCII))
        out.write(ESC_BOLD_OFF)
        out.write(sep)

        // Footer
        out.write(ESC_ALIGN_CENTER)
        out.write("Thank you for your business!\n".toByteArray(StandardCharsets.US_ASCII))
        out.write(byteArrayOf(LF, LF, LF, LF))

        // Cut
        out.write(GS_CUT_PARTIAL)

        return out.toByteArray()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val leftPadding = (width - text.length) / 2
        val rightPadding = width - text.length - leftPadding
        return " ".repeat(leftPadding) + text + " ".repeat(rightPadding)
    }

    private fun padRight(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        return text + " ".repeat(width - text.length)
    }

    private fun padLeft(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        return " ".repeat(width - text.length) + text
    }
}
