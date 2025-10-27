package com.example.test2.network

import android.content.Context
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import java.io.InputStream

class NutritionixRepository(private val service: NutritionixService) {

    suspend fun recognizeFoodFromImage(
        imageUri: Uri,
        context: Context
    ): Result<List<NutritionixFood>> {
        return try {
            val imagePart = createImagePart(imageUri, context)

            val response: Response<NutritionixImageResponse> =
                service.recognizeFoodFromImage(
                    image = imagePart,
                    appId = NetworkModule.NUTRITIONIX_APP_ID,
                    appKey = NetworkModule.NUTRITIONIX_APP_KEY
                )

            if (response.isSuccessful) {
                val foods = response.body()?.foods ?: emptyList()
                Result.success(foods)
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createImagePart(uri: Uri, context: Context): MultipartBody.Part {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: byteArrayOf()

        val requestFile = RequestBody.create(
            "image/jpeg".toMediaType(), // ← ИСПРАВЛЕНО ЗДЕСЬ
            bytes
        )

        return MultipartBody.Part.createFormData(
            "image",
            "food_image.jpg",
            requestFile
        )
    }

    suspend fun recognizeFoodFromText(query: String): Result<List<NutritionixFood>> {
        return try {
            val response: Response<NutritionixTextResponse> =
                service.recognizeFoodFromText(
                    NutritionixTextRequest(query),
                    NetworkModule.NUTRITIONIX_APP_ID,
                    NetworkModule.NUTRITIONIX_APP_KEY
                )

            if (response.isSuccessful) {
                val foods = response.body()?.foods ?: emptyList()
                Result.success(foods)
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}