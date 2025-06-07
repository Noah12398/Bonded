package com.example.bonded
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat)

        // Get usernames from Intent
        currentUser = intent.getStringExtra("selfUser") ?: return
        targetUser = intent.getStringExtra("userName") ?: return

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

                adapter.notifyDataSetChanged()
                messageList.scrollToPosition(messages.size - 1)
            }

        }



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
        }

        Toast.makeText(this, "Label saved for link", Toast.LENGTH_SHORT).show()
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
        adapter.notifyItemInserted(messages.size - 1)
        messageList.scrollToPosition(messages.size - 1)

        Toast.makeText(this, "Message received: ${msg.content}", Toast.LENGTH_SHORT).show()

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
