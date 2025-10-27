package com.example.test2.data  // Твоя package

import android.content.Context
import android.util.Log
import com.example.test2.network.ApiService
import com.example.test2.network.Token
import com.example.test2.network.User as NetworkUser
import com.example.test2.utils.TokenManager
import com.example.test2.data.User
import com.example.test2.data.UserDao
import com.example.test2.network.UserCreate
import kotlinx.coroutines.flow.Flow
import org.mindrot.jbcrypt.BCrypt
import retrofit2.HttpException

class UserRepository(
    private val dao: UserDao,
    private val api: ApiService,
    private val context: Context
) {
    fun getAllUsers(): Flow<List<User>> = dao.getAllUsers()

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
            dao.insert(localUser)
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
            val user = dao.getUserByUsername(username)
            if (user != null && verifyPassword(password, user.hashedPassword)) {
                TokenManager.saveToken(context, TokenManager.getToken(context) ?: "", username)
                Result.success("Login successful (offline)")
            } else {
                val token = api.login(username, password)
                TokenManager.saveToken(context, token.access_token, username)
                Log.d("UserRepository", "Token saved in login for $username")
                val hashedPassword = hashPassword(password)
                dao.insert(User(id = 0, username = username, hashedPassword = hashedPassword, lastModified = System.currentTimeMillis()))
                Result.success("Login successful")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> Result.failure(Exception("Incorrect username or password"))
                else -> Result.failure(Exception("Login failed: ${e.message()}"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Login error: ${e.message}", e)
            val user = dao.getUserByUsername(username)
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

    suspend fun logout() {
        TokenManager.clearToken(context)
        dao.clearAll()
    }

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return BCrypt.checkpw(password, hashedPassword)
    }
}