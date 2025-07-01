package com.example.bonded

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.Socket
import org.json.JSONObject

class Signup : AppCompatActivity() {
    private lateinit var editMail: EditText
    private lateinit var editUser: EditText
    private lateinit var editPassword: EditText
    private lateinit var signup: Button
    private lateinit var socket: Socket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        editMail = findViewById(R.id.Email)
        editUser = findViewById(R.id.Username)
        editPassword = findViewById(R.id.password)
        signup = findViewById(R.id.signup)

        // Ensure socket is set up
        if (!SocketHandler.isInitialized()) {
            throw IllegalStateException("Socket should be initialized in MyApp")
        }
        socket = SocketHandler.getSocket()
        if (!socket.connected()) {
            SocketHandler.establishConnection()
        }


        signup.setOnClickListener {
            val username = editUser.text.toString().trim()
            val password = editPassword.text.toString().trim()
            val email = editMail.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                val signupData = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                    put("email", email)
                }

                Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT).show()  // Debug line

                socket.emit("signup", signupData)

                socket.once("signup_success") {
                    runOnUiThread {
                        Toast.makeText(this, "Signup successful! Please login.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }

                socket.once("signup_error") { args ->
                    val error = args[0] as String
                    runOnUiThread {
                        Toast.makeText(this, "Signup failed: $error", Toast.LENGTH_SHORT).show()
                    }
                }

            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
