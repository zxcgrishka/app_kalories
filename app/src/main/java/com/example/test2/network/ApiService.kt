package com.example.test2.network

import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("register")
    suspend fun register(@Body user: UserCreate): User

    @POST("login")
    suspend fun login(@Body loginRequest: LoginRequest): TokenWithId

    @GET("meals")
    suspend fun getMeals(): List<MealApi>

    @POST("meals")
    suspend fun postMeal(@Body meal: MealApi): MealApi

    // DailyMeal
    @GET("daily-meals")
    suspend fun getDailyMeals(): List<DailyMealApi>

    @POST("daily-meals")
    suspend fun postDailyMeal(@Body dailyMeal: DailyMealApi): DailyMealApi

    // Custom Products
    @GET("custom-products")
    suspend fun getCustomProducts(): List<CustomProductApi>

    @POST("custom-products")
    suspend fun postCustomProduct(@Body product: CustomProductApi): CustomProductApi
}