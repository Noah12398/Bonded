package com.example.bonded

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
//import com.google.android.gms.common.util.CollectionUtils.listOf
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class Homescreen : AppCompatActivity() {
    private lateinit var chatList: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var adapter: UserAdapter
    private val users = mutableListOf<String>()
    private lateinit var socket: Socket
    private lateinit var username: String
    private lateinit var logout:Button
    private lateinit var messageDao: MessageDao
    private lateinit var searchInputLayout: TextInputLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        username = intent.getStringExtra("username").toString()
        chatList = findViewById(R.id.chatList)
        searchBar = findViewById(R.id.searchBar)
        searchInputLayout = findViewById(R.id.searchInputLayout) // Add this ID in XML

        logout=findViewById(R.id.logoutButton)
        adapter = UserAdapter(users) { name ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userName", name)
            intent.putExtra("selfUser", username)
            startActivity(intent)
        }

        chatList.layoutManager = LinearLayoutManager(this)
        chatList.adapter = adapter

        if (!SocketHandler.isInitialized()) {
            SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
            SocketHandler.establishConnection()
        }
        socket = SocketHandler.getSocket()

        // Assuming you already have a reference to your database
        messageDao = AppDatabase.getDatabase(this).messageDao()

        lifecycleScope.launch {
            val chattedUsers = withContext(Dispatchers.IO) {
                messageDao.getUsersChattedWith(username)
            }

            users.clear()
            users.addAll(chattedUsers)
            adapter.notifyDataSetChanged()
        }


        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    socket.emit("search_users", query)
                } else {
                    // Hide all search results if search bar is empty
                    lifecycleScope.launch {
                        val chattedUsers = withContext(Dispatchers.IO) {
                            messageDao.getUsersChattedWith(username)
                        }

                        users.clear()
                        users.addAll(chattedUsers)
                        adapter.notifyDataSetChanged()
                    }
                }
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
        searchInputLayout.setEndIconOnClickListener {
            val searchEditText = findViewById<TextInputEditText>(R.id.searchBar)

            searchEditText.text?.clear()  // Manually clear if needed
            // Reload chat list
            lifecycleScope.launch {
                val chattedUsers = withContext(Dispatchers.IO) {
                    messageDao.getUsersChattedWith(username)
                }

                users.clear()
                users.addAll(chattedUsers)
                adapter.notifyDataSetChanged()
            }
        }

        logout.setOnClickListener {
            val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
            with(sharedPref.edit()) {
                clear()
                apply()
            }
            socket.disconnect() // Disconnect from Socket.IO

            // Navigate to login screen
            val intent = Intent(this, Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val chattedUsers = withContext(Dispatchers.IO) {
                messageDao.getUsersChattedWith(username)
            }

            users.clear()
            users.addAll(chattedUsers)
            adapter.notifyDataSetChanged()
        }
    }

}
