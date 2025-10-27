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
    private const val NUTRITIONIX_BASE_URL = "https://trackapi.nutritionix.com/"

    // мои айди и ключ
    const val NUTRITIONIX_APP_ID = "fbc0468d"
    const val NUTRITIONIX_APP_KEY = "5d1c75eb69b673a3c85f2662b22005f4"

    fun provideApiService(): ApiService {
        return provideRetrofit().create(ApiService::class.java)
    }
    private fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NUTRITIONIX_BASE_URL)
            .client(provideOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun provideNutritionixService(): NutritionixService {
        return provideRetrofit().create(NutritionixService::class.java)
    }
}
fun provideApiService(context: Context): ApiService {
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

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(ApiService::class.java)
}
