package com.example.kanak.printing

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.DailySalesSummaryData
import com.example.kanak.domain.models.Invoice
import java.math.BigDecimal

/**
 * Standard Android PrintManager helper for ESC/POS styled thermal receipts
 * and Daily Sales Summaries.
 * Allows printing directly to Android Print Spooler, Bluetooth/WiFi thermal printers,
 * or exporting directly as PDF.
 */
object KanakAndroidPrintHelper {

    fun printInvoiceReceipt(
        context: Context,
        invoice: Invoice,
        businessName: String = "KANAK FMCG Wholesale",
        businessAddress: String = "Road 2415, Block 324, Manama, Bahrain",
        vatNumber: String = "BH200012345600",
        crNumber: String = "123456-1"
    ) {
        val activity = findActivity(context) ?: return
        printInvoiceReceipt(activity, invoice, businessName, businessAddress, vatNumber, crNumber)
    }

    fun printInvoiceReceipt(
        activity: Activity,
        invoice: Invoice,
        businessName: String = "KANAK FMCG Wholesale",
        businessAddress: String = "Road 2415, Block 324, Manama, Bahrain",
        vatNumber: String = "BH200012345600",
        crNumber: String = "123456-1"
    ) {
        val htmlContent = buildInvoiceHtml(invoice, businessName, businessAddress, vatNumber, crNumber)
        printHtml(activity, htmlContent, "Kanak_Invoice_${invoice.invoiceNumber}")
    }

    fun printDailySalesSummary(
        context: Context,
        summary: DailySalesSummaryData,
        businessName: String = "KANAK FMCG Wholesale",
        vatNumber: String = "BH200012345600",
        crNumber: String = "123456-1"
    ) {
        val activity = findActivity(context) ?: return
        printDailySalesSummary(activity, summary, businessName, vatNumber, crNumber)
    }

    fun printDailySalesSummary(
        activity: Activity,
        summary: DailySalesSummaryData,
        businessName: String = "KANAK FMCG Wholesale",
        vatNumber: String = "BH200012345600",
        crNumber: String = "123456-1"
    ) {
        val htmlContent = buildDailySummaryHtml(summary, businessName, vatNumber, crNumber)
        printHtml(activity, htmlContent, "Kanak_DailySummary_${summary.sessionDate.replace(" ", "_")}")
    }

