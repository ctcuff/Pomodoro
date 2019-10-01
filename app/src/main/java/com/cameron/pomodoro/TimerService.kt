package com.cameron.pomodoro

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.os.IBinder
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class TimerService : Service() {
    companion object {
        const val BROADCAST_TIME = "TimerService"
        const val TIME_REMAINING = "timeRemaining"
        const val TIMER_FINISHED = "timerFinished"
        const val INTENT_EXTRA_RUNNING = "running"
        const val INTENT_EXTRA_RUNTIME = "runtime"
    }

    private val notificationId = 420
    private val serviceReceiver = ServiceEchoReceiver()
    private lateinit var timer: CountDownTimer
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var preferences: SharedPreferences
    private lateinit var builder: NotificationCompat.Builder

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        notificationManager = NotificationManagerCompat.from(this)

        // Listens for "ping" so we can send a "pong", letting the app
        // know that this service is running
        broadcastManager = LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(serviceReceiver, IntentFilter(ServiceEchoReceiver.PING))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pongIntent = Intent(ServiceEchoReceiver.PONG).apply {
            putExtra(INTENT_EXTRA_RUNNING, true)
        }
        val defaultRuntime = getDefaultRuntime(preferences)

        var isOnBreak = preferences.getBoolean(
            getString(R.string.pref_is_on_break),
            resources.getBoolean(R.bool.is_on_break)
        )

        val runtime = intent?.getLongExtra(INTENT_EXTRA_RUNTIME, defaultRuntime) ?: defaultRuntime
        val tickIntent = Intent(BROADCAST_TIME)
        val finishIntent = Intent(TIMER_FINISHED)
        var min = runtime / 60_000L
        var sec = runtime % 60_000L

        // Opens the MainActivity when the notification is clicked
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, 0)

        // Broadcast that the timer has started
        broadcastManager.sendBroadcastSync(pongIntent)

        builder = NotificationCompat.Builder(this, getString(R.string.app_name)).apply {
            setSmallIcon(
                if (isOnBreak) R.drawable.ic_hourglass_empty_24dp
                else R.drawable.ic_hourglass_full_24dp
            )
            setContentTitle(
                getString(
                    if (isOnBreak) R.string.pomodoro_break
                    else R.string.pomodoro_working
                )
            )
            setContentText(getString(R.string.notification_time_remaining, min, sec / 1000))
            setContentIntent(pendingIntent)
            setColorized(true)
            setOngoing(true)
            priority = NotificationCompat.PRIORITY_HIGH
            color = ContextCompat.getColor(
                this@TimerService,
                if (isOnBreak) R.color.pomodoroBreak else R.color.pomodoroWorking
            )
        }

        timer = object : CountDownTimer(runtime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                min = millisUntilFinished / 60_000
                sec = millisUntilFinished % 60_000

                builder.setContentText(
                    getString(R.string.notification_time_remaining, min, sec / 1000)
                )
                notificationManager.notify(notificationId, builder.build())
                preferences.edit(commit = true) {
                    putLong(getString(R.string.pref_time_remaining), millisUntilFinished)
                }

                tickIntent.putExtra(TIME_REMAINING, millisUntilFinished)
                sendBroadcast(tickIntent)
            }

            override fun onFinish() {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val pattern = mutableListOf<Long>(0).apply {
                    for (i in 0..7) {
                        add(200L)
                    }
                }

                preferences.edit(commit = true) {
                    isOnBreak = !isOnBreak
                    putBoolean(getString(R.string.pref_is_on_break), isOnBreak)
                    putLong(getString(R.string.pref_time_remaining), 0L)
                }

                tickIntent.putExtra(TIME_REMAINING, 0L)
                vibrator.vibrate(pattern.toLongArray(), -1)
                notificationManager.cancel(notificationId)

                sendBroadcast(tickIntent)
                sendBroadcast(finishIntent)

                startService(Intent(this@TimerService, TimerService::class.java).apply {
                    putExtra(
                        INTENT_EXTRA_RUNTIME,
                        if (isOnBreak) getDefaultBreaktime(preferences)
                        else getDefaultRuntime(preferences)
                    )
                })
            }
        }
        timer.start()
        startForeground(notificationId, builder.build())

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(ServiceEchoReceiver.PONG).apply {
            putExtra(INTENT_EXTRA_RUNNING, false)
        }
        timer.cancel()
        notificationManager.cancel(notificationId)

        // Broadcast that the timer has stopped
        broadcastManager.sendBroadcastSync(intent)
        broadcastManager.unregisterReceiver(serviceReceiver)
    }

    private fun getDefaultRuntime(prefs: SharedPreferences): Long {
        return prefs.getLong(
            getString(R.string.pref_default_runtime),
            resources.getInteger(R.integer.default_runtime).toLong()
        )
    }

    private fun getDefaultBreaktime(prefs: SharedPreferences): Long {
        return prefs.getLong(
            getString(R.string.pref_default_breaktime),
            resources.getInteger(R.integer.default_breaktime).toLong()
        )
    }
}