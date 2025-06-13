package com.example.bonded

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_links)

        val currentUser = intent.getStringExtra("currentUser") ?: return
        val targetUser = intent.getStringExtra("targetUser") ?: return

        val recyclerView = findViewById<RecyclerView>(R.id.linksRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        db = AppDatabase.getDatabase(this)
        messageDao = db.messageDao()

        lifecycleScope.launch(Dispatchers.IO) {
            val allMessages = messageDao.getMessagesBetween(currentUser, targetUser)

            val links = allMessages.filter {
                it.content.matches(Regex("(https?://\\S+)|(www\\.\\S+)"))
            }

            withContext(Dispatchers.Main) {
                recyclerView.adapter = LinksAdapter(links.map { it.content to it.label })
            }
        }
    }
}
