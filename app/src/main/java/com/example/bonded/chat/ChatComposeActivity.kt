package com.example.bonded.chat

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.bonded.AppDatabase
import com.example.bonded.MessageEntity
import com.example.bonded.SessionManager
import com.example.bonded.links.LinksComposeActivity
import com.example.bonded.theme.appColors
import com.example.bonded.ui.login.SocketHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import io.socket.client.Socket
import java.text.SimpleDateFormat
import java.util.*

class ChatComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selfUser = intent.getStringExtra("selfUser") ?: ""
        val userName = intent.getStringExtra("userName") ?: ""

        if (!SocketHandler.isInitialized()) {
            SocketHandler.setSocket("https://bonded-server-301t.onrender.com/")
            SocketHandler.establishConnection()
        }

        setContent {
            ChatScreen(selfUser, userName)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(selfUser: String, userName: String) {
    val context = LocalContext.current
    val socket = remember { SocketHandler.getSocket() }
    val db = remember { AppDatabase.getDatabase(context) }
    val messageDao = db.messageDao()
    val colors = appColors()

    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<MessageEntity>() }
    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var labelInput by remember { mutableStateOf("") }
    var showLabelDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        SessionManager.autoLogin(context)
        messageDao.getMessagesBetweenFlow(selfUser, userName).collect { dbMessages ->
            messages.clear()
            messages.addAll(dbMessages)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.card,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { (context as ComponentActivity).finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.radialGradient(colors = listOf(colors.primary, colors.primary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                            color = colors.buttonText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(userName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                    }

                    IconButton(onClick = {
                        val intent = Intent(context, LinksComposeActivity::class.java)
                        intent.putExtra("currentUser", selfUser)
                        intent.putExtra("targetUser", userName)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Link, contentDescription = "Links", tint = colors.primary)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(
                        message = msg,
                        isFromCurrentUser = msg.sender == selfUser,
                        onLongClick = { message ->
                            if (message.content.startsWith("http://") || message.content.startsWith("https://")) {
                                selectedMessage = message
                                labelInput = message.label ?: ""
                                showLabelDialog = true
                            }
                        },
                        colors = colors
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.card,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...", color = colors.hint) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.hint.copy(alpha = 0.3f),
                            cursorColor = colors.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (messageText.isNotBlank()) {
                                sendMessage(socket, userName, selfUser, messageText)
                                insertMessage(context, selfUser, userName, messageText)
                                messageText = ""
                            }
                        }),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                sendMessage(socket, userName, selfUser, messageText)
                                insertMessage(context, selfUser, userName, messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = colors.primary,
                        contentColor = colors.buttonText
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (showLabelDialog && selectedMessage != null) {
            AlertDialog(
                onDismissRequest = { showLabelDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        updateLabel(context, selectedMessage!!.id, labelInput)
                        showLabelDialog = false
                    }) {
                        Text("Save", color = colors.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLabelDialog = false }) {
                        Text("Cancel", color = colors.hint)
                    }
                },
                title = {
                    Text("Label Link", fontWeight = FontWeight.SemiBold, color = colors.text)
                },
                text = {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Enter label") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            focusedLabelColor = colors.primary,
                            cursorColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                containerColor = colors.card,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isFromCurrentUser: Boolean,
    onLongClick: (MessageEntity) -> Unit,
    colors: com.example.bonded.theme.CustomColors
) {
    val isLink = message.content.startsWith("http://") || message.content.startsWith("https://")
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timestamp = timeFormat.format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .let { modifier ->
                    if (isLink) {
                        modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onLongClick(message) }
                        )
                    } else modifier
                }
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromCurrentUser) colors.primary else colors.card
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    color = if (isFromCurrentUser) colors.buttonText else colors.text,
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                )
                message.label?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🏷️ $it",
                        color = if (isFromCurrentUser) colors.buttonText.copy(alpha = 0.8f) else colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timestamp,
                    color = if (isFromCurrentUser) colors.buttonText.copy(alpha = 0.7f) else colors.hint,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private fun sendMessage(socket: Socket, to: String, from: String, messageText: String) {
    val json = JSONObject().apply {
        put("to", to)
        put("message", messageText)
    }
    socket.emit("private_message", json)
    Log.d("COMPOSE_CHAT", "Message sent: $messageText")
}

private fun insertMessage(context: android.content.Context, sender: String, receiver: String, content: String) {
    val dao = AppDatabase.getDatabase(context).messageDao()
    (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
        dao.insertMessage(
            MessageEntity(
                sender = sender,
                receiver = receiver,
                content = content,
                timestamp = System.currentTimeMillis(),
                isSentByCurrentUser = true
            )
        )
    }
}

private fun updateLabel(context: android.content.Context, messageId: Int, label: String) {
    val dao = AppDatabase.getDatabase(context).messageDao()
    (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
        val msg = dao.getMessageById(messageId)
        if (msg != null) {
            dao.updateMessage(msg.copy(label = label.ifBlank { null }))
        }
    }
}
