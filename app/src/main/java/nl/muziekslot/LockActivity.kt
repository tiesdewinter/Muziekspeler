package nl.muziekslot

import android.app.Activity
import android.app.KeyguardManager
import android.content.*
import android.graphics.*
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.*
import android.os.*
import android.view.*
import android.widget.*

class LockActivity : Activity() {
    private lateinit var bg: ImageView
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var seek: SeekBar
    private lateinit var play: TextView
    private var ctrl: MediaController? = null
    private val h = Handler(Looper.getMainLooper())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(m: MediaMetadata?) = showMeta(m)
        override fun onPlaybackStateChanged(s: PlaybackState?) = showState(s)
    }
    private val tick = object : Runnable {
        override fun run() {
            ctrl?.playbackState?.let { s ->
                val pos = if (s.state == PlaybackState.STATE_PLAYING)
                    s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
                else s.position
                seek.progress = pos.toInt()
            }
            h.postDelayed(this, 500)
        }
    }
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = finish()
    }

    private fun label(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; scaleX = 1.5f; scaleY = 1.5f }
        if (Build.VERSION.SDK_INT >= 31) bg.setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP))
        root.addView(bg, -1, -1)
        root.addView(View(this).apply { setBackgroundColor(0x99000000.toInt()) }, -1, -1)

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0x33FFFFFF)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat())
            }
        }
        title = label("", 24f, true); artist = label("", 18f).apply { alpha = 0.7f }
        seek = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { ctrl?.transportControls?.seekTo(progress.toLong()) }
            })
        }
        val prev = label("⏮", 40f).apply { setOnClickListener { ctrl?.transportControls?.skipToPrevious() } }
        play = label("⏸", 56f).apply { setOnClickListener {
            if (ctrl?.playbackState?.state == PlaybackState.STATE_PLAYING) ctrl?.transportControls?.pause()
            else ctrl?.transportControls?.play() } }
        val next = label("⏭", 40f).apply { setOnClickListener { ctrl?.transportControls?.skipToNext() } }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            for (v in listOf(prev, play, next)) addView(v, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val hint = label("Veeg omhoog om te ontgrendelen", 12f).apply { alpha = 0.5f }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(72), dp(32), dp(40))
            addView(art, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(28) })
            addView(title); addView(artist); addView(seek, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
            addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        }
        root.addView(col, -1, -1)
        setContentView(root)

        var downY = 0f
        root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) downY = e.y
            if (e.action == MotionEvent.ACTION_UP && downY - e.y > dp(150)) {
                getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null); finish()
            }
            true
        }
        registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onStart() {
        super.onStart()
        try {
            val list = getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(this, MediaListener::class.java))
            ctrl = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        } catch (e: SecurityException) { Toast.makeText(this, "Geef eerst toegang tot meldingen", Toast.LENGTH_LONG).show() }
        val c = ctrl ?: return finish()
        c.registerCallback(cb); showMeta(c.metadata); showState(c.playbackState); h.post(tick)
    }

    override fun onStop() { ctrl?.unregisterCallback(cb); h.removeCallbacks(tick); super.onStop() }
    override fun onDestroy() { try { unregisterReceiver(unlocked) } catch (_: Exception) {}; super.onDestroy() }

    private fun showMeta(m: MediaMetadata?) {
        title.text = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist.text = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val bmp = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        art.setImageBitmap(bmp); bg.setImageBitmap(bmp)
        seek.max = (m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).toInt().coerceAtLeast(1)
    }

    private fun showState(s: PlaybackState?) { play.text = if (s?.state == PlaybackState.STATE_PLAYING) "⏸" else "▶" }
}
