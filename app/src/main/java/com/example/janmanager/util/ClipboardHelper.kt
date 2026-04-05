package com.example.janmanager.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    
    fun copyToClipboard(context: Context, text: String, label: String = "copied_text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun readFromClipboard(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClip != null) {
            val item = clipboard.primaryClip!!.getItemAt(0)
            return item.text?.toString()
        }
        return null
    }
}
