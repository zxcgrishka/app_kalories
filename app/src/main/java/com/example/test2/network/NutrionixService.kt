package com.example.test2.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface NutritionixService {

    // Для распознавания по фото
    @Multipart
    @POST("v2/natural/nutrients")
    suspend fun recognizeFoodFromImage(
        @Part image: MultipartBody.Part,
        @Header("x-app-id") appId: String,
        @Header("x-app-key") appKey: String,
        @Header("x-remote-user-id") userId: String = "0"
    ): Response<NutritionixImageResponse>

    // Для текстового поиска (пока по фото не получается ну или  пусть будет)
    @Headers("Content-Type: application/json")
    @POST("v2/natural/nutrients")
    suspend fun recognizeFoodFromText(
        @Body request: NutritionixTextRequest,
        @Header("x-app-id") appId: String,
        @Header("x-app-key") appKey: String
    ): Response<NutritionixTextResponse>
}