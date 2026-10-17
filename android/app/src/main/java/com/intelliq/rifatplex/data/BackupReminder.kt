package com.intelliq.rifatplex.data

import android.content.Context

/**
 * "Back up your papers" reminder. Papers live only on this phone, so the app asks now and then.
 * Pure rule in [BackupReminder.due] (JVM tests); the times are kept in [BackupReminderStore].
 *
 * - Due [INTERVAL_DAYS] days after the last backup, or after the first time the phone held data when there never was one.
 * - After a reminder it stays quiet for [REPEAT_DAYS] days, so a user who ignores it is not nagged on every launch.
 * - A phone clock set back never produces a reminder.
 * - "Backed up" = the backup mail was opened in the mail app. The app cannot see whether Send was pressed.
 */
object BackupReminder {
    const val INTERVAL_DAYS = 30
    const val REPEAT_DAYS = 7
    private const val DAY = 86_400_000L

    fun due(hasData: Boolean, lastBackupAt: Long, firstSeenAt: Long, lastRemindedAt: Long, now: Long): Boolean {
        if (!hasData) return false
        val baseline = if (lastBackupAt > 0) lastBackupAt else firstSeenAt
        if (baseline <= 0 || now < baseline) return false
        if (now - baseline < INTERVAL_DAYS * DAY) return false
        if (lastRemindedAt > 0 && (now < lastRemindedAt || now - lastRemindedAt < REPEAT_DAYS * DAY)) return false
        return true
    }
}

class BackupReminderStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("intelliq_backup", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var instance: BackupReminderStore? = null
        fun get(context: Context): BackupReminderStore =
            instance ?: synchronized(this) { instance ?: BackupReminderStore(context.applicationContext).also { instance = it } }
    }

    fun markBackedUp(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("lastBackupAt", now).apply()
    }

    fun markReminded(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("lastRemindedAt", now).apply()
    }

    /** The first call with data on the phone only starts the 30 days; it never reminds. */
    fun shouldRemind(hasData: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        if (!hasData) return false
        if (prefs.getLong("firstSeenAt", 0L) == 0L) prefs.edit().putLong("firstSeenAt", now).apply()
        return BackupReminder.due(
            hasData, prefs.getLong("lastBackupAt", 0L), prefs.getLong("firstSeenAt", 0L), prefs.getLong("lastRemindedAt", 0L), now,
        )
    }
}
