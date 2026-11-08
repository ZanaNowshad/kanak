package com.example.kanak.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class GoogleSheetProperties(
    val title: String,
    val sheetId: Int? = null,
    val index: Int? = null
)

@JsonClass(generateAdapter = true)
data class GoogleSheet(
    val properties: GoogleSheetProperties
)

@JsonClass(generateAdapter = true)
data class GoogleSpreadsheetProperties(
    val title: String
)

@JsonClass(generateAdapter = true)
data class GoogleSpreadsheet(
    val spreadsheetId: String? = null,
    val properties: GoogleSpreadsheetProperties? = null,
    val sheets: List<GoogleSheet>? = null,
    val spreadsheetUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleSpreadsheetCreateRequest(
    val properties: GoogleSpreadsheetProperties,
    val sheets: List<GoogleSheet>
)

@JsonClass(generateAdapter = true)
data class GoogleValueRange(
    val range: String? = null,
    val majorDimension: String = "ROWS",
    val values: List<List<Any?>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GoogleUpdateValuesResponse(
    val spreadsheetId: String? = null,
    val updatedRange: String? = null,
    val updatedRows: Int? = null,
    val updatedColumns: Int? = null,
    val updatedCells: Int? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAppendValuesResponse(
    val spreadsheetId: String? = null,
    val tableRange: String? = null,
    val updates: GoogleUpdateValuesResponse? = null
)

/**
 * Retrofit interface for Google Sheets API v4 endpoints.
 * Enables direct bi-directional synchronization of product data, customers, suppliers,
 * and daily sales logs between local Room database / Firestore and Google Sheets.
 */
interface GoogleSheetsApiService {

    @GET("https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}")
    suspend fun getSpreadsheet(
        @Header("Authorization") authHeader: String? = null,
        @Path("spreadsheetId") spreadsheetId: String
    ): Response<GoogleSpreadsheet>

    @POST("https://sheets.googleapis.com/v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Header("Authorization") authHeader: String? = null,
        @Body request: GoogleSpreadsheetCreateRequest
    ): Response<GoogleSpreadsheet>

    @GET("https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Header("Authorization") authHeader: String? = null,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String
    ): Response<GoogleValueRange>

    @PUT("https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Header("Authorization") authHeader: String? = null,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body body: GoogleValueRange
    ): Response<GoogleUpdateValuesResponse>

    @POST("https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Header("Authorization") authHeader: String? = null,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body body: GoogleValueRange
    ): Response<GoogleAppendValuesResponse>
}
