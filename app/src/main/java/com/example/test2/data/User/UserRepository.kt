package com.example.test2.data.User

import android.content.Context
import android.util.Log
import com.example.test2.data.AppDatabase
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.DailyMealDao
import com.example.test2.data.Meal
import com.example.test2.data.MealDao
import com.example.test2.network.ApiService
import com.example.test2.network.UserCreate
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.ProductDao
import com.example.test2.utils.TokenManager
import kotlinx.coroutines.flow.Flow
import org.mindrot.jbcrypt.BCrypt
import retrofit2.HttpException

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
            val token = api.login(username, password)
            TokenManager.saveToken(context, token.access_token, username)
            Log.d("UserRepository", "Token saved in register for $username")
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
                val token = api.login(username, password)
                TokenManager.saveToken(context, token.access_token, username)
                Log.d("UserRepository", "Token saved in login for $username")
                val hashedPassword = hashPassword(password)
                Userdao.insert(User(id = 0, username = username, hashedPassword = hashedPassword, lastModified = System.currentTimeMillis()))
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
    suspend fun insertMeal(meal: Meal) = MealDao.insertMeal(meal)

    fun getAllProducts(): Flow<List<Product>> = ProductDao.getAllProducts()
    suspend fun logout() {
        TokenManager.clearToken(context)
        Userdao.clearAll()
    }

    fun getTodayDailyMealsByUser(userId: Long): Flow<List<DailyMeal>> = DailyMealDao.getTodayDailyMealsByUser(userId)
    suspend fun insertDailyMeal(dailyMeal: DailyMeal) {
        DailyMealDao.insertDailyMeal(dailyMeal)
    }
    suspend fun clearDailyMealsByUser(userId: Long) {
        DailyMealDao.clearDailyMealsByUser(userId)
    }


    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return BCrypt.checkpw(password, hashedPassword)
    }
}