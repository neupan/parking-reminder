package com.neupan.parking_reminder.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

class RingtonePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAlarmUriResolved(context: Context): Uri {
        val saved = prefs.getString(KEY_ALARM_URI, null)
        if (saved != null) return Uri.parse(saved)
        return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: Uri.parse("content://settings/system/alarm_alert")
    }

    fun setAlarmUri(uri: Uri) {
        prefs.edit().putString(KEY_ALARM_URI, uri.toString()).apply()
    }

    fun getAlarmTitle(context: Context): String {
        val uri = getAlarmUriResolved(context)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        return ringtone?.getTitle(context) ?: "默认闹钟"
    }

    companion object {
        private const val PREFS_NAME = "reminder_ringtone"
        private const val KEY_ALARM_URI = "alarm_uri"
    }
}
