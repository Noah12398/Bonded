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
    private lateinit var editpassword: EditText
    private lateinit var login: Button
    private lateinit var signup: Button
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Log.d(TAG, "onCreate called")

        // Initialize views
        try {
            editUser = findViewById(R.id.Username)
            editpassword = findViewById(R.id.password)
            login = findViewById(R.id.button)
            signup = findViewById(R.id.signup)
            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views", e)
            Toast.makeText(this, "Error loading login screen", Toast.LENGTH_LONG).show()
            return
        }

        // Check if already logged in (do this synchronously but quickly)
        checkExistingSession()

        // Set up login button
        login.setOnClickListener {
            val username = editUser.text.toString().trim()
            val password = editpassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button to prevent multiple clicks
            login.isEnabled = false
            login.text = "Logging in..."

            // Perform login on background thread
            performLogin(username, password)
        }

        signup.setOnClickListener {
            val intent = Intent(this, Signup::class.java)
            startActivity(intent)
        }
    }

    private fun checkExistingSession() {
        try {
            val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
            val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
            val savedUsername = sharedPref.getString("username", "")

            if (isLoggedIn && !savedUsername.isNullOrEmpty()) {
                Log.d(TAG, "User already logged in, redirecting to home")
                val intent = Intent(this, Homescreen::class.java)
                intent.putExtra("username", savedUsername)
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing session", e)
        }
    }

    private fun performLogin(username: String, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting login process for user: $username")

                // Since socket is already initialized in Application class, just get it
                if (!SocketHandler.isInitialized()) {
                    Log.d(TAG, "Socket not initialized, initializing now")
                    SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
                }

                val socket = SocketHandler.getSocket()

                // Remove any existing listeners to avoid duplicates
                socket.off("login_success")
                socket.off("login_error")
                socket.off(Socket.EVENT_CONNECT_ERROR)
                socket.off(Socket.EVENT_DISCONNECT)

                // Set up listeners
                setupSocketListeners(socket, username)

                // If not connected, establish connection
                if (!socket.connected()) {
                    Log.d(TAG, "Socket not connected, connecting...")
                    SocketHandler.establishConnection()

                    // Wait for connection with timeout (non-blocking way)
                    var waitTime = 0
                    while (!socket.connected() && waitTime < 5000) { // 5 second timeout
                        delay(100) // Use coroutine delay instead of Thread.sleep
                        waitTime += 100
                    }

                    if (!socket.connected()) {
                        throw Exception("Failed to connect to server after 5 seconds")
                    }
                }

                Log.d(TAG, "Socket connected, sending login credentials")

                // Send login request
                val credentials = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                socket.emit("register", credentials)
                Log.d(TAG, "Login credentials sent")

            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                runOnUiThread {
                    resetLoginButton()
                    Toast.makeText(this@Login, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupSocketListeners(socket: Socket, username: String) {
        socket.on("login_success") {
            Log.d(TAG, "Login successful")
            runOnUiThread {
                try {
                    val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putBoolean("isLoggedIn", true)
                        putString("username", username)
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
            Log.d(TAG, "Login error received")
            runOnUiThread {
                resetLoginButton()
                val errorMsg = if (args.isNotEmpty()) args[0].toString() else "Login failed"
                Toast.makeText(this@Login, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }

        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "Socket connection error: ${args.contentToString()}")
            runOnUiThread {
                resetLoginButton()
                Toast.makeText(this@Login, "Failed to connect to server", Toast.LENGTH_LONG).show()
            }
        }

        socket.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Socket disconnected")
            runOnUiThread {
                resetLoginButton()
                Toast.makeText(this@Login, "Connection lost", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetLoginButton() {
        login.isEnabled = true
        login.text = "Login"
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        // Clean up socket listeners
        try {
            if (SocketHandler.isInitialized()) {
                val socket = SocketHandler.getSocket()
                socket.off("login_success")
                socket.off("login_error")
                socket.off(Socket.EVENT_CONNECT_ERROR)
                socket.off(Socket.EVENT_DISCONNECT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up socket listeners", e)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }
}