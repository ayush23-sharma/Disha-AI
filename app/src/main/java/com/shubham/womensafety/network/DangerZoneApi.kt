package com.shubham.womensafety.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DangerZoneApi {
    @GET("api/v1/danger-zones")
    suspend fun getDangerZones(): Response<List<DangerZoneResponse>>

    /**
     * Requests route polylines from the backend, which proxies the Directions API.
     * The Directions API key is kept on the server; the app never sees it.
     */
    @GET("api/v1/directions")
    suspend fun getDirections(
        @Query("origin_lat") originLat: Double,
        @Query("origin_lng") originLng: Double,
        @Query("dest_lat") destLat: Double,
        @Query("dest_lng") destLng: Double,
    ): Response<List<DirectionsRouteResponse>>
}
