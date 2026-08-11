// SPDX-License-Identifier: GPL-3.0-only
package com.verisonder.sonderrotate

import android.content.Context

/** The single stored option. Off by default: Shizuku is an extra, not a requirement. */
object Preferences {
    private const val FILE = "sonderrotate"
    private const val KEY_FORCE_APPS = "force_apps"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun forceApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FORCE_APPS, false)

    fun setForceApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCE_APPS, value).apply()
    }
}