    fun findActivity(context: Context): Activity? {
        var current = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun printHtml(activity: Activity, html: String, jobName: String) {
        activity.runOnUiThread {
            val webView = WebView(activity)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager.print(jobName, printAdapter, attributes)
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    private fun buildInvoiceHtml(
        invoice: Invoice,
        businessName: String,
        businessAddress: String,
        vatNumber: String,
        crNumber: String
    ): String {
        val rows = StringBuilder()
        for (line in invoice.lines) {
            val unitPriceStr = BahrainFinancials.formatBhd(line.unitPrice)
            val lineTotalStr = BahrainFinancials.formatBhd(line.lineTotal)
            val qtyStr = line.qty.stripTrailingZeros().toPlainString()
            rows.append(
                """
                <tr>
                    <td style="padding: 6px 0; border-bottom: 1px dashed #ccc;">
                        <div style="font-weight: bold; font-size: 13px;">${escapeHtml(line.productName)}</div>
                        <div style="font-size: 11px; color: #555;">$qtyStr &times; $unitPriceStr (VAT 10%)</div>
                    </td>
                    <td style="text-align: right; vertical-align: top; padding: 6px 0; border-bottom: 1px dashed #ccc; font-weight: bold; font-size: 13px;">
                        $lineTotalStr
                    </td>
                </tr>
                """.trimIndent()
            )
        }

        val paymentBadge = when {
            invoice.isVoid -> "<span style='color: red; font-weight: bold;'>[ VOIDED ]</span>"
            !invoice.isCredit -> "<span style='color: green; font-weight: bold;'>[ CASH - PAID ]</span>"
            invoice.isPaid -> "<span style='color: green; font-weight: bold;'>[ CREDIT - PAID ]</span>"
            else -> "<span style='color: #d97706; font-weight: bold;'>[ CREDIT - DUE BHD ${BahrainFinancials.formatBhd(invoice.balanceDue)} ]</span>"
        }

        val vanLine = if (invoice.vanCode.isNotBlank()) "<div style='font-size: 11px;'>Van: <strong>${escapeHtml(invoice.vanCode)}</strong></div>" else ""

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    font-family: 'Courier New', Courier, monospace, sans-serif;
                    margin: 0;
                    padding: 12px;
                    color: #000;
                    max-width: 380px;
                    margin: 0 auto;
                }
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .bold { font-weight: bold; }
                .header-title { font-size: 20px; font-weight: 900; letter-spacing: 1px; margin-bottom: 2px; }
                .sub-title { font-size: 12px; margin-bottom: 2px; }
                .divider { border-top: 1px dashed #000; margin: 8px 0; }
                .double-divider { border-top: 2px solid #000; margin: 8px 0; }
                table { width: 100%; border-collapse: collapse; }
                .totals-row td { padding: 3px 0; font-size: 13px; }
                .grand-total { font-size: 16px; font-weight: 900; }
            </style>
        </head>
        <body>
            <div class="text-center">
                <div class="header-title">KANAK</div>
                <div class="sub-title bold">${escapeHtml(businessName)}</div>
                <div class="sub-title">${escapeHtml(businessAddress)}</div>
                <div class="sub-title">CR: <strong>$crNumber</strong> | VAT TRN: <strong>$vatNumber</strong></div>
                <div style="font-size: 11px; margin-top: 4px;">TAX INVOICE / SIMPLIFIED TAX INVOICE</div>
            </div>

            <div class="divider"></div>

            <div style="font-size: 12px; line-height: 1.4;">
                <div>Invoice No: <strong>${escapeHtml(invoice.invoiceNumber)}</strong></div>
                <div>Date: <strong>${escapeHtml(invoice.date.take(16).replace("T", " "))}</strong></div>
                <div>Customer: <strong>${escapeHtml(invoice.customerName)}</strong></div>
                <div>Cashier: <strong>${escapeHtml(invoice.createdBy)}</strong></div>
                $vanLine
                <div style="margin-top: 4px;">Terms: $paymentBadge</div>
            </div>

            <div class="divider"></div>

            <table>
                <thead>
                    <tr style="border-bottom: 1px solid #000; font-size: 11px;">
                        <th style="text-align: left; padding-bottom: 4px;">ITEM</th>
                        <th style="text-align: right; padding-bottom: 4px;">TOTAL</th>
                    </tr>
                </thead>
                <tbody>
                    $rows
                </tbody>
            </table>

            <div class="divider"></div>

            <table>
                <tr class="totals-row">
                    <td>Subtotal (Net of VAT):</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(invoice.subtotal)}</td>
                </tr>
                <tr class="totals-row">
                    <td>VAT (10% NBR Bahrain):</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(invoice.vatTotal)}</td>
                </tr>
                <tr class="double-divider"></tr>
                <tr class="totals-row grand-total">
                    <td>TOTAL PAYABLE:</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(invoice.total)}</td>
                </tr>
                ${if (invoice.isCredit) """
                <tr class="totals-row" style="color: #16a34a;">
                    <td>Amount Paid:</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(invoice.amountPaid)}</td>
                </tr>
                <tr class="totals-row" style="color: #dc2626; font-weight: bold;">
                    <td>Balance Due:</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(invoice.balanceDue)}</td>
                </tr>
                """ else ""}
            </table>

            <div class="divider"></div>

            <div class="text-center" style="font-size: 11px; margin-top: 10px; line-height: 1.4;">
                <div class="bold">THANK YOU FOR YOUR VALUED BUSINESS!</div>
                <div>Returns accepted within 24 hours with valid receipt.</div>
                <div style="margin-top: 6px; font-size: 10px; color: #555;">'Kanak' Fleet & Van Wholesale System &bull; zedosai.com</div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildDailySummaryHtml(
        summary: DailySalesSummaryData,
        businessName: String,
        vatNumber: String,
        crNumber: String
    ): String {
        val vanInfo = summary.vanCode ?: "All Fleet Vans"
        val rows = StringBuilder()
        for (inv in summary.invoices) {
            val terms = if (inv.isCredit) "CREDIT" else "CASH"
            rows.append(
                """
                <tr style="font-size: 11px; border-bottom: 1px dotted #ccc;">
                    <td style="padding: 4px 0;">${escapeHtml(inv.invoiceNumber)}<br><span style="color: #555;">${escapeHtml(inv.customerName.take(18))}</span></td>
                    <td style="text-align: center;">$terms</td>
                    <td style="text-align: right; font-weight: bold;">${BahrainFinancials.formatBhd(inv.total)}</td>
                </tr>
                """.trimIndent()
            )
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    font-family: 'Courier New', Courier, monospace, sans-serif;
                    margin: 0;
                    padding: 12px;
                    color: #000;
                    max-width: 420px;
                    margin: 0 auto;
                }
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .bold { font-weight: bold; }
                .header-title { font-size: 18px; font-weight: 900; }
                .divider { border-top: 1px dashed #000; margin: 8px 0; }
                .double-divider { border-top: 2px solid #000; margin: 8px 0; }
                table { width: 100%; border-collapse: collapse; }
                .totals-row td { padding: 4px 0; font-size: 12px; }
            </style>
        </head>
        <body>
            <div class="text-center">
                <div class="header-title">DAILY SALES SUMMARY</div>
                <div class="bold">${escapeHtml(businessName)}</div>
                <div style="font-size: 11px;">CR: $crNumber | VAT TRN: $vatNumber</div>
                <div style="font-size: 12px; margin-top: 4px;">Session: <strong>${escapeHtml(summary.sessionDate)}</strong></div>
                <div style="font-size: 12px;">Van Route: <strong>$vanInfo</strong></div>
            </div>

            <div class="double-divider"></div>

            <div class="bold" style="font-size: 13px; margin-bottom: 4px;">SALES & REVENUE SUMMARY</div>
            <table>
                <tr class="totals-row">
                    <td>Total Invoices Issued:</td>
                    <td class="text-right bold">${summary.totalInvoicesCount}</td>
                </tr>
                <tr class="totals-row">
                    <td>Total Gross Sales (BHD):</td>
                    <td class="text-right bold" style="font-size: 14px;">BHD ${BahrainFinancials.formatBhd(summary.totalGrossSales)}</td>
                </tr>
                <tr class="divider"></tr>
                <tr class="totals-row" style="color: #16a34a;">
                    <td>&bull; Cash Sales (${summary.cashInvoicesCount} bills):</td>
                    <td class="text-right bold">BHD ${BahrainFinancials.formatBhd(summary.totalCashSales)}</td>
                </tr>
                <tr class="totals-row" style="color: #d97706;">
                    <td>&bull; Credit Sales (${summary.creditInvoicesCount} bills):</td>
                    <td class="text-right bold">BHD ${BahrainFinancials.formatBhd(summary.totalCreditSales)}</td>
                </tr>
                <tr class="totals-row">
                    <td>&bull; Cash Collected on Credit:</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(summary.cashCollectedOnCredit)}</td>
                </tr>
                <tr class="totals-row" style="color: #dc2626;">
                    <td>&bull; Outstanding Credit Balance Due:</td>
                    <td class="text-right bold">BHD ${BahrainFinancials.formatBhd(summary.totalOutstandingCredit)}</td>
                </tr>
            </table>

            <div class="divider"></div>

            <div class="bold" style="font-size: 13px; margin-bottom: 4px;">TAX & VAT BREAKDOWN (NBR BAHRAIN)</div>
            <table>
                <tr class="totals-row">
                    <td>Taxable Net Sales:</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(summary.taxableSubtotal)}</td>
                </tr>
                <tr class="totals-row">
                    <td>Standard VAT Rate:</td>
                    <td class="text-right">10.000%</td>
                </tr>
                <tr class="totals-row" style="font-weight: bold;">
                    <td>Total VAT Collected:</td>
                    <td class="text-right">BHD ${BahrainFinancials.formatBhd(summary.vatAmountCollected)}</td>
                </tr>
            </table>

            <div class="divider"></div>

            <div class="bold" style="font-size: 12px; margin-bottom: 4px;">BILLS AUDIT TRAIL (${summary.invoices.size})</div>
            <table>
                <thead>
                    <tr style="font-size: 10px; border-bottom: 1px solid #000;">
                        <th style="text-align: left;">INV # / CUSTOMER</th>
                        <th style="text-align: center;">TERMS</th>
                        <th style="text-align: right;">AMOUNT</th>
                    </tr>
                </thead>
                <tbody>
                    $rows
                </tbody>
            </table>

            <div class="divider"></div>

            <div class="text-center" style="font-size: 10px; color: #555; margin-top: 10px;">
                Report generated via Kanak Mobile POS &bull; Official Bahrain FMCG Engine
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
