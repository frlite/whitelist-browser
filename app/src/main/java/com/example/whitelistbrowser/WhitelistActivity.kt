package com.example.whitelistbrowser

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whitelistbrowser.databinding.ActivityWhitelistBinding

/**
 * 白名单管理页面：查看服务端下发的白名单列表（只读），添加需通过服务端管理端。
 */
class WhitelistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhitelistBinding
    private lateinit var syncManager: WhitelistSyncManager
    private lateinit var adapter: DomainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        syncManager = WhitelistSyncManager(this)

        adapter = DomainAdapter(onDelete = { /* 服务端模式下不允许本地删除 */ })
        binding.rvDomains.layoutManager = LinearLayoutManager(this)
        binding.rvDomains.adapter = adapter

        binding.btnAdd.setOnClickListener {
            Toast.makeText(this, "请通过管理端添加白名单", Toast.LENGTH_SHORT).show()
        }

        refresh()
    }

    private fun refresh() {
        val domains = syncManager.getCachedWhitelist()
        adapter.submit(domains.sorted())
    }
}
