package com.example.bonded.ui.home

import android.content.Intent
import android.os.Bundle
import com.example.bonded.chat.ChatActivity

import com.example.bonded.ui.login.LoginActivity
import com.example.bonded.ui.login.SocketHandler

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable

class Homescreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val username = intent.getStringExtra("username") ?: ""

        if (!SocketHandler.isInitialized()) {
            SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
            SocketHandler.establishConnection()
        }

        setContent {
            HomeScreenUI(username)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenUI(
    username: String,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme // or your appColors()

    var searchQuery by remember { mutableStateOf("") }
    val users by viewModel.users.collectAsState()

    // Initialize only once
    LaunchedEffect(Unit) {
        viewModel.initialize(username, context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    IconButton(onClick = {
                        viewModel.logout(context)
                        val intent = Intent(context, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.onSearchChange(it)
                },
                label = { Text("Search users") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(users) { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                val intent = Intent(context, ChatActivity::class.java)
                                intent.putExtra("userName", user)
                                intent.putExtra("selfUser", username)
                                context.startActivity(intent)
                            },
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
                    ) {
                        Text(
                            text = user,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
