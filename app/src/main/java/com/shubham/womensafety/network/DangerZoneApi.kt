package com.shubham.womensafety.network

import retrofit2.Response
import retrofit2.http.GET

interface DangerZoneApi {
    @GET("api/v1/danger-zones")
    suspend fun getDangerZones(): Response<List<DangerZoneResponse>>
}
