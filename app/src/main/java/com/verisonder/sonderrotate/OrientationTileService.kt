// SPDX-License-Identifier: GPL-3.0-only
package com.verisonder.sonderrotate

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The tile. Tapping it swaps the screen between portrait and landscape.
 *
 * The tile is the app; the activity exists to grant the permission and set the one option. So a
 * tap when the permission is missing opens that screen rather than failing quietly, which is the
 * only useful thing it could do.
 */
class OrientationTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (!OrientationController.canWrite(this)) {
            openApp()
            return
        }
        val next = OrientationController.current(this).next()
        OrientationController.apply(this, next)
        // Only when the user asked for it, and only if Shizuku is actually there. A failure is
        // not worth reporting: the rotation itself has already happened, and the override only
        // affects apps that would have refused it.
        if (Preferences.forceApps(this)) {
            ShizukuShell.setIgnoreOrientationRequest(true)
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val locked = !OrientationController.isAutoRotate(this)
        val current = OrientationController.current(this)

        tile.state = if (OrientationController.canWrite(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(
            this,
            if (locked && current == Orientation.LANDSCAPE) R.drawable.ic_landscape else R.drawable.ic_portrait
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !OrientationController.canWrite(this) -> getString(R.string.tile_needs_permission)
                !locked -> getString(R.string.orientation_auto)
                current == Orientation.LANDSCAPE -> getString(R.string.orientation_landscape)
                else -> getString(R.string.orientation_portrait)
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // startActivityAndCollapse(Intent) was deprecated in 34 and throws there; the
            // PendingIntent form is the replacement.
            val pending = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
