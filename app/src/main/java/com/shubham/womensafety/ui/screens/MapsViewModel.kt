package com.shubham.womensafety.ui.screens

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.shubham.womensafety.repository.SafetyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RoutePolylineUi(
    val path: List<LatLng>,
    val isSafest: Boolean
)

class MapsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val SAFE_HAVENS = listOf(
            LatLng(28.4257, 77.3549),
            LatLng(28.3981, 77.3107),
            LatLng(28.4104, 77.3178),
            LatLng(28.3930, 77.3115),
            LatLng(28.4366, 77.3090),
            LatLng(28.4629, 77.3024)
        )
        private const val MAPS_API_KEY_META_DATA = "com.google.android.geo.API_KEY"
    }

    private val safetyRepository = SafetyRepository()
    private val directionsHttpClient = OkHttpClient()

    var dynamicDangerZones by mutableStateOf<List<LatLng>>(emptyList())
        private set

    var destinationLatLng by mutableStateOf<LatLng?>(null)
        private set

    var routePolylines by mutableStateOf<List<RoutePolylineUi>>(emptyList())
        private set

    var safetyReportText by mutableStateOf<String?>(null)
        private set

    var showDangerZonesUnavailable by mutableStateOf(false)
        private set

    var routeToastMessage by mutableStateOf<String?>(null)
        private set

    fun consumeRouteToast() {
        routeToastMessage = null
    }

    fun loadDangerZones() {
        viewModelScope.launch {
            dynamicDangerZones = safetyRepository.fetchDynamicDangerZones()
            if (dynamicDangerZones.isEmpty()) {
                showDangerZonesUnavailable = true
            }
        }
    }

    fun consumeDangerZonesUnavailableToast() {
        showDangerZonesUnavailable = false
    }

    fun onDestinationSelected(latLng: LatLng) {
        destinationLatLng = latLng
        routePolylines = emptyList()
        safetyReportText = null
    }

    fun fetchRoutes(origin: LatLng, dest: LatLng) {
        viewModelScope.launch {
            try {
                val apiKey = getGoogleMapsApiKey()
                if (apiKey.isBlank()) {
                    routeToastMessage = "Google Maps API key missing"
                    return@launch
                }

                val requestUrl = buildString {
                    append("https://maps.googleapis.com/maps/api/directions/json?")
                    append("origin=${origin.latitude},${origin.longitude}")
                    append("&destination=${dest.latitude},${dest.longitude}")
                    append("&mode=driving")
                    append("&alternatives=true")
                    append("&key=$apiKey")
                }

                val response = withContext(Dispatchers.IO) {
                    directionsHttpClient.newCall(
                        Request.Builder().url(requestUrl).build()
                    ).execute().use { httpResponse ->
                        if (!httpResponse.isSuccessful) {
                            val errorBody = httpResponse.body?.string().orEmpty()
                            Log.e("RouteError", "Directions HTTP failure code=${httpResponse.code}, url=$requestUrl, body=$errorBody")
                            throw IllegalStateException("Directions API HTTP ${httpResponse.code}. Body: $errorBody")
                        }
                        httpResponse.body?.string().orEmpty()
                    }
                }

                val directionsJson = JSONObject(response)
                val directionsStatus = directionsJson.optString("status")
                if (directionsStatus != "OK") {
                    val directionsError = directionsJson.optString("error_message")
                    Log.e("RouteError", "Directions status=$directionsStatus, error=$directionsError, url=$requestUrl, raw=$response")
                    routeToastMessage = "Route service status: $directionsStatus"
                    return@launch
                }

                val routesArray = directionsJson.optJSONArray("routes") ?: run {
                    Log.e("RouteError", "Directions response missing routes array. raw=$response")
                    return@launch
                }

                val scoredRoutes = mutableListOf<ScoredRoute>()
                for (index in 0 until routesArray.length()) {
                    val route = routesArray.getJSONObject(index)
                    val encodedPolyline = route.getJSONObject("overview_polyline").getString("points")
                    val decodedPath = PolyUtil.decode(encodedPolyline)
                    var score = 0

                    dynamicDangerZones.forEach { dangerPoint ->
                        if (PolyUtil.isLocationOnPath(dangerPoint, decodedPath, false, 500.0)) {
                            score -= 20
                        }
                    }

                    SAFE_HAVENS.forEach { safeHavenPoint ->
                        if (PolyUtil.isLocationOnPath(safeHavenPoint, decodedPath, false, 500.0)) {
                            score += 10
                        }
                    }

                    scoredRoutes.add(ScoredRoute(decodedPath, score))
                }

                if (scoredRoutes.isEmpty()) return@launch

                val safestRouteIndex = scoredRoutes.indices.maxByOrNull { scoredRoutes[it].score } ?: return@launch
                val safestRoute = scoredRoutes[safestRouteIndex]

                val scoreBuilder = StringBuilder()
                scoreBuilder.append("Optimal Route Score: ${safestRoute.score}")
                var alternateCount = 1
                scoredRoutes.forEachIndexed { index, route ->
                    if (index != safestRouteIndex) {
                        scoreBuilder.append("\nAlternate $alternateCount: ${route.score}")
                        alternateCount++
                    }
                }

                routePolylines = scoredRoutes.mapIndexed { index, route ->
                    RoutePolylineUi(path = route.path, isSafest = index == safestRouteIndex)
                }
                safetyReportText = scoreBuilder.toString()

                routeToastMessage = if (scoredRoutes.size == 1) {
                    "Only 1 viable route found."
                } else {
                    "Safe Route Calculated from ${scoredRoutes.size} options."
                }
            } catch (exception: Exception) {
                Log.e("RouteError", "Failed to fetch or render routes: ${exception.message}", exception)
                routeToastMessage = "Unable to load safe routes"
            }
        }
    }

    private data class ScoredRoute(
        val path: List<LatLng>,
        val score: Int
    )

    private fun getGoogleMapsApiKey(): String {
        val context = getApplication<Application>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val metadata = appInfo.metaData ?: return ""
        val directValue = metadata.getString(MAPS_API_KEY_META_DATA).orEmpty()
        if (directValue.isNotBlank() && !directValue.startsWith("@")) return directValue

        val resourceId = metadata.getInt(MAPS_API_KEY_META_DATA)
        if (resourceId != 0) return context.getString(resourceId)

        if (directValue.startsWith("@")) {
            val resourceName = directValue.removePrefix("@string/")
            val resolvedId = context.resources.getIdentifier(resourceName, "string", context.packageName)
            if (resolvedId != 0) return context.getString(resolvedId)
        }

        return ""
    }
}
