package com.example.kanak.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Production-ready Google Sheets API Client & Service Class.
 * Configured with Retrofit and OkHttp to automatically sign requests using OAuth 2.0 Bearer tokens.
 * Handles high-level operations on specified Google Sheet IDs:
 * - Create spreadsheets with customizable sheets
 * - Read rows from specified sheet ranges
 * - Append rows to specified sheet ranges (e.g. DailySalesLogs)
 * - Update/overwrite cell ranges
 */
class GoogleSheetsService(
    private val tokenProvider: () -> String?
) {
    companion object {
        const val BASE_URL = "https://sheets.googleapis.com/"
        private const val TAG = "GoogleSheetsService"
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        // Attach OAuth 2.0 Bearer token if not explicitly set
        if (original.header("Authorization") == null) {
            val token = tokenProvider()
            if (!token.isNullOrBlank()) {
                val headerVal = if (token.startsWith("Bearer ")) token else "Bearer $token"
                builder.header("Authorization", headerVal)
            }
        }
        builder.header("Accept", "application/json")
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: GoogleSheetsApiService = retrofit.create(GoogleSheetsApiService::class.java)

    /**
     * Creates a new Google Spreadsheet with the given title and tabs.
     * Returns the created spreadsheet ID.
     */
    suspend fun createSpreadsheet(
        title: String,
        sheetTitles: List<String> = listOf("Products", "Customers", "Suppliers", "DailySalesLogs")
    ): Result<String> {
        return try {
            val request = GoogleSpreadsheetCreateRequest(
                properties = GoogleSpreadsheetProperties(title = title),
                sheets = sheetTitles.mapIndexed { idx, name ->
                    GoogleSheet(properties = GoogleSheetProperties(title = name, index = idx))
                }
            )
            val response = api.createSpreadsheet(request = request)
            if (response.isSuccessful && response.body()?.spreadsheetId != null) {
                val sheetId = response.body()!!.spreadsheetId!!
                Log.i(TAG, "Created spreadsheet '$title' with ID: $sheetId")
                Result.success(sheetId)
            } else {
                val error = "Failed to create spreadsheet (HTTP ${response.code()}): ${response.errorBody()?.string()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating spreadsheet: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Reads rows from a specified range in the specified Google Sheet ID.
     * @param spreadsheetId The target Google Sheet ID
     * @param range Range expression, e.g. "Products!A2:J1000"
     */
    suspend fun readRows(
        spreadsheetId: String,
        range: String
    ): Result<List<List<Any?>>> {
        return try {
            val response = api.getValues(spreadsheetId = spreadsheetId, range = range)
            if (response.isSuccessful && response.body() != null) {
                val values = response.body()?.values ?: emptyList()
                Log.d(TAG, "Read ${values.size} rows from '$range' in sheet: $spreadsheetId")
                Result.success(values)
            } else {
                val error = "Failed to read rows from '$range' (HTTP ${response.code()}): ${response.errorBody()?.string()}"
                Log.w(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading rows from '$range': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Appends rows to a specified range in the specified Google Sheet ID.
     * @param spreadsheetId The target Google Sheet ID
     * @param range Target range, e.g. "DailySalesLogs!A:K"
     * @param rows List of row values
     * @param valueInputOption "USER_ENTERED" (parses formulas/currencies) or "RAW"
     */
    suspend fun appendRows(
        spreadsheetId: String,
        range: String,
        rows: List<List<Any?>>,
        valueInputOption: String = "USER_ENTERED"
    ): Result<Int> {
        if (rows.isEmpty()) return Result.success(0)
        return try {
            val body = GoogleValueRange(range = range, values = rows)
            val response = api.appendValues(
                spreadsheetId = spreadsheetId,
                range = range,
                valueInputOption = valueInputOption,
                body = body
            )
            if (response.isSuccessful) {
                val appended = response.body()?.updates?.updatedRows ?: rows.size
                Log.i(TAG, "Appended $appended rows to '$range' in sheet: $spreadsheetId")
                Result.success(appended)
            } else {
                val error = "Failed to append rows to '$range' (HTTP ${response.code()}): ${response.errorBody()?.string()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception appending rows to '$range': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates rows in a specified range in the specified Google Sheet ID.
     * @param spreadsheetId The target Google Sheet ID
     * @param range Exact range to overwrite, e.g. "Products!A2:J50"
     * @param rows List of row values
     * @param valueInputOption "USER_ENTERED" or "RAW"
     */
    suspend fun updateRows(
        spreadsheetId: String,
        range: String,
        rows: List<List<Any?>>,
        valueInputOption: String = "USER_ENTERED"
    ): Result<Int> {
        if (rows.isEmpty()) return Result.success(0)
        return try {
            val body = GoogleValueRange(range = range, values = rows)
            val response = api.updateValues(
                spreadsheetId = spreadsheetId,
                range = range,
                valueInputOption = valueInputOption,
                body = body
            )
            if (response.isSuccessful) {
                val updated = response.body()?.updatedRows ?: rows.size
                Log.i(TAG, "Updated $updated rows in '$range' in sheet: $spreadsheetId")
                Result.success(updated)
            } else {
                val error = "Failed to update rows in '$range' (HTTP ${response.code()}): ${response.errorBody()?.string()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating rows in '$range': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves spreadsheet metadata and worksheet tabs.
     */
    suspend fun getSpreadsheetMetadata(spreadsheetId: String): Result<GoogleSpreadsheet> {
        return try {
            val response = api.getSpreadsheet(spreadsheetId = spreadsheetId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()} failed to get spreadsheet metadata"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
