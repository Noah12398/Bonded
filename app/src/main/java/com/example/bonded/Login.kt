package com.example.bonded
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
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
object SocketHandler {
    private lateinit var socket: Socket
    private val TAG = "SocketHandler"

    fun isInitialized(): Boolean {
        return ::socket.isInitialized
    }

    @Synchronized
    fun setSocket(serverUrl: String) {
        Log.d(TAG, "SocketHandler::setSocket called")
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
            Log.d(TAG, "Socket created for URL: $serverUrl")
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Invalid URI: $serverUrl", e)
            throw e
        }
    }

    @Synchronized
    fun getSocket(): Socket {
        Log.d(TAG, "SocketHandler::getSocket called")
        if (!::socket.isInitialized) {
            throw IllegalStateException("Socket not initialized. Call setSocket() first.")
        }
        return socket
    }

    @Synchronized
    fun establishConnection() {
        if (::socket.isInitialized && !socket.connected()) {
            Log.d(TAG, "Establishing socket connection...")
            socket.connect()
        } else if (::socket.isInitialized && socket.connected()) {
            Log.d(TAG, "Socket already connected")
        }
    }

    @Synchronized
    fun closeConnection() {
        if (::socket.isInitialized) {
            Log.d(TAG, "Closing socket connection...")
            socket.disconnect()
            socket.off()
        }
    }
}



class Login : AppCompatActivity() {
    private lateinit var editUser: EditText
    private lateinit var editPassword: EditText
    private lateinit var login: Button
    private lateinit var signup: Button
    private val TAG = "LoginActivity"

    // Encrypted SharedPreferences
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
        setContentView(R.layout.activity_login)

        editUser = findViewById(R.id.Username)
        editPassword = findViewById(R.id.password)
        login = findViewById(R.id.button)
        signup = findViewById(R.id.signup)

        checkExistingSession()

        login.setOnClickListener {
            val username = editUser.text.toString().trim()
            val password = editPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            login.isEnabled = false
            login.text = "Logging in..."
            performLogin(username, password)
        }

        signup.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
        }
    }

    private fun checkExistingSession() {
        try {
            val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
            val savedUsername = sharedPref.getString("username", "")
            val savedPassword = sharedPref.getString("password", "")

            if (isLoggedIn && !savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                performLogin(savedUsername, savedPassword)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing session", e)
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
                    resetLoginButton()
                    Toast.makeText(this@Login, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupSocketListeners(socket: Socket, username: String, password: String) {
        socket.on("login_success") {
            runOnUiThread {
                try {
                    with(sharedPref.edit()) {
                        putBoolean("isLoggedIn", true)
                        putString("username", username)
                        putString("password", password) // Securely stored
                        apply()
                    }

                    val intent = Intent(this@Login, Homescreen::class.java)
                    intent.putExtra("username", username)
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling login success", e)
                    resetLoginButton()
                }
            }
        }

        socket.on("login_error") { args ->
            runOnUiThread {
                resetLoginButton()
                val errorMsg = if (args.isNotEmpty()) args[0].toString() else "Login failed"
                Toast.makeText(this@Login, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetLoginButton() {
        login.isEnabled = true
        login.text = "Login"
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
