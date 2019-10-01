package com.cameron.pomodoro

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private var isTimerRunning = false
    private var isOnBreak = false
    private var defaultWorkTime = 0L
    private var defaultBreaktime = 0L
    private lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var preferences: SharedPreferences
    private lateinit var timerIntent: Intent

    private val pong = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isTimerRunning = intent!!.getBooleanExtra(TimerService.INTENT_EXTRA_RUNNING, false)
        }
    }

    private val onTick = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val timeRemaining = intent!!.getLongExtra(TimerService.TIME_REMAINING, 0L)
            val min = timeRemaining / 60_000
            val sec = (timeRemaining % 60_000) / 1_000

            tv_time_remaining.text = getString(R.string.time_remaining, min, sec)

            timer_progress.progress = if (isOnBreak) {
                ((timeRemaining.toFloat() / defaultBreaktime) * 100).toInt()
            } else {
                ((timeRemaining.toFloat() / defaultWorkTime) * 100).toInt()
            }
        }
    }

    private val onFinish = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            timer_progress.progress = 100

            if (isOnBreak) {
                animateColorChange(
                    ContextCompat.getColor(this@MainActivity, R.color.pomodoroWorking),
                    ContextCompat.getColor(this@MainActivity, R.color.pomodoroBreak)
                )
            } else {
                animateColorChange(
                    ContextCompat.getColor(this@MainActivity, R.color.pomodoroBreak),
                    ContextCompat.getColor(this@MainActivity, R.color.pomodoroWorking)
                )
            }
        }
    }

    private val preferenceChangeListener = OnSharedPreferenceChangeListener { pref, key ->
        when (key) {
            getString(R.string.pref_is_on_break) -> {
                isOnBreak = pref.getBoolean(key, resources.getBoolean(R.bool.is_on_break))

                ic_toggle_session_type.setImageDrawable(
                    getDrawable(
                        if (isOnBreak) R.drawable.ic_hourglass_empty_32dp
                        else R.drawable.ic_hourglass_full_32dp
                    )
                )
            }
            getString(R.string.pref_default_runtime) -> {
                defaultWorkTime = pref.getLong(
                    key,
                    resources.getInteger(R.integer.default_runtime).toLong()
                )

                if (!isOnBreak) {
                    pref.edit(commit = true) {
                        putLong(getString(R.string.pref_time_remaining), defaultWorkTime)
                    }
                }

                // Don't reset the text view if the timer is running since
                // the Timer Service can only change its time if it's not running
                if (!isTimerRunning && !isOnBreak) {
                    timer_progress.progress = 100
                    tv_time_remaining.text = getString(
                        R.string.time_remaining,
                        defaultWorkTime / 60_000,
                        (defaultWorkTime % 60_000) / 1_000
                    )
                }
            }
            getString(R.string.pref_default_breaktime) -> {
                defaultBreaktime = pref.getLong(
                    key,
                    resources.getInteger(R.integer.default_breaktime).toLong()
                )

                if (isOnBreak) {
                    pref.edit(commit = true) {
                        putLong(getString(R.string.pref_time_remaining), defaultBreaktime)
                    }
                }

                if (!isTimerRunning && isOnBreak) {
                    timer_progress.progress = 100
                    tv_time_remaining.text = getString(
                        R.string.time_remaining,
                        defaultBreaktime / 60_000,
                        (defaultBreaktime % 60_000) / 1_000
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Makes the notification bar transparent
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        broadcastManager = LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(pong, IntentFilter(ServiceEchoReceiver.PONG))
            sendBroadcastSync(Intent(ServiceEchoReceiver.PING))
        }
        preferences = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        timerIntent = Intent(this, TimerService::class.java)

        defaultWorkTime = preferences.getLong(
            getString(R.string.pref_default_runtime),
            resources.getInteger(R.integer.default_runtime).toLong()
        )

        defaultBreaktime = preferences.getLong(
            getString(R.string.pref_default_breaktime),
            resources.getInteger(R.integer.default_breaktime).toLong()
        )

        isOnBreak = preferences.getBoolean(
            getString(R.string.pref_is_on_break),
            resources.getBoolean(R.bool.is_on_break)
        )

        val timeRemaining = preferences.getLong(
            getString(R.string.pref_time_remaining),
            defaultWorkTime
        )

        tv_time_remaining.text = getString(
            R.string.time_remaining,
            timeRemaining / 60_000,
            (timeRemaining % 60_000) / 1_000
        )

        timer_progress.progress = if (isOnBreak) {
            ((timeRemaining.toFloat() / defaultBreaktime) * 100).toInt()
        } else {
            ((timeRemaining.toFloat() / defaultWorkTime) * 100).toInt()
        }
    }

    override fun onResume() {
        super.onResume()
        val backgroundColor =
            if (!isOnBreak) R.color.pomodoroWorking
            else R.color.pomodoroBreak

        btn_start.visibility = if (isTimerRunning) View.GONE else View.VISIBLE
        btn_pause.visibility = if (isTimerRunning) View.VISIBLE else View.GONE

        ic_toggle_session_type.setImageDrawable(
            getDrawable(
                if (isOnBreak) R.drawable.ic_hourglass_empty_32dp
                else R.drawable.ic_hourglass_full_32dp
            )
        )

        registerReceiver(onTick, IntentFilter(TimerService.BROADCAST_TIME))
        registerReceiver(onFinish, IntentFilter(TimerService.TIMER_FINISHED))

        // Ask the timer if it's running
        broadcastManager.sendBroadcastSync(Intent(ServiceEchoReceiver.PING))

        root_view.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))

        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onTick)
        unregisterReceiver(onFinish)

        broadcastManager.unregisterReceiver(pong)
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun animateColorChange(from: Int, to: Int) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), from, to)
        colorAnimation.apply {
            duration = 1000
            addUpdateListener { animator ->
                root_view.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    @Suppress("DEPRECATION")
    fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(20)
    }

    fun onStartClick(v: View) {
        val timeRemaining = preferences.getLong(
            getString(R.string.pref_time_remaining),
            if (isOnBreak) defaultBreaktime else defaultWorkTime
        )

        val toColor = if (isOnBreak) R.color.pomodoroBreak else R.color.pomodoroWorking

        btn_start.visibility = View.GONE
        btn_pause.visibility = View.VISIBLE
        vibrate()

        // Resume the timer from where is was paused
        // if it was previously paused
        startService(timerIntent.apply {
            putExtra(TimerService.INTENT_EXTRA_RUNTIME, timeRemaining)
        })

        animateColorChange(
            (root_view.background as ColorDrawable).color,
            ContextCompat.getColor(this, toColor)
        )
    }

    fun onPauseClick(v: View) {
        btn_start.visibility = View.VISIBLE
        btn_pause.visibility = View.GONE
        vibrate()
        stopService(timerIntent)
    }

    fun onCancelClick(v: View) {
        val min: Long
        val sec: Long

        if (isOnBreak) {
            min = defaultBreaktime / 60_000
            sec = (defaultBreaktime % 60_000) / 1_000
        } else {
            min = defaultWorkTime / 60_000
            sec = (defaultWorkTime % 60_000) / 1_000
        }

        btn_start.visibility = View.VISIBLE
        btn_pause.visibility = View.GONE
        tv_time_remaining.text = getString(R.string.time_remaining, min, sec)
        timer_progress.progress = 100

        vibrate()
        stopService(timerIntent)

        preferences.edit(commit = true) {
            putLong(
                getString(R.string.pref_time_remaining),
                if (isOnBreak) defaultBreaktime else defaultWorkTime
            )
        }
    }

    fun openSettingsFragment(v: View) {
        val tag = SettingsActivity::class.java.name
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            ClassLoader.getSystemClassLoader(),
            tag
        )

        // Prevents multiple dialogs from showing at once
        if (supportFragmentManager.findFragmentByTag(tag) == null) {
            (fragment as DialogFragment).show(supportFragmentManager, tag)
            vibrate()
        }
    }

    fun toggleSessionType(v: View) {
        val timeRemaining: Long

        vibrate()
        stopService(timerIntent)

        if (isOnBreak) {
            ic_toggle_session_type.setImageDrawable(
                getDrawable(R.drawable.ic_hourglass_full_32dp)
            )

            animateColorChange(
                ContextCompat.getColor(this@MainActivity, R.color.pomodoroBreak),
                ContextCompat.getColor(this@MainActivity, R.color.pomodoroWorking)
            )

            timeRemaining = preferences.getLong(
                getString(R.string.pref_default_runtime),
                resources.getInteger(R.integer.default_runtime).toLong()
            )
        } else {
            ic_toggle_session_type.setImageDrawable(
                getDrawable(R.drawable.ic_hourglass_empty_32dp)
            )

            animateColorChange(
                ContextCompat.getColor(this@MainActivity, R.color.pomodoroWorking),
                ContextCompat.getColor(this@MainActivity, R.color.pomodoroBreak)
            )

            timeRemaining = preferences.getLong(
                getString(R.string.pref_default_breaktime),
                resources.getInteger(R.integer.default_breaktime).toLong()
            )
        }

        tv_time_remaining.text = getString(
            R.string.time_remaining,
            timeRemaining / 60_000,
            (timeRemaining % 60_000) / 1_000
        )

        timer_progress.progress = 100
        btn_pause.visibility = View.GONE
        btn_start.visibility = View.VISIBLE

        preferences.edit(commit = true) {
            putBoolean(getString(R.string.pref_is_on_break), !isOnBreak)
            putLong(getString(R.string.pref_time_remaining), timeRemaining)
        }
    }
}
