package com.example.bonded
import android.content.Intent
import android.util.Log
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
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
    val id: Int=0,
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


        lifecycleScope.launch {
            messageDao.getMessagesBetweenFlow(currentUser, targetUser).collect { dbMessages ->
                Log.d("FLOW", "Collected: ${dbMessages.map { it.content }}")

                val formattedMessages = dbMessages.map {
                    Message(
                        id = it.id,
                        content = it.content,
                        isSentByCurrentUser = it.sender == currentUser,
                        label = it.label
                    )
                }

                messageList.post {
                    messages.clear()
                    allMessages.clear()
                    messages.addAll(formattedMessages)
                    allMessages.addAll(formattedMessages)
                    adapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) {
                        messageList.scrollToPosition(messages.size - 1)
                    }
                }
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

                messageList.post {
                    messages.clear()
                    messages.addAll(filtered)
                    adapter.notifyDataSetChanged()
                }
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
    }


    override fun onResume() {
        super.onResume()
        SessionManager.autoLogin(this)

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
        val currentLabel = messages[position].label
        input.hint = "Enter label"
        input.setText(currentLabel ?: "")

        AlertDialog.Builder(this)
            .setTitle("Label this link")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim()
                labelMessage(position, label)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete Label") { _, _ ->
                labelMessage(position, "") // empty string means delete
            }
            .show()
    }


    private fun labelMessage(position: Int, label: String) {
        val msg = messages.getOrNull(position) ?: return
        val messageId = msg.id

        lifecycleScope.launch(Dispatchers.IO) {
            val existingMsg = messageDao.getMessageById(messageId)
            if (existingMsg != null) {
                val updatedMsg = existingMsg.copy(label = if (label.isEmpty()) null else label)
                messageDao.updateMessage(updatedMsg)
            }

            withContext(Dispatchers.Main) {
                val updatedLabel = if (label.isEmpty()) null else label
                val updatedMessage = msg.copy(label = updatedLabel)

                messageList.post {
                    if (position in messages.indices) {
                        messages[position] = updatedMessage
                        adapter.notifyItemChanged(position)
                    }

                    allMessages.replaceAll {
                        if (it.id == messageId) updatedMessage else it
                    }

                    val toastMsg = when {
                        label.isEmpty() -> "Label deleted"
                        msg.label == null -> "Label added"
                        else -> "Label updated"
                    }

                    Toast.makeText(this@ChatActivity, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }




    private fun sendMessage(messageText: String) {
        val messageData = JSONObject().apply {
            put("to", targetUser)
            put("message", messageText)
        }
        socket.emit("private_message", messageData)
        Log.d("MESSAGE_SENT", "Message sent to $targetUser: $messageText")
    }

    private fun addMessage(text: String, isSentByCurrentUser: Boolean) {
//        val newMessage = Message(content = text, isSentByCurrentUser = true)
//
//        messageList.post {
//            messages.add(newMessage)
//            allMessages.add(newMessage)
//            adapter.notifyItemInserted(messages.size - 1)
//            messageList.scrollToPosition(messages.size - 1)
//        }

        lifecycleScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(
                MessageEntity(
                    content = text,
                    isSentByCurrentUser = true,
                    sender = currentUser,
                    receiver = targetUser,
                    timestamp = System.currentTimeMillis()
                )
            )
            Log.d("DB_INSERT", "Sent message stored in DB: $text")
        }
    }


}