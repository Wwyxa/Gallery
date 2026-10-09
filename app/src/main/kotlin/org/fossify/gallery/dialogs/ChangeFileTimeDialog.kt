package org.fossify.gallery.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.media.MediaScannerConnection
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.graphics.ColorUtils
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getDialogBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.FileTimePlanner
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ChangeFileTimeDialog(private val activity: BaseSimpleActivity, paths: List<String>, private val refresh: () -> Unit) {
    private data class Entry(val path: String, val original: Long, val size: Long, val target: Long = 0)
    private class Choice {
        var selectedIndex = 0
        var onChanged: (() -> Unit)? = null
    }
    private val zone = ZoneId.systemDefault()
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
    private val dialogContext: Context = activity.getAlertDialogBuilder().context
    private val frozenPaths = paths.distinct().toList()

    init {
        ensureBackgroundThread {
            val entries = frozenPaths.map { path -> File(path).let { Entry(path, it.lastModified(), it.length()) } }
            activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) configure(entries) }
        }
    }

    private fun column() = LinearLayout(dialogContext).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun messageView(message: String) = TextView(dialogContext).apply {
        text = message
        setTextColor(activity.getProperTextColor())
        textSize = 16f
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, padding)
    }

    private fun rowsView(rows: List<String>) = ListView(dialogContext).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.5f).toInt())
        adapter = object : ArrayAdapter<String>(dialogContext, android.R.layout.simple_list_item_1, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply { (this as TextView).setTextColor(activity.getProperTextColor()) }
        }
    }

    private fun styleInput(input: EditText) {
        input.setTextColor(activity.getProperTextColor())
        input.backgroundTintList = ColorStateList.valueOf(activity.getProperPrimaryColor())
    }

    private fun configure(entries: List<Entry>) {
        if (entries.isEmpty()) return
        val content = column().apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        fun label(parent: LinearLayout, text: Int) {
            parent.addView(TextView(dialogContext).apply {
                setText(text)
                setTextColor(activity.getProperTextColor())
                val spacing = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, spacing, 0, 0)
            })
        }
        fun choices(parent: LinearLayout, title: Int, values: Int): Choice {
            val choice = Choice()
            val options = activity.resources.getStringArray(values)
            label(parent, title)
            val density = activity.resources.displayMetrics.density
            val backgroundColor = activity.getDialogBackgroundColor()
            val textColor = activity.getProperTextColor()
            val primaryColor = activity.getProperPrimaryColor()
            val spinner = AppCompatSpinner(dialogContext, null, androidx.appcompat.R.attr.spinnerStyle, android.widget.Spinner.MODE_DROPDOWN).apply {
                minimumHeight = (48 * density).toInt()
                backgroundTintList = ColorStateList.valueOf(textColor)
                setPopupBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                    setColor(backgroundColor)
                    cornerRadius = 4 * density
                    setStroke((density).toInt().coerceAtLeast(1), ColorUtils.blendARGB(backgroundColor, textColor, 0.15f))
                })
                adapter = object : ArrayAdapter<String>(dialogContext, android.R.layout.simple_spinner_item, options) {
                    init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                        super.getView(position, convertView, parent).apply {
                            (this as TextView).setTextColor(textColor)
                            textSize = 16f
                            minimumHeight = (48 * density).toInt()
                            gravity = android.view.Gravity.CENTER_VERTICAL
                        }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                        super.getDropDownView(position, convertView, parent).apply {
                            (this as TextView).setTextColor(textColor)
                            textSize = 16f
                            minimumHeight = (48 * density).toInt()
                            val padding = (16 * density).toInt()
                            setPadding(padding, (12 * density).toInt(), padding, (12 * density).toInt())
                            setBackgroundColor(if (position == choice.selectedIndex) ColorUtils.blendARGB(backgroundColor, primaryColor, 0.14f) else backgroundColor)
                            if (this is android.widget.CheckedTextView) {
                                isChecked = position == choice.selectedIndex
                                checkMarkTintList = ColorStateList.valueOf(primaryColor)
                            }
                        }
                }
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (choice.selectedIndex != position) {
                            choice.selectedIndex = position
                            choice.onChanged?.invoke()
                        }
                    }
                }
            }
            parent.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return choice
        }
        fun number(parent: LinearLayout, title: Int): EditText {
            label(parent, title)
            return AppCompatEditText(dialogContext).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                styleInput(this)
                setText("0")
                parent.addView(this)
            }
        }
        val mode = choices(content, R.string.file_time_mode, R.array.file_time_modes)
        val dateFields = column().also { content.addView(it) }
        label(dateFields, R.string.file_time_start)
        val initial = Instant.ofEpochMilli(entries.first().original).atZone(zone).toLocalDateTime().withNano(0)
        val inputs = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.HORIZONTAL
            dateFields.addView(this)
        }
        fun inputGroup() = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            inputs.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val dateGroup = inputGroup()
        val timeGroup = inputGroup()
        fun timeField(parent: LinearLayout, value: String, hintText: String): EditText = AppCompatEditText(dialogContext).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            textSize = 16f
            styleInput(this)
            hint = hintText
            setText(value)
            parent.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val dateInput = timeField(dateGroup, dateFormat.format(initial), "yyyy-MM-dd")
        val timeInput = timeField(timeGroup, timeFormat.format(initial), "HH:mm:ss")
        val controls = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            dateFields.addView(this)
        }
        val components = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.HORIZONTAL
            controls.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val checkColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(activity.getProperPrimaryColor(), activity.getProperTextColor())
        )
        val date = AppCompatCheckBox(dialogContext).apply {
            setText(R.string.file_time_date)
            setTextColor(activity.getProperTextColor())
            buttonTintList = checkColors
            textSize = 14f
            isChecked = true
            components.addView(this)
        }
        val time = AppCompatCheckBox(dialogContext).apply {
            setText(R.string.file_time_time)
            setTextColor(activity.getProperTextColor())
            buttonTintList = checkColors
            textSize = 14f
            isChecked = true
            components.addView(this)
        }
        fun picker(parent: LinearLayout, title: Int, icon: Int, action: () -> Unit) {
            val size = (48 * activity.resources.displayMetrics.density).toInt()
            val button = androidx.appcompat.widget.AppCompatImageButton(dialogContext).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(activity.getProperTextColor())
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                contentDescription = activity.getString(title)
                tooltipText = activity.getString(title)
                setOnClickListener { action() }
            }
            parent.addView(button, LinearLayout.LayoutParams(size, size))
        }
        picker(dateGroup, R.string.file_time_pick_date, android.R.drawable.ic_menu_month) {
            val previous = runCatching { LocalDate.parse(dateInput.text.toString(), dateFormat) }.getOrDefault(initial.toLocalDate())
            val widget = DatePicker(ContextThemeWrapper(dialogContext, R.style.FileTimePickerTheme)).apply {
                init(previous.year, previous.monthValue - 1, previous.dayOfMonth, null)
            }
            val pickerBuilder = activity.getAlertDialogBuilder()
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    widget.clearFocus()
                    dateInput.setText(dateFormat.format(LocalDate.of(widget.year, widget.month + 1, widget.dayOfMonth)))
                }
            activity.setupDialogStuff(widget, pickerBuilder, R.string.file_time_pick_date)
        }
        picker(timeGroup, R.string.file_time_pick_time, android.R.drawable.ic_menu_recent_history) {
            val previous = runCatching { LocalTime.parse(timeInput.text.toString(), timeFormat) }.getOrDefault(initial.toLocalTime())
            val widget = TimePicker(ContextThemeWrapper(dialogContext, R.style.FileTimePickerTheme)).apply {
                setIs24HourView(android.text.format.DateFormat.is24HourFormat(activity))
                hour = previous.hour
                minute = previous.minute
            }
            val pickerBuilder = activity.getAlertDialogBuilder()
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    widget.clearFocus()
                    timeInput.setText(timeFormat.format(previous.withHour(widget.hour).withMinute(widget.minute)))
                }
            activity.setupDialogStuff(widget, pickerBuilder, R.string.file_time_pick_time)
        }
        val interval = column().also { content.addView(it) }
        val directionFields = column().also { interval.addView(it) }
        val direction = choices(directionFields, R.string.file_time_direction, R.array.file_time_directions)
        val amounts = LinearLayout(dialogContext).apply { orientation = LinearLayout.HORIZONTAL; interval.addView(this) }
        fun intervalNumber(title: Int): EditText {
            val field = column()
            val spacing = (4 * activity.resources.displayMetrics.density).toInt()
            field.setPadding(0, 0, spacing, 0)
            amounts.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return number(field, title)
        }
        val days = intervalNumber(R.string.file_time_days)
        val hours = intervalNumber(R.string.file_time_hours)
        val minutes = intervalNumber(R.string.file_time_minutes)
        val seconds = intervalNumber(R.string.file_time_seconds)
        val orderFields = column().also { content.addView(it) }
        val order = choices(orderFields, R.string.file_time_order, R.array.file_time_orders)
        var dialog: AlertDialog? = null
        fun updateFields() {
            val position = mode.selectedIndex
            dateFields.visibility = if (position == 1) View.GONE else View.VISIBLE
            components.visibility = if (position == 0) View.VISIBLE else View.GONE
            dateGroup.visibility = if (position == 2 || date.isChecked) View.VISIBLE else View.GONE
            timeGroup.visibility = if (position == 2 || time.isChecked) View.VISIBLE else View.GONE
            interval.visibility = if (position == 0) View.GONE else View.VISIBLE
            directionFields.visibility = if (position == 1) View.VISIBLE else View.GONE
            orderFields.visibility = if (position == 2 && entries.size > 1) View.VISIBLE else View.GONE
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = position != 0 || date.isChecked || time.isChecked
        }
        date.setOnCheckedChangeListener { _, _ -> updateFields() }
        time.setOnCheckedChangeListener { _, _ -> updateFields() }
        mode.onChanged = { updateFields() }
        val builder = activity.getAlertDialogBuilder()
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.file_time_preview, null)
        activity.setupDialogStuff(ScrollView(dialogContext).apply { addView(content) }, builder, R.string.file_time_title) { alertDialog ->
            dialog = alertDialog
            alertDialog.setTitle(activity.getString(R.string.file_time_title_count, entries.size))
            updateFields()
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    fun amount(field: EditText, multiplier: Long): Long {
                        val value = field.text.toString().toLong()
                        require(value >= 0)
                        return Math.multiplyExact(value, multiplier)
                    }
                    val duration = if (mode.selectedIndex == 0) 0L else
                        Math.addExact(Math.addExact(amount(days, 86400), amount(hours, 3600)), Math.addExact(amount(minutes, 60), amount(seconds, 1)))
                    require(duration >= 0)
                    val sorted = when (order.selectedIndex) {
                        1 -> entries.sortedBy { File(it.path).name.lowercase(java.util.Locale.ROOT) }
                        2 -> entries.sortedByDescending { File(it.path).name.lowercase(java.util.Locale.ROOT) }
                        3 -> entries.sortedBy { it.original }
                        4 -> entries.sortedByDescending { it.original }
                        else -> entries
                    }
                    val needsDate = mode.selectedIndex == 2 || mode.selectedIndex == 0 && date.isChecked
                    val needsTime = mode.selectedIndex == 2 || mode.selectedIndex == 0 && time.isChecked
                    val selectedDate = if (needsDate) LocalDate.parse(dateInput.text.toString().trim(), dateFormat) else initial.toLocalDate()
                    val selectedTimeOfDay = if (needsTime) LocalTime.parse(timeInput.text.toString().trim(), timeFormat) else initial.toLocalTime()
                    val selectedTime = LocalDateTime.of(selectedDate, selectedTimeOfDay)
                    val start = if (mode.selectedIndex == 2) FileTimePlanner.uniform(0, selectedTime, true, true, zone) else 0L
                    val plan = (if (mode.selectedIndex == 2) sorted else entries).mapIndexed { index, entry ->
                        val target = when (mode.selectedIndex) {
                            0 -> FileTimePlanner.uniform(entry.original, selectedTime, date.isChecked, time.isChecked, zone)
                            1 -> FileTimePlanner.offset(entry.original, if (direction.selectedIndex == 0) duration else -duration)
                            else -> FileTimePlanner.sequence(start, duration, index)
                        }
                        entry.copy(target = target / 1000 * 1000)
                    }
                    preview(plan) { alertDialog.dismiss() }
                } catch (_: Exception) {
                    val errorBuilder = activity.getAlertDialogBuilder().setPositiveButton(android.R.string.ok, null)
                    activity.setupDialogStuff(messageView(activity.getString(R.string.file_time_invalid)), errorBuilder, R.string.file_time_title)
                }
            }
        }
    }

    private fun display(value: Long) = format.format(Instant.ofEpochMilli(value).atZone(zone))

    private fun preview(plan: List<Entry>, close: () -> Unit) {
        val rows = plan.map { "${File(it.path).name}\n${display(it.original)} → ${display(it.target)}" }
        val builder = activity.getAlertDialogBuilder()
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.file_time_apply) { _, _ -> close(); authorize(plan) }
        activity.setupDialogStuff(rowsView(rows), builder, R.string.file_time_preview)
    }

    private fun authorize(plan: List<Entry>) {
        val permitted = ArrayList<Entry>()
        val failures = ArrayList<Pair<Entry, String>>()
        fun next(index: Int) {
            if (activity.isFinishing || activity.isDestroyed) return
            if (index == plan.size) {
                execute(permitted, failures)
                return
            }
            val entry = plan[index]
            activity.checkManageMediaOrHandleSAFDialogSdk30(entry.path) { allowed ->
                if (allowed) permitted.add(entry) else failures.add(entry to activity.getString(R.string.file_time_permission))
                android.os.Handler(android.os.Looper.getMainLooper()).post { next(index + 1) }
            }
        }
        next(0)
    }

    private fun execute(plan: List<Entry>, failures: ArrayList<Pair<Entry, String>>) {
        val cancelled = AtomicBoolean(false)
        val progressText = messageView("0 / ${plan.size}")
        val progressBuilder = activity.getAlertDialogBuilder().setNegativeButton(android.R.string.cancel, null)
        var progress: AlertDialog? = null
        activity.setupDialogStuff(progressText, progressBuilder, R.string.file_time_title) { alertDialog ->
            progress = alertDialog
            alertDialog.setCancelable(false)
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancelled.set(true) }
        }
        if (progress == null) return
        ensureBackgroundThread {
            val successful = ArrayList<String>()
            var cacheUpdated = true
            var completed = 0
            for (entry in plan) {
                if (cancelled.get()) break
                val file = File(entry.path)
                try {
                    check(file.isFile) { activity.getString(R.string.file_time_missing) }
                    check(file.lastModified() == entry.original && file.length() == entry.size) { activity.getString(R.string.file_time_changed) }
                    check(file.setLastModified(entry.target)) { activity.getString(R.string.file_time_write_failed) }
                    val actual = file.lastModified()
                    check(actual / 1000 == entry.target / 1000) { activity.getString(R.string.file_time_verify_failed) }
                    successful.add(entry.path)
                    if (runCatching { activity.mediaDB.updateLastModified(entry.path, actual) }.isFailure) cacheUpdated = false
                } catch (exception: Exception) {
                    failures.add(entry to (exception.message ?: activity.getString(R.string.file_time_write_failed)))
                }
                completed++
                val count = completed
                activity.runOnUiThread { progressText.text = "$count / ${plan.size}" }
            }
            var indexed = cacheUpdated
            if (successful.isNotEmpty()) {
                val scans = CountDownLatch(successful.size)
                val scanFailed = AtomicBoolean(false)
                try {
                    MediaScannerConnection.scanFile(activity.applicationContext, successful.toTypedArray(), null) { _, uri ->
                        if (uri == null) scanFailed.set(true)
                        scans.countDown()
                    }
                    indexed = scans.await(30, TimeUnit.SECONDS) && !scanFailed.get() && cacheUpdated
                } catch (_: Exception) {
                    indexed = false
                }
            }
            val remaining = plan.size - completed
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                progress?.dismiss()
                refresh()
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val result = activity.getString(R.string.file_time_result, successful.size, failures.size, remaining) +
                        if (indexed) "" else "\n" + activity.getString(R.string.file_time_scan_pending)
                    val builder = activity.getAlertDialogBuilder()
                        .setPositiveButton(android.R.string.ok, null)
                    if (failures.isNotEmpty()) {
                        builder.setNeutralButton(R.string.file_time_failures) { _, _ ->
                            val failureBuilder = activity.getAlertDialogBuilder()
                                .setPositiveButton(android.R.string.ok, null)
                                .setNeutralButton(R.string.file_time_retry) { _, _ -> authorize(failures.map { it.first }) }
                            activity.setupDialogStuff(rowsView(failures.map { "${it.first.path}\n${it.second}" }), failureBuilder, R.string.file_time_failures)
                        }
                    }
                    activity.setupDialogStuff(messageView(result), builder, R.string.file_time_title)
                }
            }
        }
    }
}