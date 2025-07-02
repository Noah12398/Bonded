// Improved LinksComposeActivity.kt
package com.example.bonded.links

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bonded.AppDatabase
import com.example.bonded.theme.CustomColors
import com.example.bonded.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinksComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUser = intent.getStringExtra("currentUser") ?: ""
        val targetUser = intent.getStringExtra("targetUser") ?: ""

        setContent {
            LinksScreen(currentUser, targetUser)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinksScreen(currentUser: String, targetUser: String) {
    val context = LocalContext.current
    val colors = appColors()
    val tabs = listOf("By Domain", "By Label")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.card,
                shadowElevation = 8.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { (context as ComponentActivity).finish() }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primary)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(Icons.Default.Link, contentDescription = "Links", tint = colors.primary, modifier = Modifier.size(28.dp))

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shared Links", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.text)
                            Text("with $targetUser", fontSize = 14.sp, color = colors.primary)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = pagerState.currentPage == index
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) colors.primary else Color.Transparent),
                                border = if (!isSelected) CardDefaults.outlinedCardBorder() else null,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (index == 0) Icons.Default.Language else Icons.Default.Label,
                                        contentDescription = null,
                                        tint = if (isSelected) colors.buttonText else colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        title,
                                        color = if (isSelected) colors.buttonText else colors.primary,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(top = 8.dp)) { page ->
                when (page) {
                    0 -> LinksByDomain(currentUser, targetUser, colors)
                    1 -> LinksByLabel(currentUser, targetUser, colors)
                }
            }
        }
    }
}

fun extractUrl(text: String): String? {
    val urlRegex = Regex("(https?://\\S+)|(www\\.\\S+)")
    return urlRegex.find(text)?.value
}

fun extractDomain(url: String): String {
    return try {
        val host = Uri.parse(url).host ?: return "Other"
        val cleaned = host.removePrefix("www.")
        val parts = cleaned.split(".")
        if (parts.size >= 2) parts[parts.size - 2].replaceFirstChar { it.uppercase() } else parts.first().replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        "Other"
    }
}

@Composable
fun LinksByDomain(currentUser: String, targetUser: String, colors: CustomColors) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).messageDao() }

    var groupedLinks by remember { mutableStateOf<Map<String, List<Pair<String, String?>>>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        isLoading = true
        val allMessages = withContext(Dispatchers.IO) {
            dao.getMessagesBetween(currentUser, targetUser)
                .filter { extractUrl(it.content) != null }
                .map { extractUrl(it.content)!! to it.label }
        }

        val filtered = allMessages.filter { it.first.contains(searchQuery, ignoreCase = true) }
        groupedLinks = filtered.groupBy { extractDomain(it.first) }
        isLoading = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search links by domain...", color = colors.textSecondary) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.primary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.3f),
                    cursorColor = colors.primary,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (groupedLinks.isEmpty()) {
                EmptyState("No links found", "Share some links to see them organized by domain", colors)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedLinks.forEach { (group, links) ->
                        item {
                            DomainHeader(group, links.size, colors)
                        }
                        items(links) { (url, label) ->
                            LinkItem(url, label, context, colors)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LinksByLabel(currentUser: String, targetUser: String, colors: CustomColors) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).messageDao() }

    var groupedLinks by remember { mutableStateOf<Map<String, List<Pair<String, String?>>>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        isLoading = true
        val allMessages = withContext(Dispatchers.IO) {
            dao.getMessagesBetween(currentUser, targetUser)
                .filter { extractUrl(it.content) != null }
                .map { extractUrl(it.content)!! to it.label }
        }

        val filtered = if (searchQuery.isBlank()) {
            allMessages
        } else {
            allMessages.filter { it.second?.contains(searchQuery, ignoreCase = true) == true }
        }
        groupedLinks = filtered.groupBy { it.second ?: "No Label" }
        isLoading = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search links by label...", color = colors.textSecondary) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.primary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.3f),
                    cursorColor = colors.primary,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (groupedLinks.isEmpty()) {
                EmptyState("No labeled links found", "Long press on links in chat to add labels", colors)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedLinks.forEach { (label, links) ->
                        item {
                            LabelHeader(label, links.size, colors)
                        }
                        items(links) { (url, label) ->
                            LinkItem(url, label, context, colors)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DomainHeader(domain: String, count: Int, colors: CustomColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = domain,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
            Text(
                text = "$count ${if (count == 1) "link" else "links"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary
            )
        }
    }
}

@Composable
fun LabelHeader(label: String, count: Int, colors: CustomColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Label,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (label == "No Label") "Unlabeled" else label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
            Text(
                text = "$count ${if (count == 1) "link" else "links"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary
            )
        }
    }
}

@Composable
fun LinkItem(url: String, label: String?, context: Context, colors: CustomColors) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Handle the case where URL might be malformed or no app can handle it
                }
            }
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(colors.primary, colors.primary.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = colors.buttonText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!label.isNullOrBlank() && label != "No Label") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏷️",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, colors: CustomColors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.text
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}