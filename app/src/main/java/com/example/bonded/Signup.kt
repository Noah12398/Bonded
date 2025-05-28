package com.example.bonded

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.bonded.ui.theme.BondedTheme

class Signup : AppCompatActivity() {
    private lateinit var editmail: EditText
    private lateinit var edituser: EditText
    private lateinit var editpassword: EditText
    private lateinit var signup: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        editmail=findViewById(R.id.Email)
        edituser=findViewById(R.id.Username)
        editpassword=findViewById(R.id.password)
        signup=findViewById(R.id.signup)

        signup.setOnClickListener{
            val intent= Intent(this,Signup::class.java)
            startActivity(intent)
        }
    }
}