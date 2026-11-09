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
    val address: String
)

@JsonClass(generateAdapter = true)
data class RegisteredCompanyResponse(
    @Json(name = "tenant_id") val tenantId: String,
    @Json(name = "user_id") val userId: String,
    val email: String,
    val role: String,
    @Json(name = "company_name") val companyName: String
)

interface KanakBackendApiService {
    @POST("api/v1/auth/companies")
    suspend fun registerCompany(
        @Header("Authorization") authorization: String,
        @Body company: RegisterCompanyRequest
    ): Response<RegisteredCompanyResponse>
}
