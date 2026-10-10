package com.shubham.womensafety.network

import com.shubham.womensafety.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Base URL is read from local.properties at build time via BuildConfig.
// To configure: add BACKEND_BASE_URL=<your url> to local.properties (see local.properties.example).
const val DANGER_ZONE_BASE_URL = BuildConfig.BACKEND_BASE_URL

object RetrofitClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(DANGER_ZONE_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val dangerZoneApi: DangerZoneApi = retrofit.create(DangerZoneApi::class.java)
}
