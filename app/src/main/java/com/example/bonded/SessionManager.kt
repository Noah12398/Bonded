package com.example.bonded

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import io.socket.client.Socket

object SessionManager {
    fun autoLogin(context: Context) {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPref = EncryptedSharedPreferences.create(
            "secure_user_session",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val savedUsername = sharedPref.getString("username", null)
        val savedPassword = sharedPref.getString("password", null)

        if (!savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
            if (!SocketHandler.isInitialized()) {
                SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
            }

            val socket = SocketHandler.getSocket()

            socket.off("login_success")
            socket.off("login_error")

            if (!socket.connected()) {
                SocketHandler.establishConnection()
                socket.once(Socket.EVENT_CONNECT) {
                    val loginData = JSONObject().apply {
                        put("username", savedUsername)
                        put("password", savedPassword)
                    }
                    socket.emit("register", loginData)
                }
            } else {
                val loginData = JSONObject().apply {
                    put("username", savedUsername)
                    put("password", savedPassword)
                }
                socket.emit("register", loginData)
            }
        }
    }
}
