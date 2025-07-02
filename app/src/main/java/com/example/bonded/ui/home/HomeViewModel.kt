package com.example.bonded.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bonded.AppDatabase
import com.example.bonded.MessageDao
import com.example.bonded.SessionManager
import com.example.bonded.ui.login.SocketHandler
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HomeViewModel : ViewModel() {

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users

    private lateinit var messageDao: MessageDao
    private lateinit var socket: Socket
    private lateinit var currentUsername: String

    fun initialize(username: String, context: Context) {
        if (!::socket.isInitialized) {
            socket = SocketHandler.getSocket()
        }

        if (!SocketHandler.isConnected()) {
            SocketHandler.establishConnection()
        }

        currentUsername = username
        messageDao = AppDatabase.getDatabase(context).messageDao()

        viewModelScope.launch {
            val chattedUsers = withContext(Dispatchers.IO) {
                messageDao.getUsersChattedWith(username)
            }
            _users.value = chattedUsers
        }

        socket.off("search_results")
        socket.on("search_results") { args ->
            val searchResult = args[0] as JSONArray
            val resultList = mutableListOf<String>()
            for (i in 0 until searchResult.length()) {
                resultList.add(searchResult.getString(i))
            }
            _users.value = resultList
        }
    }

    fun onSearchChange(query: String) {
        if (query.isNotEmpty()) {
            socket.emit("search_users", query)
        } else {
            viewModelScope.launch {
                val chattedUsers = withContext(Dispatchers.IO) {
                    messageDao.getUsersChattedWith(currentUsername)
                }
                _users.value = chattedUsers
            }
        }
    }

    fun clearSearch() {
        viewModelScope.launch {
            val chattedUsers = withContext(Dispatchers.IO) {
                messageDao.getUsersChattedWith(currentUsername)
            }
            _users.value = chattedUsers
        }
    }

    fun logout(context: Context) {
        SessionManager.hasEmittedLogin = false
        SocketHandler.closeConnection()

        val sharedPref = androidx.security.crypto.EncryptedSharedPreferences.create(
            "secure_user_session",
            androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC),
            context,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        with(sharedPref.edit()) {
            clear()
            apply()
        }
    }
}
