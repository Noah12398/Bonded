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
        try {
            val options = IO.Options().apply {
                // Connection timeout
                timeout = 10000
                // Reconnection settings
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                // Transport options
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
    private lateinit var editUser:EditText
    private lateinit var editpassword:EditText
    private lateinit var login:Button
    private lateinit var signup:Button
    //private lateinit var mAuth:FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        //FirebaseDatabase.getInstance().reference.child("Test").setValue("Hello world")
        editUser=findViewById(R.id.Username)
        editpassword=findViewById(R.id.password)
        login=findViewById(R.id.button)
        signup=findViewById(R.id.signup)



        val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
        val savedUsername = sharedPref.getString("username", "")

        if (isLoggedIn && !savedUsername.isNullOrEmpty()) {
            val intent = Intent(this, Homescreen::class.java)
            intent.putExtra("username", savedUsername)
            startActivity(intent)
            finish()
            return
        }
        login.setOnClickListener {
            val username = editUser.text.toString().trim()
            val password = editpassword.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {

                lifecycleScope.launch(Dispatchers.IO) {
                    if (!SocketHandler.isInitialized()) {
                        SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
                    }
                    val socket = SocketHandler.getSocket()

                    socket.on(Socket.EVENT_CONNECT) {
                        val credentials = JSONObject().apply {
                            put("username", username)
                            put("password", password)
                        }
                        socket.emit("register", credentials)
                    }

                    socket.on("login_success") {
                        runOnUiThread {
                            val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
                            with(sharedPref.edit()) {
                                putBoolean("isLoggedIn", true)
                                putString("username", username)
                                apply()
                            }

                            // Connect after login and store identity
                            SocketHandler.establishConnection()

                            val intent = Intent(this@Login, Homescreen::class.java)
                            intent.putExtra("username", username)
                            startActivity(intent)
                            finish()
                        }
                    }

                    socket.on("login_error") { args ->
                        val errorMsg = args[0] as String
                        runOnUiThread {
                            Toast.makeText(this@Login, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Establish connection AFTER listeners are set
                    SocketHandler.establishConnection()
                }
            }
        }



        signup.setOnClickListener{
            val intent= Intent(this,Signup::class.java)
            startActivity(intent)
        }
    }
}