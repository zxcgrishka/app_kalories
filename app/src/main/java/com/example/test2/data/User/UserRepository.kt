package com.example.test2.data.User

import android.content.Context
import android.util.Log
import com.example.test2.data.AppDatabase
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.Meal
import com.example.test2.network.ApiService
import com.example.test2.network.UserCreate
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import com.example.test2.utils.TokenManager
import kotlinx.coroutines.flow.Flow
import org.mindrot.jbcrypt.BCrypt
import retrofit2.HttpException
import java.util.Date
import com.example.test2.network.LoginRequest
import com.example.test2.network.toApiModel
import com.example.test2.network.toLocalDailyMeal
import com.example.test2.network.toLocalMeal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull


class UserRepository(
    private val database: AppDatabase,
    private val api: ApiService,
    private val context: Context,
) {
    private val Userdao = database.userDao()
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
            Log.d("UserRepository", "Token saved in register for $username")

            fullSync(networkUser.id.toLong())

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
            val user = Userdao.getUserByUsername(username)
            if (user != null && verifyPassword(password, user.hashedPassword)) {
                TokenManager.saveToken(context, TokenManager.getToken(context) ?: "", username)
                Result.success("Login successful (offline)")
            } else {
                val loginResponse = api.login(
                    LoginRequest(
                        username = username,
                        password = password
                    )
                )
                TokenManager.saveToken(context, loginResponse.access_token, username)
                Log.d("UserRepository", "Token saved in login for $username")

                val currentUserId = loginResponse.user_id
                fullSync(currentUserId)

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
            val user = Userdao.getUserByUsername(username)
            if (user != null && verifyPassword(password, user.hashedPassword)) {
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
        } catch (e: Exception) {
        }
    }

    fun getAllProducts(): Flow<List<Product>> = ProductDao.getAllProducts()
    suspend fun logout() {
        TokenManager.clearToken(context)
        Userdao.clearAll()
    }

    fun getTodayDailyMealsByUser(userId: Long): Flow<List<DailyMeal>> = DailyMealDao.getTodayDailyMealsByUser(userId)

    suspend fun insertDailyMeal(dailyMeal: DailyMeal) {
        val localId = DailyMealDao.insertDailyMeal(dailyMeal.copy(server_id = null))
        try {
            val serverDaily = api.postDailyMeal(dailyMeal.toApiModel())
            DailyMealDao.updateServerId(localId, serverDaily.id)
            Log.d("UserRepository", "DailyMeal pushed immediately")
        } catch (e: Exception) {
            Log.w("UserRepository", "Immediate push failed, will sync later")
        }
    }


    suspend fun clearDailyMealsByUser(userId: Long) {
        DailyMealDao.clearDailyMealsByUser(userId)
    }

    suspend fun getDailyMealsByPeriod(userId: Long, startDate: Date, endDate: Date): List<DailyMeal> {
        val startTimestamp = startDate.time
        val endTimestamp = endDate.time
        return database.dailyMealDao().getDailyMealsByPeriod(userId, startTimestamp, endTimestamp)
    }

    suspend fun syncMeals(userId: Long) {
        try {// --- ШАГ 1: PUSH (Отправка локальных изменений на сервер) ---
            // Сначала находим все записи, которых еще нет на сервере.
            val unsyncedMeals = MealDao.getMealsByUser(userId).firstOrNull()?.filter { it.server_id == null }

            if (!unsyncedMeals.isNullOrEmpty()) {
                Log.d("UserRepository", "Found ${unsyncedMeals.size} unsynced meals to push.")
                unsyncedMeals.forEach { meal ->
                    // Убеждаемся, что у локальной записи есть id.
                    meal.id?.let { localId ->
                        try {
                            val serverResponse = api.postMeal(meal.toApiModel())
                            // Обновляем server_id у локальной записи после успешной отправки.
                            MealDao.updateServerId(localId, serverResponse.id)
                            Log.d("UserRepository", "Pushed unsynced Meal (local_id=$localId), new server_id = ${serverResponse.id}")
                        } catch (e: Exception) {
                            Log.e("UserRepository", "Failed to push Meal (id=${localId}): ${e.message}")
                        }
                    } ?: run {
                        Log.e("UserRepository", "Cannot push Meal because its local id is null.")
                    }
                }
            } else {
                Log.d("UserRepository", "No unsynced meals to push.")
            }

            // --- ШАГ 2: PULL (Загрузка актуальных данных с сервера) ---
            Log.d("UserRepository", "Starting pull for meals...")
            val serverMeals = api.getMeals()
            Log.d("UserRepository", "Pulled ${serverMeals.size} meals from server.")

            // Полностью очищаем локальные блюда и заменяем их серверными.
            MealDao.clearMealsByUser(userId) // Если вы хотите заменять, а не объединять.

            // Вставляем свежие данные с сервера.
            serverMeals.forEach { serverMeal ->
                val localMeal = serverMeal.toLocalMeal(userId).copy(server_id = serverMeal.id)
                MealDao.insertMeal(localMeal)
            }
            Log.d("UserRepository", "Finished pulling meals.")

        } catch (e: Exception) {
            Log.e("UserRepository", "syncMeals error", e)
        }
    }



    suspend fun syncDailyMeals(userId: Long) {
        try {
            // --- ШАГ 1: PUSH (Отправка локальных изменений на сервер) ---
            // Сначала находим все записи, которых еще нет на сервере.
            val unsyncedDailyMeals = DailyMealDao.getDailyMealsByUser(userId).firstOrNull()?.filter { it.server_id == null }

            if (!unsyncedDailyMeals.isNullOrEmpty()) {
                Log.d("UserRepository", "Found ${unsyncedDailyMeals.size} unsynced daily meals to push.")
                unsyncedDailyMeals.forEach { dailyMeal ->
                    // Убеждаемся, что у локальной записи есть id. Если его нет, это ошибка в логике вставки.
                    dailyMeal.id?.let { localId ->
                        try {
                            val serverResponse = api.postDailyMeal(dailyMeal.toApiModel())
                            // Обновляем server_id у локальной записи после успешной отправки.
                            DailyMealDao.updateServerId(localId, serverResponse.id)
                            Log.d("UserRepository", "Pushed unsynced DailyMeal (local_id=$localId), new server_id = ${serverResponse.id}")
                        } catch (e: Exception) {
                            Log.e("UserRepository", "Failed to push DailyMeal (id=${localId}): ${e.message}")
                        }
                    } ?: run {
                        Log.e("UserRepository", "Cannot push DailyMeal because its local id is null.")
                    }
                }
            } else {
                Log.d("UserRepository", "No unsynced daily meals to push.")
            }

            // --- ШАГ 2: PULL (Загрузка актуальных данных с сервера) ---
            Log.d("UserRepository", "Starting pull for daily meals...")
            val serverDailyMeals = api.getDailyMeals()
            Log.d("UserRepository", "Pulled ${serverDailyMeals.size} daily meals from server.")

            // Очищаем старые данные (опционально, зависит от вашей стратегии синхронизации)
            // Если вы хотите объединять данные, а не заменять, логика будет сложнее.
            // Судя по вашему коду, вы хотите полностью заменять.
            DailyMealDao.clearDailyMealsByUser(userId)

            // Вставляем свежие данные с сервера.
            serverDailyMeals.forEach { serverDaily ->
                // Мы уже знаем server_id, так как данные пришли с сервера.
                val localDaily = serverDaily.toLocalDailyMeal(userId).copy(server_id = serverDaily.id)
                DailyMealDao.insertDailyMeal(localDaily) // Эта функция все еще должна возвращать Long, даже если мы его здесь не используем.
            }
            Log.d("UserRepository", "Finished pulling daily meals.")

        } catch (e: Exception) {
            Log.e("UserRepository", "syncDailyMeals error: ${e.message}", e)
        }
    }


    suspend fun fullSync(userId: Long) {
        syncMeals(userId)
        syncDailyMeals(userId)
    }


    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return BCrypt.checkpw(password, hashedPassword)
    }
}