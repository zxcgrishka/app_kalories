package com.example.test2.utils

import android.content.Context
import android.util.Log
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object TokenManager {
    private const val PREF_NAME = "auth_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USERNAME = "username"
    private const val TAG = "TokenManager"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREF_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(context: Context, token: String, username: String) {
        Log.d(TAG, "Saving token for $username")
        getPrefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .apply()
        Log.d(TAG, "Token saved: ${getToken(context) != null}")
    }

    fun getToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_TOKEN, null)
        Log.d(TAG, "Getting token: $token")
        return token
    }

    fun getUsername(context: Context): String? {
        return getPrefs(context).getString(KEY_USERNAME, null)
    }

    fun clearToken(context: Context) {
        Log.d(TAG, "Clearing token")
        getPrefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        val loggedIn = getToken(context) != null
        Log.d(TAG, "isLoggedIn: $loggedIn")
        return loggedIn
    }
}