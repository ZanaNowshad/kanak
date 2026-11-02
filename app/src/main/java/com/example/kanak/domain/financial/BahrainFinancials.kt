package com.example.kanak.domain.financial

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Bahrain Currency (BHD) and VAT Calculation Engine.
 *
 * Implements Section 8 of the Kanak Autonomous Engineering Directive:
 * - Currency: BHD with exactly 3 decimal places.
 * - Selling prices are VAT-inclusive.
 * - Standard VAT is 10% (0.10); support zero-rated (0.00).
 * - Exact line-level derivation:
 *     gross = quantity * unit_price
 *     net = gross / (1 + vat_rate)  [rounded to 3 decimals, HALF_UP]
 *     vat = gross - net
 * - Invariants:
 *     subtotal = sum(line_net)
 *     vat_total = sum(line_vat)
 *     total = sum(line_gross)
 *     subtotal + vat_total == total
 */
object BahrainFinancials {

    val STANDARD_VAT_RATE: BigDecimal = BigDecimal("0.10")
    val ZERO_VAT_RATE: BigDecimal = BigDecimal("0.00")
    private const val BHD_SCALE = 3
    private val ROUNDING = RoundingMode.HALF_UP

    private val bhdFormat = DecimalFormat("#,##0.000", DecimalFormatSymbols(Locale.US))

    fun scaleAmount(amount: BigDecimal): BigDecimal {
        return amount.setScale(BHD_SCALE, ROUNDING)
    }

    fun formatBhd(amount: BigDecimal): String {
        return "BHD ${bhdFormat.format(scaleAmount(amount))}"
    }

    fun formatBhdPlain(amount: BigDecimal): String {
        return bhdFormat.format(scaleAmount(amount))
    }

    fun parseBhd(value: String): BigDecimal {
        val cleaned = value.replace("BHD", "").replace(",", "").trim()
        if (cleaned.isBlank()) return BigDecimal.ZERO.setScale(BHD_SCALE, ROUNDING)
        return try {
            BigDecimal(cleaned).setScale(BHD_SCALE, ROUNDING)
        } catch (e: Exception) {
            BigDecimal.ZERO.setScale(BHD_SCALE, ROUNDING)
        }
    }

    /**
     * Calculates derived VAT amount for a single selling unit at the given VAT-inclusive selling price.
     */
    fun calculateUnitVat(sellingPrice: BigDecimal, vatRate: BigDecimal): BigDecimal {
        if (vatRate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(BHD_SCALE, ROUNDING)
        val divisor = BigDecimal.ONE.add(vatRate)
        val net = sellingPrice.divide(divisor, BHD_SCALE, ROUNDING)
        return sellingPrice.subtract(net).setScale(BHD_SCALE, ROUNDING)
    }

    data class LineCalculation(
        val qty: BigDecimal,
        val unitPrice: BigDecimal,
        val vatRate: BigDecimal,
        val gross: BigDecimal,
        val net: BigDecimal,
        val vatAmount: BigDecimal
    ) {
        val lineTotal: BigDecimal get() = gross
    }

    /**
     * Calculates authoritative line-level VAT and totals from VAT-inclusive unit price.
     */
    fun calculateLine(qty: BigDecimal, unitPrice: BigDecimal, vatRate: BigDecimal): LineCalculation {
        val scaledQty = qty.setScale(BHD_SCALE, ROUNDING)
        val scaledPrice = unitPrice.setScale(BHD_SCALE, ROUNDING)
        val scaledRate = vatRate.setScale(4, ROUNDING)

        val gross = scaledQty.multiply(scaledPrice).setScale(BHD_SCALE, ROUNDING)

        val net: BigDecimal
        val vat: BigDecimal

        if (scaledRate.compareTo(BigDecimal.ZERO) == 0) {
            net = gross
            vat = BigDecimal.ZERO.setScale(BHD_SCALE, ROUNDING)
        } else {
            val divisor = BigDecimal.ONE.add(scaledRate)
            net = gross.divide(divisor, BHD_SCALE, ROUNDING)
            vat = gross.subtract(net).setScale(BHD_SCALE, ROUNDING)
        }

        return LineCalculation(
            qty = scaledQty,
            unitPrice = scaledPrice,
            vatRate = scaledRate,
            gross = gross,
            net = net,
            vatAmount = vat
        )
    }

    data class InvoiceTotals(
        val subtotal: BigDecimal,
        val vatTotal: BigDecimal,
        val total: BigDecimal
    )

    /**
     * Reconciles invoice totals from line calculations.
     * Guarantees subtotal + vatTotal == total.
     */
    fun calculateInvoiceTotals(lines: List<LineCalculation>): InvoiceTotals {
        var subtotal = BigDecimal.ZERO.setScale(BHD_SCALE, ROUNDING)
        var vatTotal = BigDecimal.ZERO.setScale(BHD_SCALE, ROUNDING)
        var total = BigDecimal.ZERO.setScale(BHD_SCALE, ROUNDING)

        for (line in lines) {
            subtotal = subtotal.add(line.net)
            vatTotal = vatTotal.add(line.vatAmount)
            total = total.add(line.gross)
        }

        return InvoiceTotals(
            subtotal = subtotal.setScale(BHD_SCALE, ROUNDING),
            vatTotal = vatTotal.setScale(BHD_SCALE, ROUNDING),
            total = total.setScale(BHD_SCALE, ROUNDING)
        )
    }
}
