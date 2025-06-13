package com.example.bonded

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LinksAdapter(private val links: List<Pair<String, String?>>) :
    RecyclerView.Adapter<LinksAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val linkText: TextView = itemView.findViewById(R.id.linkText)
        val labelText: TextView = itemView.findViewById(R.id.labelText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_link, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (link, label) = links[position]
        holder.linkText.text = link
        holder.labelText.text = label ?: "No label"

        holder.linkText.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            it.context.startActivity(intent)
        }
    }

    override fun getItemCount() = links.size
}
