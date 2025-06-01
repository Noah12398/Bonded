package com.example.bonded

import com.example.bonded.Message

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.socket.client.Socket
import org.json.JSONObject



data class Message(val content: String, val isSentByCurrentUser: Boolean)

class ChatActivity : AppCompatActivity() {

    private lateinit var socket: Socket
    private lateinit var currentUser: String
    private lateinit var targetUser: String

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

        adapter = MessageAdapter(messages)
        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = adapter

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                messageInput.text.clear()
                addMessage(text, true)
            }
        }

        // Receive message from server
        socket.on("private_message") { args ->
            val data = args[0] as JSONObject
            val fromUser = data.getString("from")
            val message = data.getString("message")

            // Only add if it's from targetUser
            if (fromUser == targetUser) {
                runOnUiThread {
                    addMessage(message, false)
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
    }

    private fun addMessage(text: String, isSentByCurrentUser: Boolean) {
        messages.add(Message(text, isSentByCurrentUser))
        adapter.notifyItemInserted(messages.size - 1)
        messageList.scrollToPosition(messages.size - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.off("private_message")
    }
}
