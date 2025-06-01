package com.example.bonded

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.util.CollectionUtils.listOf
import io.socket.client.Socket
import org.json.JSONArray

class Homescreen : AppCompatActivity() {
    private lateinit var chatList: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var adapter: UserAdapter
    private val users = mutableListOf<String>()
    private lateinit var socket: Socket
    private lateinit var username: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        username = intent.getStringExtra("username").toString()
        chatList = findViewById(R.id.chatList)
        searchBar = findViewById(R.id.searchBar)

        adapter = UserAdapter(users) { name ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userName", name)
            intent.putExtra("selfUser", username)
            startActivity(intent)
        }

        chatList.layoutManager = LinearLayoutManager(this)
        chatList.adapter = adapter

        socket = SocketHandler.getSocket()
        socket.emit("get_chat_users", username)

        socket.on("chat_users_list") { args ->
            val userList = args[0] as JSONArray
            users.clear()
            for (i in 0 until userList.length()) {
                users.add(userList.getString(i))
            }
            runOnUiThread { adapter.notifyDataSetChanged() }
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                socket.emit("search_users", s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        socket.on("search_results") { args ->
            val searchResult = args[0] as JSONArray
            users.clear()
            for (i in 0 until searchResult.length()) {
                users.add(searchResult.getString(i))
            }
            runOnUiThread { adapter.notifyDataSetChanged() }
        }
    }
}
