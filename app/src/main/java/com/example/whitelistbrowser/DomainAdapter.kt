package com.example.whitelistbrowser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whitelistbrowser.databinding.ItemDomainBinding

class DomainAdapter(
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<DomainAdapter.ViewHolder>() {

    private val items = mutableListOf<String>()

    fun submit(domains: List<String>) {
        items.clear()
        items.addAll(domains)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDomainBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemDomainBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(domain: String) {
            binding.tvDomain.text = domain
            binding.btnDelete.setOnClickListener { onDelete(domain) }
        }
    }
}
