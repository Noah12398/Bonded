package com.example.bonded
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth

class Login : AppCompatActivity() {
    private lateinit var editUser:EditText
    private lateinit var editpassword:EditText
    private lateinit var login:Button
    private lateinit var signup:Button
    private lateinit var mAuth:FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth=FirebaseAuth.getInstance()
        editUser=findViewById(R.id.Username)
        editpassword=findViewById(R.id.password)
        login=findViewById(R.id.button)
        signup=findViewById(R.id.signup)

        login.setOnClickListener{
            val intent= Intent(this,Homescreen::class.java)
            startActivity(intent)
        }
        signup.setOnClickListener{
            val intent= Intent(this,Signup::class.java)
            startActivity(intent)
        }
    }
}
