package com.example.kanak.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class GatewayRequest(
    val action: String,
    val idToken: String,
    val payload: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GatewayResponse(
    val success: Boolean,
    val error: String? = null,
    val data: Any? = null
)

interface KanakApiService {
    @POST
    suspend fun executeGatewayAction(
        @Url url: String,
        @Body request: GatewayRequest
    ): Response<GatewayResponse>
}
