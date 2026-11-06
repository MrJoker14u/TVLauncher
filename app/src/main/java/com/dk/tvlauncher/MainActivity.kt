package com.dk.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.util.Calendar

class MainActivity : Activity() {
    private lateinit var sp: SharedPreferences
    private val REQ = 11

    private class App(val pkg: String, val label: String, val intent: Intent, val icon: Drawable)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun parse(h: String?, d: Int): Int = try { Color.parseColor(h) } catch (e: Exception) { d }
    private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("tvl", MODE_PRIVATE)
        buildUi()
    }

    override fun onBackPressed() {}

    // ---------- UI ----------
    private fun buildUi() {
        val accent = parse(sp.getString("accent", "#FFC107"), 0xFFFFC107.toInt())
        val card = parse(sp.getString("card", "#14110F"), 0xFF14110F.toInt())
        val fill = (card and 0xFFFFFF) or 0xCC000000.toInt()

        val root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0B1030.toInt(), 0xFFB3341C.toInt(), 0xFFFF9A1F.toInt(), 0xFF07080E.toInt()))
        val wp = File(filesDir, "wp.jpg")
        if (wp.exists()) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(BitmapFactory.decodeFile(wp.path))
            root.addView(iv, FrameLayout.LayoutParams(-1, -1))
        }
        val shade = View(this)
        shade.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x55000000, 0x00000000, 0xAA000000.toInt(), 0xF0000000.toInt()))
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(40), dp(24), dp(40), dp(20))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        // header: greeting (left), clock + buttons (right)
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greet = when {
            hr < 5 -> "Good Night"; hr < 12 -> "Good Morning"; hr < 17 -> "Good Afternoon"
            hr < 21 -> "Good Evening"; else -> "Good Night"
        }
        val name = sp.getString("name", "Deep") ?: ""
        val sb = SpannableStringBuilder("$greet, $name")
        sb.setSpan(ForegroundColorSpan(accent), greet.length + 2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val g = TextView(this); g.text = sb; g.textSize = 30f; g.setTextColor(Color.WHITE)
        val sub = TextView(this); sub.text = "Relax • Watch • Explore"; sub.textSize = 14f; sub.setTextColor(0xCCFFFFFF.toInt())
        val gbox = LinearLayout(this); gbox.orientation = LinearLayout.VERTICAL; gbox.gravity = Gravity.CENTER_VERTICAL
        gbox.addView(g); gbox.addView(sub)
        header.addView(gbox, LinearLayout.LayoutParams(0, -2, 1f))

        val rb = LinearLayout(this); rb.orientation = LinearLayout.VERTICAL; rb.gravity = Gravity.END
        val clk = TextClock(this)
        clk.format12Hour = "hh:mm a"; clk.format24Hour = "hh:mm a"; clk.textSize = 26f; clk.setTextColor(Color.WHITE); clk.gravity = Gravity.END
        val dt = TextClock(this)
        dt.format12Hour = "EEE, d MMM yyyy"; dt.format24Hour = "EEE, d MMM yyyy"; dt.textSize = 13f
        dt.setTextColor(0xDDFFFFFF.toInt()); dt.gravity = Gravity.END
        val btns = LinearLayout(this); btns.orientation = LinearLayout.HORIZONTAL; btns.gravity = Gravity.END
        btns.setPadding(0, dp(8), 0, 0)
        btns.addView(pill("Launcher", fill, accent) { launcherSettings() })
        btns.addView(pill("TV Settings", fill, accent) { openTvSettings() })
        rb.addView(clk); rb.addView(dt); rb.addView(btns)
        header.addView(rb, LinearLayout.LayoutParams(-2, -2))
        col.addView(header)

        // app grid
        val all = apps()
        val favs = favs(all)
        val cells = ArrayList<View>()
        for (a in favs) cells.add(tile(a.label, a.icon, fill, accent, { launch(a.intent) }, { removeFav(a.pkg) }))
        cells.add(tile("Add App", null, fill, accent, { addAppDialog(all) }, null))
        val gridBox = LinearLayout(this); gridBox.orientation = LinearLayout.VERTICAL
        for (row in cells.chunked(5)) {
            val r = LinearLayout(this); r.orientation = LinearLayout.HORIZONTAL
            for (c in row) r.addView(c)
            repeat(5 - row.size) {
                r.addView(View(this), LinearLayout.LayoutParams(0, dp(100), 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            }
            gridBox.addView(r)
        }
        val sv = ScrollView(this); sv.isVerticalScrollBarEnabled = false; sv.addView(gridBox)
        col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })

        // bottom: inputs + storage
        val bottom = LinearLayout(this); bottom.orientation = LinearLayout.HORIZONTAL
        bottom.addView(inputsCard(fill, accent), LinearLayout.LayoutParams(0, -2, 2.6f).apply { rightMargin = dp(12) })
        bottom.addView(storageCard(fill, accent), LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(bottom, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun focusBg(fill: Int, accent: Int, r: Int): StateListDrawable {
        fun gd(stroke: Int, w: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = r.toFloat(); setStroke(w, stroke) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), gd(accent, dp(3)))
            addState(intArrayOf(), gd(0x33FFFFFF, dp(1)))
        }
    }

    private fun focusable(v: View) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnFocusChangeListener { x, f ->
            val s = if (f) 1.07f else 1f
            x.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    private fun box(fill: Int, accent: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(14).toFloat()
        setStroke(dp(1), (accent and 0xFFFFFF) or 0x88000000.toInt())
    }

    private fun pill(text: String, fill: Int, accent: Int, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text; t.textSize = 15f; t.setTextColor(Color.WHITE)
        t.setPadding(dp(16), dp(10), dp(16), dp(10))
        t.background = focusBg(fill, accent, dp(10))
        focusable(t)
        t.setOnClickListener { onClick() }
        t.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) }
        return t
    }

    private fun tile(label: String, icon: Drawable?, fill: Int, accent: Int, onClick: () -> Unit, onLong: (() -> Unit)?): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL; t.gravity = Gravity.CENTER
        t.setPadding(dp(6), dp(6), dp(6), dp(6))
        t.background = focusBg(fill, accent, dp(14))
        focusable(t)
        if (icon != null) {
            val iv = ImageView(this)
            iv.setImageDrawable(icon); iv.adjustViewBounds = true; iv.scaleType = ImageView.ScaleType.FIT_CENTER
            t.addView(iv, LinearLayout.LayoutParams(-2, dp(54)))
        } else {
            val p = TextView(this); p.text = "+"; p.textSize = 34f; p.setTextColor(Color.WHITE); p.gravity = Gravity.CENTER
            t.addView(p)
        }
        val l = TextView(this)
        l.text = label; l.textSize = 13f; l.setTextColor(Color.WHITE); l.setSingleLine()
        l.ellipsize = TextUtils.TruncateAt.END; l.gravity = Gravity.CENTER
        t.addView(l)
        t.setOnClickListener { onClick() }
        if (onLong != null) t.setOnLongClickListener { onLong(); true }
        t.layoutParams = LinearLayout.LayoutParams(0, dp(100), 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
        return t
    }

    private fun inputsCard(fill: Int, accent: Int): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(10), dp(14), dp(10))
        c.background = box(fill, accent)
        val t = TextView(this); t.text = "INPUTS"; t.textSize = 11f; t.setTextColor(0xAAFFFFFF.toInt())
        c.addView(t)
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        val list = inputs()
        if (list.isEmpty()) {
            val n = TextView(this); n.text = "No TV inputs found"; n.setTextColor(0xCCFFFFFF.toInt()); row.addView(n)
        }
        for ((label, uri) in list) {
            row.addView(pill(label, 0x22FFFFFF, accent) {
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { toast("Can't open $label") }
            })
        }
        val hs = HorizontalScrollView(this); hs.isHorizontalScrollBarEnabled = false; hs.addView(row)
        c.addView(hs)
        return c
    }

    private fun storageCard(fill: Int, accent: Int): View {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        val used = total - free
        val pct = if (total > 0) (used * 100 / total).toInt() else 0
        fun gb(x: Long) = String.format("%.1f GB", x / 1073741824.0)
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(10), dp(14), dp(10))
        c.background = box(fill, accent)
        val t = TextView(this); t.text = "Storage  $pct%"; t.textSize = 15f; t.setTextColor(Color.WHITE)
        val s = TextView(this); s.text = "${gb(used)} used of ${gb(total)}"; s.textSize = 12f; s.setTextColor(0xCCFFFFFF.toInt())
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100; pb.progress = pct; pb.progressTintList = ColorStateList.valueOf(accent)
        c.addView(t); c.addView(s); c.addView(pb)
        return c
    }

    // ---------- apps ----------
    private fun apps(): List<App> {
        val pm = packageManager
        val seen = HashSet<String>()
        val out = ArrayList<App>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val p = ri.activityInfo.packageName
                if (p == packageName || !seen.add(p)) continue
                val i = pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p) ?: continue
                val icon: Drawable = try { pm.getApplicationBanner(ri.activityInfo.applicationInfo) } catch (e: Exception) { null } ?: ri.loadIcon(pm)
                out.add(App(p, ri.loadLabel(pm).toString(), i, icon))
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun favs(all: List<App>): List<App> {
        val s = sp.getString("favs", null) ?: run {
            val defaults = listOf("com.google.android.youtube.tv", "com.netflix.ninja",
                "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.graymatrix.did",
                "com.sonyliv", "org.videolan.vlc", "com.android.vending")
            val have = defaults.filter { d -> all.any { it.pkg == d } }
            val v = (if (have.isEmpty()) all.take(9).map { it.pkg } else have).joinToString(",")
            sp.edit().putString("favs", v).apply()
            v
        }
        return s.split(",").filter { it.isNotEmpty() }.mapNotNull { p -> all.find { it.pkg == p } }
    }

    private fun favList(): MutableList<String> =
        (sp.getString("favs", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()

    private fun removeFav(pkg: String) {
        val l = favList(); l.remove(pkg)
        sp.edit().putString("favs", l.joinToString(",")).apply()
        buildUi()
    }

    private fun addAppDialog(all: List<App>) {
        val cur = favList()
        val list = all.filter { it.pkg !in cur }
        AlertDialog.Builder(this).setTitle("Add app to home")
            .setItems(list.map { it.label }.toTypedArray()) { _, i ->
                cur.add(list[i].pkg)
                sp.edit().putString("favs", cur.joinToString(",")).apply()
                buildUi()
            }.show()
    }

    private fun launch(i: Intent) {
        try { startActivity(i) } catch (e: Exception) { toast("Can't open app") }
    }

    private fun openTvSettings() {
        try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) { toast("Can't open settings") }
    }

    private fun inputs(): List<Pair<String, Uri>> {
        val out = ArrayList<Pair<String, Uri>>()
        try {
            val m = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            for (i in m.tvInputList) {
                if (i.isPassthroughInput) {
                    val l = (i.loadCustomLabel(this) ?: i.loadLabel(this)).toString()
                    out.add(Pair(l, TvContract.buildChannelUriForPassthroughInput(i.id)))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    // ---------- launcher settings ----------
    private fun launcherSettings() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(12), dp(24), dp(4))
        fun field(hint: String, v: String): EditText {
            val e = EditText(this); e.hint = hint; e.setText(v); e.setSingleLine(); box.addView(e); return e
        }
        val name = field("Your name", sp.getString("name", "Deep") ?: "")
        val acc = field("Accent color, e.g. #FFC107", sp.getString("accent", "#FFC107") ?: "")
        val cd = field("Card color, e.g. #14110F", sp.getString("card", "#14110F") ?: "")
        val auto = CheckBox(this); auto.text = "Auto-match colors to wallpaper"; auto.isChecked = sp.getBoolean("auto", true)
        box.addView(auto)
        var dlg: AlertDialog? = null

        fun fix(s: String): String { val t = s.trim(); return if (t.startsWith("#")) t else "#$t" }
        fun save() {
            val ed = sp.edit().putString("name", name.text.toString()).putBoolean("auto", auto.isChecked)
            val a = fix(acc.text.toString()); val c = fix(cd.text.toString())
            try { Color.parseColor(a); ed.putString("accent", a) } catch (e: Exception) { toast("Bad accent hex") }
            try { Color.parseColor(c); ed.putString("card", c) } catch (e: Exception) { toast("Bad card hex") }
            ed.apply()
        }

        val pick = Button(this); pick.text = "Choose wallpaper"
        pick.setOnClickListener { save(); dlg?.dismiss(); pickWallpaper() }
        val rm = Button(this); rm.text = "Remove wallpaper"
        rm.setOnClickListener { save(); File(filesDir, "wp.jpg").delete(); dlg?.dismiss(); buildUi() }
        box.addView(pick); box.addView(rm)

        dlg = AlertDialog.Builder(this).setTitle("Launcher settings").setView(box)
            .setPositiveButton("Save") { _, _ -> save(); buildUi() }
            .setNegativeButton("Cancel", null).create()
        dlg.show()
    }

    private fun pickWallpaper() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ)
        } catch (e: Exception) { toast("No file picker found on this TV") }
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val uri = data?.data
        if (rc != REQ || res != RESULT_OK || uri == null) return
        try {
            val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= 1920) s *= 2
            val o2 = BitmapFactory.Options(); o2.inSampleSize = s
            val bm = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bm != null) {
                File(filesDir, "wp.jpg").outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                if (sp.getBoolean("auto", true)) autoColors(bm)
            } else toast("Couldn't load image")
        } catch (e: Exception) { toast("Couldn't load image") }
        buildUi()
    }

    // pick the strongest vivid hue as accent, dark tint of the average as card color
    private fun autoColors(bm: Bitmap) {
        val s = Bitmap.createScaledBitmap(bm, 48, 27, true)
        val w = DoubleArray(12); val rs = DoubleArray(12); val gs = DoubleArray(12); val bs = DoubleArray(12)
        var tr = 0L; var tg = 0L; var tb = 0L; var n = 0
        val hsv = FloatArray(3)
        for (y in 0 until s.height) for (x in 0 until s.width) {
            val p = s.getPixel(x, y)
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            tr += r; tg += g; tb += b; n++
            Color.colorToHSV(p, hsv)
            val wt = (hsv[1] * hsv[2]).toDouble()
            if (wt < 0.15) continue
            val k = (hsv[0] / 30f).toInt().coerceIn(0, 11)
            w[k] += wt; rs[k] += r * wt; gs[k] += g * wt; bs[k] += b * wt
        }
        var best = 0
        for (k in 1 until 12) if (w[k] > w[best]) best = k
        val accent = if (w[best] > 0) {
            val ar = rs[best] / w[best]; val ag = gs[best] / w[best]; val ab = bs[best] / w[best]
            val f = 255.0 / maxOf(ar, ag, ab, 1.0)
            Color.rgb((ar * f).toInt().coerceIn(0, 255), (ag * f).toInt().coerceIn(0, 255), (ab * f).toInt().coerceIn(0, 255))
        } else Color.rgb(224, 224, 224)
        val card = Color.rgb((tr / n * 0.22).toInt(), (tg / n * 0.22).toInt(), (tb / n * 0.22).toInt())
        sp.edit().putString("accent", hex(accent)).putString("card", hex(card)).apply()
    }
}
