package com.example.test2.network

import android.content.Context
import com.example.test2.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    // ТОЛЬКО ваш бэкенд URL - Nutritionix удалён
    private const val YOUR_API_BASE_URL = "http://10.0.2.2:8000/"

    // ===== ВАШ БЭКЕНД API =====
    fun provideMyApiService(context: Context): ApiService {
        return provideMyApiRetrofit(context).create(ApiService::class.java)
    }

    private fun provideMyApiRetrofit(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(YOUR_API_BASE_URL)
            .client(provideMyApiOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun provideMyApiOkHttpClient(context: Context): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = TokenManager.getToken(context)
            val requestBuilder = original.newBuilder()
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}