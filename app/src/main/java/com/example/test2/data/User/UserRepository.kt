package com.example.test2.data.User

import android.content.Context
import android.util.Log
import com.example.test2.data.AppDatabase
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.Meal
import com.example.test2.network.ApiService
import com.example.test2.network.LoginRequest
import com.example.test2.network.UserCreate
import com.example.test2.network.toApiModel
import com.example.test2.network.toLocalDailyMeal
import com.example.test2.network.toLocalMeal
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import com.example.test2.utils.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.mindrot.jbcrypt.BCrypt
import retrofit2.HttpException
import java.util.Date

class UserRepository(
    private val database: AppDatabase,
    val api: ApiService,
    private val context: Context,
) {
    val Userdao = database.userDao()
    private val MealDao = database.mealDao()
    private val ProductDao = database.productDao()
    private val DailyMealDao = database.dailyMealDao()

    fun getAllUsers(): Flow<List<User>> = Userdao.getAllUsers()

    suspend fun register(username: String, password: String): Result<String> {
        return try {
            val networkUser = api.register(UserCreate(username, password))
            val hashedPassword = hashPassword(password)
            val localUser = User(
                id = networkUser.id,
                username = networkUser.username,
                hashedPassword = hashedPassword,
                lastModified = networkUser.lastModified
            )
            Userdao.insert(localUser)

            val loginResponse = api.login(LoginRequest(username = username, password = password))
            TokenManager.saveToken(context, loginResponse.access_token, username)

            fullSync(loginResponse.user_id.toLong())

            Result.success("Registration successful")
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> Result.failure(Exception("Username already exists"))
                422 -> Result.failure(Exception("Invalid data"))
                else -> Result.failure(Exception("Registration failed: ${e.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("No internet or server error"))
        }
    }

    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val localUser = Userdao.getUserByUsername(username)
            if (localUser != null && verifyPassword(password, localUser.hashedPassword)) {
                TokenManager.saveToken(context, TokenManager.getToken(context) ?: "", username)
                Result.success("Login successful (offline)")
            } else {
                val loginResponse = api.login(LoginRequest(username = username, password = password))
                TokenManager.saveToken(context, loginResponse.access_token, username)

                fullSync(loginResponse.user_id.toLong())

                val hashedPassword = hashPassword(password)
                Userdao.insert(User(
                    id = loginResponse.user_id.toInt(),
                    username = username,
                    hashedPassword = hashedPassword,
                    lastModified = System.currentTimeMillis()
                ))

                Result.success("Login successful")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> Result.failure(Exception("Incorrect username or password"))
                else -> Result.failure(Exception("Login failed: ${e.message()}"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Login error: ${e.message}", e)
            val localUser = Userdao.getUserByUsername(username)
            if (localUser != null && verifyPassword(password, localUser.hashedPassword)) {
                TokenManager.saveToken(context, TokenManager.getToken(context) ?: "", username)
                Result.success("Login successful (offline)")
            } else {
                Result.failure(Exception("No internet and user not found locally"))
            }
        }
    }

    fun autoLogin(): Boolean {
        val loggedIn = TokenManager.isLoggedIn(context)
        Log.d("UserRepository", "autoLogin: $loggedIn")
        return loggedIn
    }

    fun getMealsByUser(userId: Long): Flow<List<Meal>> = MealDao.getMealsByUser(userId)

    suspend fun insertMeal(meal: Meal) {
        val localId = MealDao.insertMeal(meal.copy(server_id = null))
        try {
            val serverMeal = api.postMeal(meal.toApiModel())
            MealDao.updateServerId(localId, serverMeal.id)
            Log.d("UserRepository", "Immediate push Meal success, server_id = ${serverMeal.id}")
        } catch (e: Exception) {
            Log.w("UserRepository", "Immediate push Meal failed: ${e.message}. Will sync later.")
        }
    }

    fun getAllProducts(): Flow<List<Product>> = ProductDao.getAllProducts()

    fun getTodayDailyMealsByUser(userId: Long): Flow<List<DailyMeal>> = DailyMealDao.getTodayDailyMealsByUser(userId)

    suspend fun insertDailyMeal(dailyMeal: DailyMeal) {
        val localId = DailyMealDao.insertDailyMeal(dailyMeal.copy(server_id = null))
        try {
            val serverDaily = api.postDailyMeal(dailyMeal.toApiModel())
            DailyMealDao.updateServerId(localId, serverDaily.id)
            Log.d("UserRepository", "Immediate push DailyMeal success, server_id = ${serverDaily.id}")
        } catch (e: Exception) {
            Log.w("UserRepository", "Immediate push DailyMeal failed: ${e.message}. Will sync later.")
        }
    }

    suspend fun getDailyMealsByPeriod(userId: Long, startDate: Date, endDate: Date): List<DailyMeal> {
        val startTimestamp = startDate.time
        val endTimestamp = endDate.time
        return DailyMealDao.getDailyMealsByPeriod(userId, startTimestamp, endTimestamp)
    }

    suspend fun syncMeals(userId: Long) {
        try {
            // PUSH unsynced
            val unsyncedMeals = MealDao.getMealsByUser(userId).first().filter { it.server_id == null }
            unsyncedMeals.forEach { meal ->
                meal.id?.let { localId ->
                    try {
                        val serverResponse = api.postMeal(meal.toApiModel())
                        MealDao.updateServerId(localId, serverResponse.id)
                        Log.d("UserRepository", "Pushed unsynced Meal (local_id=$localId)")
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Push Meal failed: ${e.message}")
                    }
                }
            }

            // PULL from server
            val serverMeals = api.getMeals()
            MealDao.clearMealsByUser(userId)
            serverMeals.forEach { serverMeal ->
                val localMeal = serverMeal.toLocalMeal(userId).copy(server_id = serverMeal.id)
                MealDao.insertMeal(localMeal)
            }
            Log.d("UserRepository", "syncMeals completed: pulled ${serverMeals.size}, pushed ${unsyncedMeals.size}")
        } catch (e: Exception) {
            Log.e("UserRepository", "syncMeals error: ${e.message}", e)
        }
    }

    suspend fun syncDailyMeals(userId: Long) {
        try {
            // PUSH unsynced
            val unsyncedDaily = DailyMealDao.getDailyMealsByUser(userId).first().filter { it.server_id == null }
            unsyncedDaily.forEach { dailyMeal ->
                dailyMeal.id?.let { localId ->
                    try {
                        val serverResponse = api.postDailyMeal(dailyMeal.toApiModel())
                        DailyMealDao.updateServerId(localId, serverResponse.id)
                        Log.d("UserRepository", "Pushed unsynced DailyMeal (local_id=$localId)")
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Push DailyMeal failed: ${e.message}")
                    }
                }
            }

            // PULL from server
            val serverDailyMeals = api.getDailyMeals()
            DailyMealDao.clearDailyMealsByUser(userId)
            serverDailyMeals.forEach { serverDaily ->
                val localDaily = serverDaily.toLocalDailyMeal(userId).copy(server_id = serverDaily.id)
                DailyMealDao.insertDailyMeal(localDaily)
            }
            Log.d("UserRepository", "syncDailyMeals completed: pulled ${serverDailyMeals.size}, pushed ${unsyncedDaily.size}")
        } catch (e: Exception) {
            Log.e("UserRepository", "syncDailyMeals error: ${e.message}", e)
        }
    }

    suspend fun fullSync(userId: Long) {
        syncMeals(userId)
        syncDailyMeals(userId)
    }

    suspend fun logout() {
        TokenManager.clearToken(context)
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("current_user_id").apply()
        Userdao.clearAll()
    }

    private fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

    private fun verifyPassword(password: String, hashedPassword: String): Boolean = BCrypt.checkpw(password, hashedPassword)
}