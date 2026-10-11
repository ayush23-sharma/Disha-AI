package com.shubham.womensafety.network

import com.google.gson.annotations.SerializedName

/**
 * Matches the RoutePolyline response from the backend /api/v1/directions endpoint.
 * The backend returns only the encoded overview polyline — the Directions API key
 * never leaves the server.
 */
data class DirectionsRouteResponse(
    @SerializedName("overview_polyline") val overviewPolyline: String
)
