package com.example.bonded
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
//import com.google.firebase.auth.FirebaseAuth
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URISyntaxException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

object SocketHandler {
    private lateinit var socket: Socket
    private const val TAG = "SocketHandler"

    fun isInitialized(): Boolean {
        return ::socket.isInitialized
    }

    fun isConnected(): Boolean {
        return isInitialized() && socket.connected()
    }

    @Synchronized
    fun setSocket(serverUrl: String) {
        Log.d(TAG, "setSocket() called with URL: $serverUrl")
        if (::socket.isInitialized) {
            Log.d(TAG, "Socket already initialized")
            return
        }

        try {
            val options = IO.Options().apply {
                timeout = 10000
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                transports = arrayOf("websocket", "polling")
            }

            socket = IO.socket(serverUrl, options)
            Log.d(TAG, "Socket created successfully")
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Invalid URI for socket", e)
            throw e
        }
    }

    @Synchronized
    fun getSocket(): Socket {
        if (!::socket.isInitialized) {
            throw IllegalStateException("Socket not initialized. Call setSocket() first.")
        }
        return socket
    }

    @Synchronized
    fun establishConnection() {
        if (::socket.isInitialized) {
            if (!socket.connected()) {
                Log.d(TAG, "Connecting socket...")
                socket.connect()
            } else {
                Log.d(TAG, "Socket already connected")
            }
        } else {
            Log.w(TAG, "establishConnection() called before socket was initialized")
        }
    }

    @Synchronized
    fun closeConnection() {
        if (::socket.isInitialized) {
            Log.d(TAG, "Disconnecting socket and removing all listeners...")
            socket.disconnect()
            socket.off()
        }
    }
}




class LoginActivity : ComponentActivity() {
    private val TAG = "LoginCompose"
    private val context by lazy { this }

    private val sharedPref by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_user_session",
            masterKey,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedUsername = sharedPref.getString("username", "") ?: ""
        val savedPassword = sharedPref.getString("password", "") ?: ""
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

        if (isLoggedIn && savedUsername.isNotEmpty() && savedPassword.isNotEmpty()) {
            performLogin(savedUsername, savedPassword)
        }

        setContent {
            LoginScreen { username, password ->
                performLogin(username, password)
            }
        }
    }

    private fun performLogin(username: String, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!SocketHandler.isInitialized()) {
                    SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
                }

                val socket = SocketHandler.getSocket()

                socket.off("login_success")
                socket.off("login_error")

                setupSocketListeners(socket, username, password)

                if (!socket.connected()) {
                    SocketHandler.establishConnection()
                    var waitTime = 0
                    while (!socket.connected() && waitTime < 5000) {
                        delay(100)
                        waitTime += 100
                    }
                    if (!socket.connected()) throw Exception("Failed to connect to server")
                }

                val credentials = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                socket.emit("register", credentials)
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupSocketListeners(socket: Socket, username: String, password: String) {
        socket.on("login_success") {
            runOnUiThread {
                with(sharedPref.edit()) {
                    putBoolean("isLoggedIn", true)
                    putString("username", username)
                    putString("password", password)
                    apply()
                }

                val intent = Intent(this@LoginActivity, Homescreen::class.java)
                intent.putExtra("username", username)
                startActivity(intent)
                finish()
            }
        }

        socket.on("login_error") { args ->
            runOnUiThread {
                val errorMsg = if (args.isNotEmpty()) args[0].toString() else "Login failed"
                Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (SocketHandler.isInitialized()) {
            val socket = SocketHandler.getSocket()
            socket.off("login_success")
            socket.off("login_error")
        }
    }
}
