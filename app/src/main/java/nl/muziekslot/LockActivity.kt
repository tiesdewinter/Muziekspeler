package nl.muziekslot

import android.app.Activity
import android.app.KeyguardManager
import android.content.*
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
    private lateinit var play: ImageView
    private lateinit var col: LinearLayout
    private lateinit var row: LinearLayout
    private lateinit var hint: TextView
    private lateinit var shade: View
    private var dim = false
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
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun icon(res: Int, box: Int, pad: Int, filled: Boolean) = ImageView(this).apply {
        setImageResource(res)
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        layoutParams = LinearLayout.LayoutParams(dp(box), dp(box)).apply { marginStart = dp(10); marginEnd = dp(10) }
        fun oval(c: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
        if (filled) {
            setColorFilter(0xFF1B1B1F.toInt())
            background = RippleDrawable(ColorStateList.valueOf(0x33000000), oval(Color.WHITE), oval(Color.WHITE))
        } else {
            setColorFilter(Color.WHITE)
            background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), null, oval(Color.WHITE))
        }
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
        shade = View(this).apply { setBackgroundColor(0x99000000.toInt()) }
        root.addView(shade, -1, -1)

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0x33FFFFFF)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat())
            }
        }
        title = label("", 24f, true); artist = label("", 18f).apply { alpha = 0.7f }
        seek = SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(Color.WHITE)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(0x55FFFFFF)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { ctrl?.transportControls?.seekTo(progress.toLong()) }
            })
        }
        val prev = icon(R.drawable.ic_prev, 64, 16, false).apply { setOnClickListener { ctrl?.transportControls?.skipToPrevious() } }
        play = icon(R.drawable.ic_pause, 80, 20, true).apply { setOnClickListener {
            if (ctrl?.playbackState?.state == PlaybackState.STATE_PLAYING) ctrl?.transportControls?.pause()
            else ctrl?.transportControls?.play() } }
        val next = icon(R.drawable.ic_next, 64, 16, false).apply { setOnClickListener { ctrl?.transportControls?.skipToNext() } }
        row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            for (v in listOf(prev, play, next)) addView(v)
        }
        hint = label("Veeg omhoog om te ontgrendelen", 12f).apply { alpha = 0.5f }

        col = LinearLayout(this).apply {
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
        c.registerCallback(cb); showMeta(c.metadata); showState(c.playbackState); h.post(tick); resetDim()
    }

    override fun onStop() { ctrl?.unregisterCallback(cb); h.removeCallbacks(tick); h.removeCallbacks(goDim); h.removeCallbacks(stop); super.onStop() }
    override fun onDestroy() { try { unregisterReceiver(unlocked) } catch (_: Exception) {}; super.onDestroy() }

    private fun showMeta(m: MediaMetadata?) {
        title.text = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist.text = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val bmp = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        art.setImageBitmap(bmp); bg.setImageBitmap(bmp)
        seek.max = (m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).toInt().coerceAtLeast(1)
    }

    private fun showState(s: PlaybackState?) {
        val p = s?.state == PlaybackState.STATE_PLAYING
        play.setImageResource(if (p) R.drawable.ic_pause else R.drawable.ic_play)
        h.removeCallbacks(stop)
        if (!p) h.postDelayed(stop, 60_000)
    }

    private val goDim = Runnable { setDim(true) }
    private val stop = Runnable { finish() }
    private fun resetDim() { h.removeCallbacks(goDim); h.postDelayed(goDim, 30_000) }

    private fun setDim(d: Boolean) {
        dim = d
        val a = window.attributes
        a.screenBrightness = if (d) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = a
        val vis = if (d) View.GONE else View.VISIBLE
        bg.visibility = vis; shade.visibility = vis; seek.visibility = vis; row.visibility = vis; hint.visibility = vis
        val lp = art.layoutParams as LinearLayout.LayoutParams
        if (d) { lp.width = dp(160); lp.height = dp(160); lp.weight = 0f; lp.gravity = Gravity.CENTER_HORIZONTAL }
        else { lp.width = -1; lp.height = 0; lp.weight = 1f; lp.gravity = -1 }
        art.layoutParams = lp
        col.gravity = if (d) Gravity.CENTER else Gravity.TOP
        title.alpha = if (d) 0.6f else 1f
        artist.alpha = if (d) 0.4f else 0.7f
        art.alpha = if (d) 0.6f else 1f
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val was = dim
            setDim(false); resetDim()
            if (was) return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
