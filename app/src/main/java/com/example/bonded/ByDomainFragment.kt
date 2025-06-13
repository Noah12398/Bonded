package com.example.bonded

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ByDomainFragment : Fragment() {

    private lateinit var adapter: GroupedLinksAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private var allLinks = listOf<Pair<String, String?>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_links_list, container, false)
        recyclerView = view.findViewById(R.id.linksRecyclerView)
        searchView = view.findViewById(R.id.searchView)

        adapter = GroupedLinksAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        fetchLinks { links ->
            allLinks = links
            adapter.submitData(groupByDomain(links))
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String?): Boolean {
                val filtered = allLinks.filter {
                    it.first.contains(query.orEmpty(), ignoreCase = true)
                }
                adapter.submitData(groupByDomain(filtered))
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean = false
        })

        return view
    }

    
   private fun groupByDomain(links: List<Pair<String, String?>>): Map<String, List<Pair<String, String?>>> {
       return links.groupBy {
           try {
               val host = Uri.parse(it.first).host ?: "Other"
               val cleaned = host.removePrefix("www.")

               // Extract second-level domain (e.g., "google" from "google.com")
               val parts = cleaned.split(".")
               val name = if (parts.size >= 2) {
                   parts[parts.size - 2] // second-level domain (e.g., "google")
               } else {
                   parts.first()
               }

               name.replaceFirstChar { it.uppercaseChar() }
           } catch (_: Exception) {
               "Other"
           }
       }
   }


              private fun extractUrl(text: String): String? {
                  val urlRegex = Regex("(https?://\\S+)|(www\\.\\S+)")
                  return urlRegex.find(text)?.value
              }
    private fun fetchLinks(callback: (List<Pair<String, String?>>) -> Unit) {
        val db = AppDatabase.getDatabase(requireContext())
        val dao = db.messageDao()
        val currentUser = activity?.intent?.getStringExtra("currentUser") ?: return
        val targetUser = activity?.intent?.getStringExtra("targetUser") ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val messages = dao.getMessagesBetween(currentUser, targetUser)
.filter { extractUrl(it.content) != null }
.map { extractUrl(it.content)!! to it.label }
            withContext(Dispatchers.Main) {
                callback(messages)
            }
        }
    }
}
