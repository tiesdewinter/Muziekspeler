package nl.muziekslot

import android.app.KeyguardManager
import android.content.*
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class MediaListener : NotificationListenerService() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (locked && isPlaying()) {
                startActivity(Intent(c, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun isPlaying(): Boolean = try {
        getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(this, MediaListener::class.java))
            .any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    } catch (e: SecurityException) { false }

    override fun onListenerConnected() {
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onListenerDisconnected() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
