package com.shubham.womensafety.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.shubham.womensafety.network.RetrofitClient
import com.shubham.womensafety.repository.SafetyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    }

    private val safetyRepository = SafetyRepository()

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
                val routeResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.dangerZoneApi.getDirections(
                        originLat = origin.latitude,
                        originLng = origin.longitude,
                        destLat = dest.latitude,
                        destLng = dest.longitude,
                    )
                }

                if (!routeResponse.isSuccessful) {
                    Log.e("RouteError", "Directions backend error: HTTP ${routeResponse.code()}")
                    routeToastMessage = "Route service unavailable (${routeResponse.code()})"
                    return@launch
                }

                val routes = routeResponse.body().orEmpty()
                if (routes.isEmpty()) {
                    routeToastMessage = "No routes returned by service"
                    return@launch
                }

                val scoredRoutes = mutableListOf<ScoredRoute>()
                for (route in routes) {
                    val decodedPath = PolyUtil.decode(route.overviewPolyline)
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
}
