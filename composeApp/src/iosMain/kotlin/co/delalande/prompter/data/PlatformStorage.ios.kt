package co.delalande.prompter.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual class PlatformStorage {
    private val fileManager = NSFileManager.defaultManager
    private val documentsDir: String =
        fileManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, false, null)
            ?.path ?: ""

    actual fun readFile(name: String): String? {
        val path = "$documentsDir/$name"
        return if (fileManager.fileExistsAtPath(path)) {
            NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        } else null
    }

    actual fun writeFile(name: String, content: String) {
        val path = "$documentsDir/$name"
        @Suppress("CAST_NEVER_SUCCEEDS")
        (content as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
    }
}
