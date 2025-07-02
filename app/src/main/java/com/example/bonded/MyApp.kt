package com.example.bonded

import android.app.Application
import android.util.Log
import com.example.bonded.ui.login.SocketHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MyAppInit", "MyApp onCreate called")
        initializeSocket()
    }

    private fun initializeSocket() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("MyAppSocket", "initializeSocket() started")

                if (!SocketHandler.isInitialized()) {
                    SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
                    SocketHandler.establishConnection()
                    Log.d("MyAppSocket", "Socket connection established")
                }

                val socket = SocketHandler.getSocket()
                socket.off("private_message")

                socket.on("private_message") { args ->
                    Log.d("SocketEvent", "private_message received")
                    CoroutineScope(Dispatchers.IO).launch {
                        handleIncomingMessage(args)
                    }
                }

            } catch (e: Exception) {
                Log.e("SocketError", "Failed to initialize socket", e)
            }
        }
    }

    private suspend fun handleIncomingMessage(args: Array<Any>) {
        if (args.isEmpty()) {
            Log.e("SocketError", "No arguments in message")
            return
        }

        val messageJson = try {
            when (val arg = args[0]) {
                is JSONObject -> arg
                else -> JSONObject(arg.toString())
            }
        } catch (e: Exception) {
            Log.e("SocketError", "Failed to parse incoming message", e)
            return
        }

        saveMessageToDatabase(messageJson)
    }

    private suspend fun saveMessageToDatabase(data: JSONObject) {
        try {
            val from = data.getString("from")
            val message = data.getString("message")

            val sharedPref = applicationContext.getSharedPreferences("user_session", MODE_PRIVATE)
            val currentUser = sharedPref.getString("username", null)

            if (currentUser == null) {
                Log.e("DB_FLOW", "Current user is null")
                return
            }

            val db = AppDatabase.getDatabase(applicationContext)
            val messageDao = db.messageDao()

            val msgEntity = MessageEntity(
                content = message,
                isSentByCurrentUser = false,
                sender = from,
                receiver = currentUser,
                timestamp = System.currentTimeMillis()
            )

            messageDao.insertMessage(msgEntity)
            Log.d("DB_FLOW", "Message saved: $message from $from")

        } catch (e: Exception) {
            Log.e("DB_FLOW", "Failed to save message", e)
        }

    }

}
