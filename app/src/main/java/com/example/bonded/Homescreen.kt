package com.example.bonded

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.util.CollectionUtils.listOf


class Homescreen : AppCompatActivity() {
    private lateinit var chatList: RecyclerView
    private val users = listOf("Alice", "Bob", "Charlie")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        chatList = findViewById(R.id.chatList)
        chatList.layoutManager = LinearLayoutManager(this)
        chatList.adapter = UserAdapter(users) { name ->
            // Start chat activity with this user
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userName", name)
            startActivity(intent)
        }
    }
}

