package com.semmyx.ledger

import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Copy out before Gradle uninstalls the test app and deletes its external files. */
object NativeScreenshots {
    fun save(bitmap: ImageBitmap, name: String) {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val file = File(app.getExternalFilesDir(null),"screenshots/$name.png").also { it.parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        shell("mkdir -p /data/local/tmp/tg-ledger-screenshots")
        shell("cp ${file.absolutePath} /data/local/tmp/tg-ledger-screenshots/$name.png")
    }
}
