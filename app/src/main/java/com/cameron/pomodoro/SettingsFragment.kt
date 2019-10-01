package com.cameron.pomodoro

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    private var workPref: Preference? = null
    private var breakPref: Preference? = null
    private var alertDialog: AlertDialog? = null
    private lateinit var preferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        preferences = context!!.getSharedPreferences(context!!.packageName, Context.MODE_PRIVATE)
        workPref = findPreference("work")
        breakPref = findPreference("break")

        val workTime = preferences.getLong(
            getString(R.string.pref_default_runtime),
            resources.getInteger(R.integer.default_runtime).toLong()
        )
        val breakTime = preferences.getLong(
            getString(R.string.pref_default_breaktime),
            resources.getInteger(R.integer.default_breaktime).toLong()
        )

        workPref?.summary = getString(
            R.string.work_duration_summary,
            workTime.toInt() / 60_000,
            (workTime.toInt() % 60_000) / 1_000
        )

        workPref?.setOnPreferenceClickListener {
            showTimePickerDialog(workPref!!.key)
            true
        }

        breakPref?.summary = getString(
            R.string.break_duration_summary,
            breakTime.toInt() / 60_000,
            (breakTime % 60_000) / 1_000
        )

        breakPref?.setOnPreferenceClickListener {
            showTimePickerDialog(breakPref!!.key)
            true
        }
    }

    override fun onPause() {
        super.onPause()
        // Prevents window leaks when the device is rotated
        alertDialog?.dismiss()
    }

    @SuppressLint("DefaultLocale", "InflateParams")
    private fun showTimePickerDialog(prefKey: String) {
        val workTime = preferences.getLong(
            getString(R.string.pref_default_runtime),
            resources.getInteger(R.integer.default_runtime).toLong()
        )

        val breakTime = preferences.getLong(
            getString(R.string.pref_default_breaktime),
            resources.getInteger(R.integer.default_breaktime).toLong()
        )

        val builder = AlertDialog.Builder(context!!).apply {
            val dialogView = layoutInflater.inflate(R.layout.dialog_duration, null)
            val minutePicker = dialogView.findViewById<NumberPicker>(R.id.picker_minutes)
            val secondPicker = dialogView.findViewById<NumberPicker>(R.id.picker_seconds)
            val displayedValues = Array(60) { i ->
                String.format("%02d", i)
            }

            setTitle("${prefKey.capitalize()} Duration")
            setView(dialogView)
            setPositiveButton("Save") { _, _ ->
                preferences.edit(commit = true) {
                    val totalTime = (minutePicker.value * 60_000L) + (secondPicker.value * 1_000L)

                    if (totalTime < 10_000L) {
                        Toast.makeText(
                            context,
                            getString(R.string.pref_duration_error),
                            Toast.LENGTH_LONG
                        ).show()
                        return@setPositiveButton
                    }

                    if (prefKey == "work") {
                        putLong(getString(R.string.pref_default_runtime), totalTime)
                    } else {
                        putLong(getString(R.string.pref_default_breaktime), totalTime)
                    }
                }

                if (prefKey == "work") {
                    workPref?.summary = getString(
                        R.string.work_duration_summary,
                        minutePicker.value,
                        secondPicker.value
                    )
                } else {
                    breakPref?.summary = getString(
                        R.string.break_duration_summary,
                        minutePicker.value,
                        secondPicker.value
                    )
                }
            }
            setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

            minutePicker.maxValue = 59
            minutePicker.minValue = 0
            minutePicker.displayedValues = displayedValues
            minutePicker.value = if (prefKey == "work") {
                (workTime.toInt() / 60_000)
            } else {
                (breakTime.toInt() / 60_000)
            }

            secondPicker.maxValue = 59
            secondPicker.minValue = 0
            secondPicker.displayedValues = displayedValues
            secondPicker.value = if (prefKey == "work") {
                (workTime.toInt() % 60_000) / 1_000
            } else {
                (breakTime.toInt() % 60_000) / 1_000
            }
        }

        alertDialog = builder.create()
        alertDialog?.show()
    }
}