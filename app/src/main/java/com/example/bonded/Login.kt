package com.example.bonded
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException
import org.json.JSONObject



object SocketHandler {
    private lateinit var socket: Socket
    private val TAG = "SocketHandler"

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
    private lateinit var mAuth:FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        //FirebaseDatabase.getInstance().reference.child("Test").setValue("Hello world")
        editUser=findViewById(R.id.Username)
        editpassword=findViewById(R.id.password)
        login=findViewById(R.id.button)
        signup=findViewById(R.id.signup)

        try {
            SocketHandler.getSocket()
        } catch (e: IllegalStateException) {
            SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
            SocketHandler.establishConnection()
        }

        login.setOnClickListener {
            val username = editUser.text.toString().trim()
            val password=editpassword.text.toString().trim()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                // Set up socket and connect
                SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
                val socket = SocketHandler.getSocket()
                SocketHandler.establishConnection()

                socket.on(Socket.EVENT_CONNECT) {
                    val credentials = JSONObject().apply {
                        put("username", username)
                        put("password", password)
                    }
                    socket.emit("register", credentials)

                }

                // Move navigation into this block:
                socket.on("login_success") {
                    runOnUiThread {
                        val intent = Intent(this, Homescreen::class.java)
                        intent.putExtra("username", username)
                        startActivity(intent)
                    }
                }

                socket.on("login_error") { args ->
                    val errorMsg = args[0] as String
                    runOnUiThread {
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }



            }
        }

        signup.setOnClickListener{
            val intent= Intent(this,Signup::class.java)
            startActivity(intent)
        }
    }
}