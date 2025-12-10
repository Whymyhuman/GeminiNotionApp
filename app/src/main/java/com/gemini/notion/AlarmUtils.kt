package com.gemini.notion

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast

object AlarmUtils {
    fun setAlarm(context: Context, hour: Int, minute: Int, message: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true) // Langsung set tanpa konfirmasi user
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Toast.makeText(context, "Alarm set for $hour:$minute - $message", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No Alarm App found", Toast.LENGTH_SHORT).show()
        }
    }
}
