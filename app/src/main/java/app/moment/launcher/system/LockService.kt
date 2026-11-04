package app.moment.launcher.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Only used to lock the screen on a double-tap of the home screen (Android has no other way
 * for a launcher to do it without device-admin). It reads no events and no screen content.
 */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private var instance: LockService? = null
        /** True if the screen was locked; false when the service is off (the user must enable it once). */
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
