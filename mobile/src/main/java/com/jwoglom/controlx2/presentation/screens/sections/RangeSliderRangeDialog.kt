package com.jwoglom.controlx2.presentation.screens.sections

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.slider.RangeSlider

/**
 * Shared range-selection dialog: a native [RangeSlider] over a [firstSeq]..[lastSeq] window with
 * live Start/End date labels ([dateLabel]) and a computed [detail] row.
 *
 * The Debug history fetcher and the NightScout "Send Range" action both call this so they share
 * one builder and identical UX instead of each rebuilding the slider, rows, and live updates.
 * The caller supplies the seq window, how to render an id as a date, and the detail string, so the
 * dialog itself has no dependency on the pump, the DB, or the anchor sampler.
 */
internal suspend fun rangeSliderRangeDialog(
    context: Context,
    title: String,
    instructions: String,
    firstSeq: Long,
    lastSeq: Long,
    dateLabel: (id: Long) -> String,
    detail: (startId: Long, endId: Long) -> String,
    confirmText: String,
    onConfirm: (startId: Long, endId: Long) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle(title)

    val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    // Range slider: two native handles pick the start (oldest) and end (head) IDs. RangeSlider
    // keeps the thumbs ordered, so start never runs past end without manual clamping.
    layout.addView(divider(context))
    layout.addView(instructions(context, instructions))
    val rangeSlider = RangeSlider(context).apply {
        valueFrom = firstSeq.toFloat()
        valueTo = lastSeq.toFloat()
        values = mutableListOf(firstSeq.toFloat(), lastSeq.toFloat())
        stepSize = 1.0f
    }
    layout.addView(rangeSlider)

    // Info rows: start/end/detail, kept current as either slider moves.
    val startRow = infoRow(context, "Start", dateLabel(firstSeq))
    val endRow = infoRow(context, "End", dateLabel(lastSeq))
    val detailRow = infoRow(context, "Detail", detail(firstSeq, lastSeq))
    layout.addView(startRow.first)
    layout.addView(endRow.first)
    layout.addView(detailRow.first)

    // Live updates: keep id/date labels and the detail current. RangeSlider keeps the thumbs
    // ordered, so start never runs past end without manual clamping.
    fun refresh() {
        val vs = rangeSlider.values
        val s = vs[0].toLong()
        val e = vs[1].toLong()
        startRow.second.text = dateLabel(s)
        endRow.second.text = dateLabel(e)
        detailRow.second.text = detail(s, e)
    }
    rangeSlider.addOnChangeListener { _, _, _ -> refresh() }

    builder.setView(layout)
    builder.setPositiveButton(confirmText) { _, _ ->
        val vs = rangeSlider.values
        onConfirm(vs[0].toLong(), vs[1].toLong())
    }
    builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }
    builder.show()
}

/** Two-column info row: a bold label and a value that fills the remaining width. */
private fun infoRow(context: Context, label: String, value: String): Pair<View, TextView> {
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (10 * context.resources.displayMetrics.density).toInt()
            bottomMargin = (10 * context.resources.displayMetrics.density).toInt()
        }
    }
    val labelView = TextView(context).apply {
        this.text = label
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, context.dp(16), 0)
    }
    val valueView = TextView(context).apply {
        this.text = value
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    }
    row.addView(labelView)
    row.addView(valueView)
    return Pair(row, valueView)
}

private fun instructions(context: Context, text: String): TextView =
    TextView(context).apply {
        this.text = text
        setTypeface(null, Typeface.NORMAL)
        // Muted gray so it reads as guidance, not a field label.
        setTextColor(0xFF9E9E9E.toInt())
    }

private fun divider(context: Context): View {
    val h = (2 * context.resources.displayMetrics.density).toInt()
    val v = View(context)
    v.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, h
    ).apply {
        topMargin = (10 * context.resources.displayMetrics.density).toInt()
        bottomMargin = (10 * context.resources.displayMetrics.density).toInt()
    }
    v.setBackgroundColor(0xFFE0E0E0.toInt())
    return v
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
