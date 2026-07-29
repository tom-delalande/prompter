package com.teleprompter.data

expect class PlatformStorage {
    fun readFile(name: String): String?
    fun writeFile(name: String, content: String)
}
