package com.example.bonded

import android.os.Bundle
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinksActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var adapter: LinksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_links)

        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
        val recyclerView = findViewById<RecyclerView>(R.id.linksRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        db = AppDatabase.getDatabase(this)
        messageDao = db.messageDao()

        val currentUser = intent.getStringExtra("currentUser") ?: return
        val targetUser = intent.getStringExtra("targetUser") ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val allMessages = messageDao.getMessagesBetween(currentUser, targetUser)

            val links = allMessages.filter {
                it.content.matches(Regex("(https?://\\S+)|(www\\.\\S+)"))
            }.map { it.content to it.label }

            withContext(Dispatchers.Main) {
                adapter = LinksAdapter(links.toMutableList()) // Initialize with actual data
                recyclerView.adapter = adapter

                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener,
                    androidx.appcompat.widget.SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        adapter.filter(query ?: "")
                        return false
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        adapter.filter(newText ?: "")
                        return false
                    }
                })
            }
        }
    }
}
