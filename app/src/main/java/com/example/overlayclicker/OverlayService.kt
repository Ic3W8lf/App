package com.example.overlayclicker

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val query = EditText(this).apply { hint = "Exact button text"; isSingleLine = true }
        val click = Button(this).apply {
            text = "Find & click"
            setOnClickListener {
                val service = ClickAccessibilityService.instance
                val target = query.text.toString().trim()
                if (target.isBlank()) Toast.makeText(this@OverlayService, "Enter target text", Toast.LENGTH_SHORT).show()
                else if (service == null) Toast.makeText(this@OverlayService, "Enable accessibility service first", Toast.LENGTH_SHORT).show()
                else service.clickText(target)
            }
        }
        val close = Button(this).apply { text = "Close panel"; setOnClickListener { stopSelf() } }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            addView(query); addView(click); addView(close)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 20; y = 140 }
        wm.addView(panel, params)
    }
    override fun onDestroy() { panel?.let { wm.removeView(it) }; panel = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
