package com.example.kanak.images

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Authoritative Bing Thumbnail URL Builder.
 * Implements Section 10 of the Kanak Autonomous Android Engineering Directive:
 * - Default product image derived using barcode + product name query.
 * - Normalized whitespace.
 * - Form-encoded UTF-8 so spaces become '+' and special characters are encoded correctly.
 * - Format: https://tse1.mm.bing.net/th?q={URL_ENCODED_BARCODE_AND_PRODUCT_NAME}
 * - No paid Bing Search API key required.
 */
object BingThumbnailUrlBuilder {

    /**
     * Builds the default automatic candidate image URL from barcode and product name.
     */
    fun buildDefaultProductImageUrl(
        barcode: String,
        productName: String
    ): String? {
        val query = listOf(
            barcode.trim(),
            productName.trim()
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (query.isBlank()) return null

        val encoded = URLEncoder.encode(
            query,
            StandardCharsets.UTF_8.name()
        )

        return "https://tse1.mm.bing.net/th?q=$encoded"
    }

    /**
     * Checks if a given URL was automatically generated from the given barcode & name.
     */
    fun isGeneratedUrl(url: String?, barcode: String, productName: String): Boolean {
        if (url == null) return false
        val expected = buildDefaultProductImageUrl(barcode, productName)
        return url == expected
    }
}
