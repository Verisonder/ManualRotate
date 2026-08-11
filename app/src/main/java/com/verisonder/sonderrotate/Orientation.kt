// SPDX-License-Identifier: GPL-3.0-only
package com.verisonder.sonderrotate

import android.content.Context
import android.provider.Settings
import android.view.Surface

/**
 * Turning the screen without turning the phone.
 *
 * Android already has everything needed for this and exposes it as two system settings, so no
 * accessibility service and no root are involved. `ACCELEROMETER_ROTATION` is the auto-rotate
 * switch, and `USER_ROTATION` is the direction the display takes when auto-rotate is off. Setting
 * one and then the other is the whole trick.
 *
 * Writing them needs `WRITE_SETTINGS`, which is a special permission the user grants once from
 * system settings rather than a runtime prompt - hence [canWrite] and the screen that asks.
 *
 * **The limit worth knowing:** an app that declares a fixed orientation in its own manifest wins.
 * A game locked to landscape stays landscape whatever `USER_ROTATION` says. Overriding that needs
 * a shell command and so needs Shizuku; see [ShizukuShell].
 */
enum class Orientation(val rotation: Int) {
    PORTRAIT(Surface.ROTATION_0),
    LANDSCAPE(Surface.ROTATION_90);

    fun next() = if (this == PORTRAIT) LANDSCAPE else PORTRAIT
}

object OrientationController {

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /** Whether the system is currently free to rotate with the phone. */
    fun isAutoRotate(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1

    /**
     * What the display is locked to right now. Reported as portrait when auto-rotate is on, since
     * in that case nothing is locked and portrait is the sensible thing to move away from first.
     */
    fun current(context: Context): Orientation {
        if (isAutoRotate(context)) return Orientation.PORTRAIT
        val rotation = Settings.System.getInt(
            context.contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0
        )
        return if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            Orientation.LANDSCAPE
        } else {
            Orientation.PORTRAIT
        }
    }

    /**
     * Locks the display to [orientation].
     *
     * Auto-rotate is switched off first and deliberately: with it on the system overrides
     * `USER_ROTATION` the moment the sensor disagrees, so setting the direction alone would appear
     * to work and then undo itself as soon as the phone moved.
     */
    fun apply(context: Context, orientation: Orientation): Boolean {
        if (!canWrite(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0
            )
            Settings.System.putInt(
                context.contentResolver, Settings.System.USER_ROTATION, orientation.rotation
            )
        }.isSuccess
    }

    /** Hands control back to the sensor. */
    fun releaseToAuto(context: Context): Boolean {
        if (!canWrite(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1
            )
        }.isSuccess
    }
}
