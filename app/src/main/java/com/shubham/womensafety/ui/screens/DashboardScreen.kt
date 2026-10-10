package com.shubham.womensafety.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.shubham.womensafety.FirebaseAuth.LoginViewModel
import com.shubham.womensafety.R
import com.shubham.womensafety.database.Guardian
import com.shubham.womensafety.database.GuardianDatabase
import com.shubham.womensafety.ui.components.DashboardActionCard
import com.shubham.womensafety.ui.theme.ColorAccent
import com.shubham.womensafety.ui.theme.DashboardBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.compose.runtime.rememberCoroutineScope

private const val TAG = "DashboardScreen"
private const val SHAKE_G_FORCE_THRESHOLD = 2.5
private const val REQUIRED_SHAKES = 3
private const val SHAKE_WINDOW_MS = 1500L
private const val SHAKE_SLOP_TIME_MS = 200L
private const val SHAKE_COOLDOWN_MS = 10_000L

private data class EmergencyResult(
    val mailList: String,
    val messageBody: String,
    val firstGuardianPhone: String?
)

@Composable
fun DashboardScreen(
    onNavigateToGuardianInfo: () -> Unit,
    onNavigateToMaps: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val authState by viewModel.authenticationState.observeAsState()

    var currentLatitude by remember { mutableStateOf("") }
    var currentLongitude by remember { mutableStateOf("") }
    var pendingCallPhoneNumber by remember { mutableStateOf<String?>(null) }
    var signInLaunched by remember { mutableStateOf(false) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val sensorManager = remember {
        context.getSystemService(SensorManager::class.java)
    }
    val accelerometer = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    val shakeState = remember {
        object {
            var shakeCount = 0
            var firstShakeTimestampMs = 0L
            var lastShakeTimestampMs = 0L
            var cooldownUntilMs = 0L
        }
    }

    suspend fun ensureLocationFetched(): Boolean {
        if (currentLatitude.isNotBlank() && currentLongitude.isNotBlank()) return true
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return suspendCoroutine { continuation ->
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLatitude = location.latitude.toString()
                    currentLongitude = location.longitude.toString()
                    continuation.resume(true)
                } else {
                    continuation.resume(false)
                }
            }.addOnFailureListener {
                continuation.resume(false)
            }
        }
    }

    fun getLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLatitude = location.latitude.toString()
                currentLongitude = location.longitude.toString()
            }
        }
    }

    fun resetShakeState() {
        shakeState.shakeCount = 0
        shakeState.firstShakeTimestampMs = 0L
        shakeState.lastShakeTimestampMs = 0L
    }

    fun showShakeProgress() {
        val progressMessage = "Shake detected (${shakeState.shakeCount}/$REQUIRED_SHAKES)"
        Log.d(TAG, progressMessage)
        Toast.makeText(context, progressMessage, Toast.LENGTH_SHORT).show()
    }

    fun emergencyFun(): EmergencyResult {
        val db = GuardianDatabase.getInstance(context)
        val guardianList: List<Guardian> = db.guardianDatabaseDao().getEmail()
        var mailList = ""
        val text = context.getString(R.string.problem)
        val text1 = text.plus(" http://maps.google.com/?q=$currentLatitude,$currentLongitude")

        if (guardianList.isNotEmpty()) {
            mailList = guardianList.joinToString(separator = ",") { it.guardianEmail }
            val smsManager = context.getSystemService(SmsManager::class.java)
            guardianList.forEach {
                smsManager.sendTextMessage(it.guardianPhoneNo, null, text1, null, null)
            }
        }

        return EmergencyResult(
            mailList = mailList,
            messageBody = text1,
            firstGuardianPhone = guardianList.firstOrNull()?.guardianPhoneNo
        )
    }

    fun triggerEmergencyEmail(mailList: String, messageBody: String) {
        val subject = "From Women Safety App"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(mailList))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, messageBody)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Send mail using.."))
    }

    fun placeAutoCall(phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(callIntent)
    }

    val requestCallPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val phoneNumber = pendingCallPhoneNumber
        pendingCallPhoneNumber = null
        if (isGranted && !phoneNumber.isNullOrBlank()) {
            placeAutoCall(phoneNumber)
        } else {
            Toast.makeText(context, "Call permission denied. SOS SMS sent only.", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun runEmergencyFlowInternal() {
        val emergencyResult = withContext(Dispatchers.IO) {
            emergencyFun()
        }
        triggerEmergencyEmail(emergencyResult.mailList, emergencyResult.messageBody)
        val firstPhone = emergencyResult.firstGuardianPhone
        if (!firstPhone.isNullOrBlank()) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                placeAutoCall(firstPhone)
            } else {
                pendingCallPhoneNumber = firstPhone
                requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                runEmergencyFlowInternal()
            }
        } else {
            Toast.makeText(context, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { result ->
        val response = result.idpResponse
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            signInLaunched = false
        } else {
            Log.i(TAG, "Sign in unsuccessful ${response?.error?.errorCode}")
        }
    }

    fun triggerEmergency() {
        coroutineScope.launch {
            val hasLocation = ensureLocationFetched()
            if (!hasLocation || currentLongitude.isBlank()) {
                Toast.makeText(
                    context,
                    "Click on Location button and try again",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            } else {
                runEmergencyFlowInternal()
            }
        }
    }

    val shakeListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs < shakeState.cooldownUntilMs) return

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH

                if (gForce <= SHAKE_G_FORCE_THRESHOLD) return
                if (nowMs - shakeState.lastShakeTimestampMs < SHAKE_SLOP_TIME_MS) return

                shakeState.lastShakeTimestampMs = nowMs
                if (shakeState.shakeCount == 0) {
                    shakeState.shakeCount = 1
                    shakeState.firstShakeTimestampMs = nowMs
                    showShakeProgress()
                    return
                }

                if (nowMs - shakeState.firstShakeTimestampMs <= SHAKE_WINDOW_MS) {
                    shakeState.shakeCount++
                } else {
                    shakeState.shakeCount = 1
                    shakeState.firstShakeTimestampMs = nowMs
                }

                showShakeProgress()

                if (shakeState.shakeCount >= REQUIRED_SHAKES) {
                    Log.i(TAG, "SOS triggered from shake sequence")
                    Toast.makeText(context, "SOS triggered", Toast.LENGTH_SHORT).show()
                    triggerEmergency()
                    resetShakeState()
                    shakeState.cooldownUntilMs = nowMs + SHAKE_COOLDOWN_MS
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    }

    DisposableEffect(lifecycleOwner, accelerometer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    accelerometer?.let {
                        sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_UI)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.unregisterListener(shakeListener)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            accelerometer?.let {
                sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(shakeListener)
        }
    }

    LaunchedEffect(Unit) {
        getLocation()
    }

    LaunchedEffect(authState) {
        when (authState) {
            LoginViewModel.AuthenticationState.UNAUTHENTICATED -> {
                if (!signInLaunched) {
                    signInLaunched = true
                    val providers = arrayListOf(
                        AuthUI.IdpConfig.EmailBuilder().build(),
                        AuthUI.IdpConfig.GoogleBuilder().build()
                    )
                    val signInIntent = AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .setTheme(R.style.LoginTheme_NoActionBar)
                        .setLogo(R.drawable.women)
                        .build()
                    signInLauncher.launch(signInIntent)
                }
            }
            LoginViewModel.AuthenticationState.AUTHENTICATED -> {
                signInLaunched = false
            }
            null -> Unit
        }
    }

    val welcomeText = when (authState) {
        LoginViewModel.AuthenticationState.AUTHENTICATED ->
            "Welcome,${FirebaseAuth.getInstance().currentUser?.displayName}"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (welcomeText.isNotEmpty()) {
            Text(
                text = welcomeText,
                color = ColorAccent,
                fontSize = 22.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 10.dp, top = 7.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DashboardActionCard(
                imageRes = R.drawable.guardian,
                label = stringResource(R.string.guardian_details),
                onClick = onNavigateToGuardianInfo
            )

            Spacer(modifier = Modifier.height(15.dp))

            DashboardActionCard(
                imageRes = R.drawable.checkloc,
                label = "Safe Navigation",
                onClick = onNavigateToMaps
            )

            Spacer(modifier = Modifier.height(35.dp))

            Button(
                onClick = { triggerEmergency() },
                modifier = Modifier.size(135.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ColorAccent),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.emergency),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
