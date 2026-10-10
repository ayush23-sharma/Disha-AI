package com.shubham.womensafety.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.shubham.womensafety.R
import com.shubham.womensafety.ui.components.SafetyReportCard
import com.shubham.womensafety.utils.PermissionUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun MapsScreen(
    mapType: MapType,
    viewModel: MapsViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(PermissionUtils.isAccessFineLocationGranted(context))
    }
    var currentUserLatLng by remember { mutableStateOf<LatLng?>(null) }
    val heatmapAdded = remember { mutableStateOf(false) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val cameraPositionState = rememberCameraPositionState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!hasLocationPermission) {
            Toast.makeText(context, context.getString(R.string.location_permission_not_granted), Toast.LENGTH_LONG).show()
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { _ -> }

    fun enableLocationSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(context)
        client.checkLocationSettings(builder.build())
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                        locationSettingsLauncher.launch(intentSenderRequest)
                    } catch (_: IntentSender.SendIntentException) {
                        // Ignore
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    fun setupLocationListener() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20000)
            .setMinUpdateIntervalMillis(20000)
            .build()

        if (!hasLocationPermission) return

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    currentUserLatLng = currentLatLng
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
                        )
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    suspend fun getCurrentUserLocation(): LatLng? = suspendCancellableCoroutine { continuation ->
        if (!PermissionUtils.isAccessFineLocationGranted(context)) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(LatLng(location.latitude, location.longitude))
                } else {
                    val singleUpdateRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                        .setMaxUpdates(1)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            fusedLocationClient.removeLocationUpdates(this)
                            val freshLocation = locationResult.lastLocation
                            continuation.resume(
                                freshLocation?.let { LatLng(it.latitude, it.longitude) }
                            )
                        }
                    }

                    fusedLocationClient.requestLocationUpdates(
                        singleUpdateRequest,
                        callback,
                        Looper.getMainLooper()
                    )
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    LaunchedEffect(Unit) {
        viewModel.loadDangerZones()
    }

    LaunchedEffect(viewModel.showDangerZonesUnavailable) {
        if (viewModel.showDangerZonesUnavailable) {
            Toast.makeText(context, "Danger zones unavailable from backend.", Toast.LENGTH_SHORT).show()
            viewModel.consumeDangerZonesUnavailableToast()
        }
    }

    LaunchedEffect(viewModel.routeToastMessage) {
        viewModel.routeToastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeRouteToast()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            if (!PermissionUtils.isLocationEnabled(context)) {
                enableLocationSettings()
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = true),
            onMapClick = { tappedLatLng ->
                viewModel.onDestinationSelected(tappedLatLng)
                coroutineScope.launch {
                    val currentLocation = getCurrentUserLocation()
                    if (currentLocation == null) {
                        Toast.makeText(
                            context,
                            "Current location unavailable. Please wait and retry.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    viewModel.fetchRoutes(currentLocation, tappedLatLng)
                }
            }
        ) {
            MapsViewModel.SAFE_HAVENS.forEach { safeHaven ->
                Marker(
                    state = MarkerState(position = safeHaven),
                    title = "Safe Haven",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                )
            }

            viewModel.destinationLatLng?.let { dest ->
                Marker(
                    state = MarkerState(position = dest),
                    title = "Destination"
                )
            }

            currentUserLatLng?.let { userLoc ->
                Marker(
                    state = MarkerState(position = userLoc),
                    title = "You"
                )
            }

            viewModel.routePolylines.forEach { route ->
                com.google.maps.android.compose.Polyline(
                    points = route.path,
                    color = androidx.compose.ui.graphics.Color(
                        if (route.isSafest) Color.GREEN else Color.DKGRAY
                    ),
                    width = if (route.isSafest) 15f else 10f,
                    zIndex = if (route.isSafest) 10f else 4f
                )
            }

            MapEffect(viewModel.dynamicDangerZones, hasLocationPermission) { map ->
                if (hasLocationPermission &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    setupLocationListener()
                }

                if (viewModel.dynamicDangerZones.isNotEmpty() && !heatmapAdded.value) {
                    val redHotGradient = Gradient(
                        intArrayOf(
                            Color.argb(0, 255, 0, 0),
                            Color.RED
                        ),
                        floatArrayOf(0.2f, 1f)
                    )

                    val heatmapProvider = HeatmapTileProvider.Builder()
                        .data(viewModel.dynamicDangerZones)
                        .gradient(redHotGradient)
                        .radius(50)
                        .build()

                    map.addTileOverlay(
                        com.google.android.gms.maps.model.TileOverlayOptions()
                            .tileProvider(heatmapProvider)
                    )
                    heatmapAdded.value = true
                }
            }
        }

        viewModel.safetyReportText?.let { reportText ->
            SafetyReportCard(
                reportText = reportText,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
    }
}
