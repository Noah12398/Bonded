package com.example.bonded

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class LinkItem {
    data class Header(val title: String) : LinkItem()
    data class Link(val url: String, val label: String?) : LinkItem()
}

class GroupedLinksAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<LinkItem>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_LINK = 1
    }

    fun submitData(groupedData: Map<String, List<Pair<String, String?>>>) {
        items.clear()
        for ((key, value) in groupedData) {
            items.add(LinkItem.Header(key))
            items.addAll(value.map { LinkItem.Link(it.first, it.second) })
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is LinkItem.Header -> TYPE_HEADER
            is LinkItem.Link -> TYPE_LINK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_link, parent, false)
            LinkViewHolder(view)
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LinkItem.Header -> (holder as HeaderViewHolder).bind(item)
            is LinkItem.Link -> (holder as LinkViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerText: TextView = itemView.findViewById(R.id.headerText)
        fun bind(item: LinkItem.Header) {
            headerText.text = item.title
        }
    }

    class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val linkText: TextView = itemView.findViewById(R.id.linkText)
        private val labelText: TextView = itemView.findViewById(R.id.labelText)
        fun bind(item: LinkItem.Link) {
            linkText.text = item.url
            labelText.text = item.label ?: "No label"
            linkText.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                it.context.startActivity(intent)
            }
        }
    }
}
