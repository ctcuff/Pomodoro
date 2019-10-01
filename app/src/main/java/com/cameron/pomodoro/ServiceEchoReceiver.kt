package com.cameron.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Used to send either "ping" or "pong" so we can determine
 * whether the timer is actually running
 * */
class ServiceEchoReceiver : BroadcastReceiver() {
    companion object {
        const val PING = "ping"
        const val PONG = "pong"
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        val pongIntent= Intent(PONG).apply {
            putExtra(TimerService.INTENT_EXTRA_RUNNING, true)
        }
        LocalBroadcastManager
            .getInstance(context!!)
            .sendBroadcastSync(pongIntent)
    }
}