package com.example.kanak.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class RegisterCompanyRequest(
    @Json(name = "company_name") val companyName: String,
    @Json(name = "cr_number") val crNumber: String,
    @Json(name = "trn_number") val trnNumber: String,
    val phone: String,
    val address: String,
    @Json(name = "initial_van_code") val initialVanCode: String? = null,
    @Json(name = "initial_van_plate") val initialVanPlate: String? = null,
    @Json(name = "initial_van_name") val initialVanName: String? = null
)

@JsonClass(generateAdapter = true)
data class RegisteredCompanyResponse(
    @Json(name = "tenant_id") val tenantId: String,
    @Json(name = "user_id") val userId: String,
    val email: String,
    val role: String,
    @Json(name = "company_name") val companyName: String,
    @Json(name = "initial_van_id") val initialVanId: String? = null
)

interface KanakBackendApiService {
    @POST("api/v1/auth/companies")
    suspend fun registerCompany(
        @Header("Authorization") authorization: String,
        @Body company: RegisterCompanyRequest
    ): Response<RegisteredCompanyResponse>

    @POST("api/v1/sync/outbox/flush")
    suspend fun flushOutbox(
        @Header("Authorization") authorization: String,
        @Body request: OutboxUploadRequest
    ): Response<OutboxUploadResponse>
}

@JsonClass(generateAdapter = true)
data class OutboxUploadItem(
    val operation: String,
    @Json(name = "entity_id") val entityId: String,
    val payload: Map<String, Any?>,
    val timestamp: String
)

@JsonClass(generateAdapter = true)
data class OutboxUploadRequest(
    @Json(name = "tenant_id") val tenantId: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "van_id") val vanId: String?,
    val items: List<OutboxUploadItem>
)

@JsonClass(generateAdapter = true)
data class OutboxUploadResponse(
    val success: Boolean,
    @Json(name = "processed_count") val processedCount: Int,
    @Json(name = "rejected_count") val rejectedCount: Int,
    @Json(name = "acknowledged_ids") val acknowledgedIds: List<String>,
    @Json(name = "rejected_ids") val rejectedIds: List<String>,
    val errors: List<String>
)
