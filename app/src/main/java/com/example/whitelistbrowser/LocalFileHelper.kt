package com.example.whitelistbrowser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * 本地文件辅助类：把用户选择的 HTML 文件复制到应用私有目录后再加载，
 * 避免 WebView 直接读取外部存储路径带来的权限与安全问题。
 */
object LocalFileHelper {

    /** 将 uri 指向的文件复制到 filesDir/local/ 下，返回目标文件；失败返回 null */
    fun copyToLocal(context: Context, uri: Uri): File? {
        return try {
            val name = queryDisplayName(context, uri) ?: "page.html"
            val safeName = name.ifBlank { "page.html" }
            val dir = File(context.filesDir, "local").apply { mkdirs() }
            val target = File(dir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            target
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
