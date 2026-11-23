package nl.muziekslot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad); gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(TextView(this).apply { text = "MuziekSlot"; textSize = 28f })
        col.addView(TextView(this).apply { text = "Zet beide rechten aan. Daarna verschijnt bij het aanzetten van je vergrendelscherm automatisch de muziekspeler op volledig scherm." ; setPadding(0, pad / 2, 0, pad / 2) })
        status = TextView(this).apply { setPadding(0, 0, 0, pad / 2) }
        col.addView(status)
        fun btn(t: String, a: () -> Unit) = col.addView(Button(this).apply { text = t; setOnClickListener { a() } })
        btn("1. Toegang tot meldingen") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        btn("2. Weergave boven andere apps") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        btn("Test nu") { startActivity(Intent(this, LockActivity::class.java)) }
        setContentView(col)
    }

    override fun onResume() {
        super.onResume()
        val n = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        val o = Settings.canDrawOverlays(this)
        status.text = "Meldingen: ${if (n) "✅" else "❌"}   Boven andere apps: ${if (o) "✅" else "❌"}"
    }
}
