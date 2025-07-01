package com.example.bonded

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.jsoup.Jsoup

class MessageAdapter(
    private var messages: MutableList<Message>,
    private val onLongClick: (position: Int) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val linkPreviewContainer: View = itemView.findViewById(R.id.linkPreviewContainer)
        val linkTitle: TextView = itemView.findViewById(R.id.linkTitle)
        val linkImage: ImageView = itemView.findViewById(R.id.linkImage)
        val linkUrl: TextView = itemView.findViewById(R.id.linkUrl)
        val linkLabel: TextView = itemView.findViewById(R.id.linkLabel)
    }

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSentByCurrentUser) 1 else 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == 1) R.layout.sentitem else R.layout.receiveitem
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    private fun isLink(text: String): Boolean {
        val urlRegex = "(https?://\\S+)|(www\\.\\S+)".toRegex()
        return urlRegex.containsMatchIn(text)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val content = message.content

        holder.messageText.text = content

        if (!message.label.isNullOrEmpty()) {
            holder.linkLabel.text = message.label
            holder.linkLabel.visibility = View.VISIBLE
        } else {
            holder.linkLabel.visibility = View.GONE
        }

        if (isLink(content)) {
            holder.messageText.setOnLongClickListener {
                onLongClick(position)
                true
            }
        } else {
            holder.messageText.setOnLongClickListener(null)
        }

        if (content.startsWith("http://") || content.startsWith("https://")) {
            holder.linkPreviewContainer.visibility = View.VISIBLE
            holder.linkUrl.text = content

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val doc = Jsoup.connect(content).get()
                    val title = doc.title()
                    val imageUrl = doc.select("meta[property=og:image]").attr("content")

                    withContext(Dispatchers.Main) {
                        holder.linkTitle.text = title
                        if (imageUrl.isNotEmpty()) {
                        //    Picasso.get().load(imageUrl).into(holder.linkImage)
                        } else {
                            holder.linkImage.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        holder.linkPreviewContainer.visibility = View.GONE
                    }
                }
            }
        } else {
            holder.linkPreviewContainer.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = messages.size
}
