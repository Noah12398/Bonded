package com.example.bonded
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class Homescreen : AppCompatActivity() {
    private lateinit var chatList: RecyclerView
    private val users = listOf("Alice", "Bob", "Charlie")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        chatList = findViewById(R.id.chatList)
        chatList.layoutManager = LinearLayoutManager(this)
        chatList.adapter = UserAdapter(users) { name ->
            // Start chat activity with this user
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userName", name)
            startActivity(intent)
        }
    }
}


class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val name = intent.getStringExtra("userName")
        // Load and show chat with that user
        title = "Chat with $name"
    }
}
class UserAdapter(
    private val userList: List<String>,
    private val onUserClick: (String) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNameTextView: TextView = itemView.findViewById(R.id.userName)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onUserClick(userList[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.userNameTextView.text = userList[position]
    }

    override fun getItemCount(): Int = userList.size
}
