package com.example.whitelistbrowser

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whitelistbrowser.databinding.ActivityWhitelistBinding

class WhitelistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhitelistBinding
    private lateinit var store: WhitelistStore
    private lateinit var adapter: DomainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = WhitelistStore(this)

        adapter = DomainAdapter(
            onDelete = { domain ->
                store.remove(domain)
                refresh()
                Toast.makeText(this, getString(R.string.toast_removed, domain), Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvDomains.layoutManager = LinearLayoutManager(this)
        binding.rvDomains.adapter = adapter

        binding.btnAdd.setOnClickListener {
            val input = binding.etDomain.text.toString().trim()
            if (input.isEmpty()) return@setOnClickListener
            val normalized = WhitelistStore.normalize(input)
            if (normalized.isEmpty()) {
                Toast.makeText(this, R.string.toast_invalid_domain, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (store.add(normalized)) {
                binding.etDomain.text?.clear()
                refresh()
                Toast.makeText(this, getString(R.string.toast_added, normalized), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
            }
        }

        refresh()
    }

    private fun refresh() {
        adapter.submit(store.getAll().sorted())
    }
}
