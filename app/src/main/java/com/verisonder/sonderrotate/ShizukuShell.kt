// SPDX-License-Identifier: GPL-3.0-only
package com.verisonder.sonderrotate

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Makes apps that lock their own orientation follow the tile anyway.
 *
 * `USER_ROTATION` is a request, and an app that declares `screenOrientation` in its manifest is
 * allowed to refuse it. Android 12 added a per-display override for exactly this, meant for large
 * screens and foldables, reachable only as a shell command:
 *
 *     wm set-ignore-orientation-request true
 *
 * Running a shell command needs a shell to run it in, which is what Shizuku provides. It is
 * entirely optional and off by default: everything else in this app works without it, and asking
 * someone to install a second app before they can rotate their home screen would be absurd.
 *
 * `Shizuku.newProcess` is hidden from the public API, so it is reached by reflection. If that ever
 * stops working the failure is contained here and the rest of the app is unaffected.
 */
object ShizukuShell {

    /** Whether Shizuku is installed and running at all. */
    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        if (!Shizuku.pingBinder()) return false
        // Pre-11 Shizuku grants through the old permission system rather than its own.
        if (Shizuku.isPreV11()) return false
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    /**
     * Tells the window manager whether to honour or ignore each app's orientation request.
     *
     * @return true when the command ran and exited cleanly.
     */
    fun setIgnoreOrientationRequest(ignore: Boolean): Boolean {
        if (!hasPermission()) return false
        return runShell("wm set-ignore-orientation-request $ignore")
    }

    private fun runShell(command: String): Boolean = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(
            null, arrayOf("sh", "-c", command), null, null
        ) as Process
        // The command is a one-liner and returns immediately; anything that hangs is a problem
        // worth surfacing rather than waiting on forever.
        process.waitFor() == 0
    }.getOrDefault(false)
}
