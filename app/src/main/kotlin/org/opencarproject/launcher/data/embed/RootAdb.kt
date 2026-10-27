package org.opencarproject.launcher.data.embed

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/**
 * adb client to the device's own adbd over 127.0.0.1. On head units like the K706 adbd runs as
 * root and listens on TCP, which gives the launcher a root shell without `su`. The first
 * connection shows the system "Allow USB debugging?" prompt for the launcher's key.
 */
object RootAdb {

    // ponytail: port fixed to the K706's 9876; make it a setting when another unit differs.
    private const val PORT = 9876

    private var dadb: Dadb? = null

    /** Connected client whose shell runs as root; throws if adb is unreachable or not root. */
    @Synchronized
    fun connect(context: Context): Dadb {
        dadb?.let { return it }
        val private = File(context.filesDir, "adbkey")
        val public = File(context.filesDir, "adbkey.pub")
        if (!private.exists() || !public.exists()) AdbKeyPair.generate(private, public)
        val client = Dadb.create("127.0.0.1", PORT, AdbKeyPair.read(private, public))
        try {
            check(client.shell("id -u").output.trim() == "0") { "adb shell is not root" }
        } catch (e: Exception) {
            client.close()
            throw e
        }
        return client.also { dadb = it }
    }

    /** Drops the cached client after an I/O error so the next [connect] starts over. */
    @Synchronized
    fun reset() {
        runCatching { dadb?.close() }
        dadb = null
    }
}
