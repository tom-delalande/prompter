package com.teleprompter.data

import android.content.Context
import java.io.File

actual class PlatformStorage(private val context: Context) {
    actual fun readFile(name: String): String? {
        val file = File(context.filesDir, name)
        return if (file.exists()) file.readText() else null
    }

    actual fun writeFile(name: String, content: String) {
        File(context.filesDir, name).writeText(content)
    }
}
