package com.example.bonded
import android.content.Intent
import android.util.Log

import com.example.bonded.Message

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.socket.client.Socket
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar


data class Message(
    val content: String,
    val isSentByCurrentUser: Boolean,
    val label: String? = null
)


class ChatActivity : AppCompatActivity() {

    private lateinit var socket: Socket
    private lateinit var currentUser: String
    private lateinit var targetUser: String
    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao

    private lateinit var messageList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter
    private val allMessages = mutableListOf<Message>() // stores all messages

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat)

        // Get usernames from Intent
        currentUser = intent.getStringExtra("selfUser") ?: return
        targetUser = intent.getStringExtra("userName") ?: return

        val chatToolbar = findViewById<Toolbar>(R.id.chatToolbar)
        chatToolbar.title = targetUser // Replace with actual user name
        setSupportActionBar(chatToolbar)

// Set click listener on the toolbar title
        chatToolbar.setOnClickListener {
            val intent = Intent(this, LinksActivity::class.java)
            intent.putExtra("currentUser", currentUser)
            intent.putExtra("targetUser", targetUser)
            startActivity(intent)

        }

        socket = SocketHandler.getSocket()

        messageList = findViewById(R.id.messageList)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        adapter = MessageAdapter(messages) { position ->
            val content = messages[position].content
            if (isLink(content)) {
                showLabelDialog(position)
            }
        }

        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = adapter


        db = AppDatabase.getDatabase(this)
        messageDao = db.messageDao()

// Load previous messages
        lifecycleScope.launch(Dispatchers.IO) {
            val savedMessages = messageDao.getMessagesBetween(currentUser, targetUser)
            withContext(Dispatchers.Main) {
                messages.addAll(savedMessages.map {
                    Message(it.content, it.sender == currentUser, it.label)
                })
                allMessages.addAll(messages)
                adapter.notifyDataSetChanged()
                messageList.scrollToPosition(messages.size - 1)
            }

        }
        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                val filtered = if (query.isEmpty()) {
                    allMessages
                } else {
                    allMessages.filter { message ->
                        message.content.contains(query, ignoreCase = true) ||
                                message.label?.contains(query, ignoreCase = true) == true
                    }
                }

                messages.clear()
                messages.addAll(filtered)
                adapter.notifyDataSetChanged()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })




        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                messageInput.text.clear()
                addMessage(text, true)
            }
        }


        // Receive message from server

// Inside your onCreate or wherever you have this listener
                socket.on("private_message") { args ->
                    Log.d("SocketEvent", "Received private_message event")

                    try {
                        val data = args[0] as JSONObject
                        val fromUser = data.optString("from", "Unknown")
                        val message = data.getString("message")

                        Log.d("SocketEvent", "Message received from: $fromUser | message: $message")

                        // Only add if it's from targetUser
                        if (fromUser == targetUser) {
                            Log.d("SocketEvent", "Message accepted from targetUser: $targetUser")
                            runOnUiThread {
                                addMessage(message, false)
                            }
                        } else {
                            Log.d("SocketEvent", "Message ignored from non-target user: $fromUser")
                        }

                    } catch (e: Exception) {
                        Log.e("SocketEvent", "Error processing private_message event", e)
                    }
                }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                val searchBar = findViewById<LinearLayout>(R.id.searchBarContainer)
                searchBar.visibility =
                    if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun isLink(text: String): Boolean {
        val urlRegex = "(https?://\\S+)|(www\\.\\S+)".toRegex()
        return urlRegex.containsMatchIn(text)
    }
    private fun showLabelDialog(position: Int) {
        val input = EditText(this)
        input.hint = "Enter label"

        AlertDialog.Builder(this)
            .setTitle("Label this link")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isNotEmpty()) {
                    labelMessage(position, label)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun labelMessage(position: Int, label: String) {
        val msg = messages[position]
        val content = msg.content

        lifecycleScope.launch(Dispatchers.IO) {
            val existingMsg = messageDao.getSpecificMessage(content, currentUser, targetUser)
            if (existingMsg != null) {
                val updatedMsg = existingMsg.copy(label = label)
                messageDao.updateMessage(updatedMsg)
            }

            withContext(Dispatchers.Main) {
                // update label in UI too
                messages[position] = msg.copy(label = label)
                allMessages[allMessages.indexOfFirst { it.content == msg.content && it.isSentByCurrentUser == msg.isSentByCurrentUser }] =
                    msg.copy(label = label)

                adapter.notifyItemChanged(position)
                Toast.makeText(this@ChatActivity, "Label saved for link", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun sendMessage(messageText: String) {
        val messageData = JSONObject().apply {
            put("to", targetUser)
            put("message", messageText)
        }
        socket.emit("private_message", messageData)
    }

    private fun addMessage(text: String, isSentByCurrentUser: Boolean) {
        val msg = Message(text, isSentByCurrentUser)
        messages.add(msg)
        allMessages.add(msg) // keep in sync

        adapter.notifyItemInserted(messages.size - 1)
        messageList.scrollToPosition(messages.size - 1)

        lifecycleScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(
                MessageEntity(
                    content = text,
                    isSentByCurrentUser = isSentByCurrentUser,
                    sender = if (isSentByCurrentUser) currentUser else targetUser,
                    receiver = if (isSentByCurrentUser) targetUser else currentUser
                )
            )
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        socket.off("private_message")
    }
}
