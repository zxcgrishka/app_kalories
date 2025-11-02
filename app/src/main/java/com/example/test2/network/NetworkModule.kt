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
    // Базовые URL
    private const val NUTRITIONIX_BASE_URL = "https://trackapi.nutritionix.com/"
    private const val YOUR_API_BASE_URL = "http://10.0.2.2:8000/" // ваш бэкенд

    // Nutritionix ключи
    const val NUTRITIONIX_APP_ID = "fbc0468d"
    const val NUTRITIONIX_APP_KEY = "5d1c75eb69b673a3c85f2662b22005f4"

    // ===== NUTRITIONIX API =====
    fun provideNutritionixService(): NutritionixService {
        return provideNutritionixRetrofit().create(NutritionixService::class.java)
    }

    private fun provideNutritionixRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NUTRITIONIX_BASE_URL)
            .client(provideNutritionixOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun provideNutritionixOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(NutritionixAuthInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Интерцептор для Nutritionix авторизации
    class NutritionixAuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val original = chain.request()
            val request = original.newBuilder()
                .header("x-app-id", NUTRITIONIX_APP_ID)
                .header("x-app-key", NUTRITIONIX_APP_KEY)
                .header("Content-Type", "application/json")
                .method(original.method, original.body)
                .build()
            return chain.proceed(request)
        }
    }

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