package com.shubham.womensafety.repository

import com.google.android.gms.maps.model.LatLng
import com.shubham.womensafety.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafetyRepository {
    suspend fun fetchDynamicDangerZones(): List<LatLng> = withContext(Dispatchers.IO) {
        runCatching {
            val response = RetrofitClient.dangerZoneApi.getDangerZones()
            if (!response.isSuccessful) {
                return@runCatching emptyList()
            }

            response.body()
                .orEmpty()
                .map { zone -> LatLng(zone.latitude, zone.longitude) }
        }.getOrDefault(emptyList())
    }
}
