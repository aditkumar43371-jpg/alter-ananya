package app.alterananya

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton

object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    fun fs(v: Float): Float = v * S.float(S.FONT)

    fun textColor(c: Context): Int {
        val tv = TypedValue()
        val ok = c.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
        return if (ok) tv.data else 0xFF000000.toInt()
    }

    fun label(c: Context, text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = fs(size)
        t.setTextColor(textColor(c))
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(c, 6), 0, dp(c, 6))
        return t
    }

    fun heading(c: Context, text: String): TextView {
        val t = label(c, text, 20f, true)
        t.setPadding(0, dp(c, 18), 0, dp(c, 6))
        ViewCompat.setAccessibilityHeading(t, true)
        return t
    }

    fun button(c: Context, text: String, desc: String? = null, onClick: () -> Unit): MaterialButton {
        val b = MaterialButton(c)
        b.text = text
        b.isAllCaps = false
        b.textSize = fs(16f)
        b.minimumHeight = dp(c, 56)
        b.minHeight = dp(c, 56)
        if (desc != null) b.contentDescription = desc
        b.setOnClickListener { onClick() }
        return b
    }
}

/** A simple vertical form builder with TalkBack-friendly labels. */
class Form(val ctx: Context) {
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = Ui.dp(ctx, 16)
        setPadding(p, p, p, p)
    }

    fun <T : View> add(v: T): T {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Ui.dp(ctx, 4)
        root.addView(v, lp)
        return v
    }

    fun heading(t: String) = add(Ui.heading(ctx, t))
    fun text(t: String): TextView = add(Ui.label(ctx, t))
    fun button(t: String, desc: String? = null, onClick: () -> Unit): MaterialButton = add(Ui.button(ctx, t, desc, onClick))

    fun edit(
        label: String, value: String, hint: String = "", multi: Boolean = false,
        secret: Boolean = false, number: Boolean = false, onChange: ((String) -> Unit)? = null
    ): EditText {
        val l = Ui.label(ctx, label)
        l.id = View.generateViewId()
        add(l)
        val e = EditText(ctx)
        e.id = View.generateViewId()
        l.labelFor = e.id
        e.setText(value)
        e.textSize = Ui.fs(16f)
        e.setTextColor(Ui.textColor(ctx))
        if (hint.isNotEmpty()) e.hint = hint
        e.minHeight = Ui.dp(ctx, 56)
        e.inputType = when {
            secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            number -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multi) { e.setSingleLine(false); e.minLines = 3; e.gravity = android.view.Gravity.TOP }
        if (onChange != null) {
            e.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
            })
        }
        add(e)
        return e
    }

    fun spinner(label: String, items: List<String>, sel: Int, onPick: (Int) -> Unit): Spinner {
        val l = Ui.label(ctx, label)
        l.id = View.generateViewId()
        add(l)
        val sp = Spinner(ctx)
        sp.id = View.generateViewId()
        l.labelFor = sp.id
        sp.minimumHeight = Ui.dp(ctx, 56)
        sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, items)
        var last = sel.coerceAtLeast(0)
        sp.setSelection(last, false)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == last) return
                last = pos
                onPick(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        add(sp)
        return sp
    }

    fun seek(label: String, min: Int, max: Int, cur: Int, fmt: (Int) -> String, onChange: (Int) -> Unit): SeekBar {
        val t = Ui.label(ctx, "$label: ${fmt(cur)}")
        t.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        add(t)
        val sb = SeekBar(ctx)
        sb.max = max - min
        sb.progress = (cur - min).coerceIn(0, max - min)
        sb.minimumHeight = Ui.dp(ctx, 48)
        sb.contentDescription = label
        ViewCompat.setStateDescription(sb, fmt(cur))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + min
                t.text = "$label: ${fmt(v)}"
                ViewCompat.setStateDescription(sb, fmt(v))
                if (fromUser) onChange(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        add(sb)
        return sb
    }

    fun check(label: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox {
        val c = CheckBox(ctx)
        c.text = label
        c.textSize = Ui.fs(16f)
        c.setTextColor(Ui.textColor(ctx))
        c.minHeight = Ui.dp(ctx, 56)
        c.isChecked = checked
        c.setOnCheckedChangeListener { _, v -> onChange(v) }
        add(c)
        return c
    }
}
