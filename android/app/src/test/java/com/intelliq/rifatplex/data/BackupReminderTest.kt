package com.intelliq.rifatplex.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private val day = 86_400_000L
    private val t0 = 1_800_000_000_000L

    private fun due(
        hasData: Boolean = true, backup: Long = 0, first: Long = t0, reminded: Long = 0, now: Long,
    ) = BackupReminder.due(hasData, backup, first, reminded, now)

    @Test fun neverRemindsWithoutData() {
        assertFalse(due(hasData = false, now = t0 + 100 * day))
    }

    @Test fun neverBackedUpCountsFromFirstTimeSeen() {
        assertFalse(due(now = t0 + 29 * day))
        assertTrue(due(now = t0 + 30 * day))
    }

    @Test fun afterABackupCountsFromThatBackup() {
        val b = t0 + 50 * day
        assertFalse(due(backup = b, now = b + 29 * day))
        assertTrue(due(backup = b, now = b + 30 * day))
    }

    @Test fun backupBeatsFirstSeen() {
        // first seen long ago, but a backup was made yesterday
        assertFalse(due(first = t0, backup = t0 + 99 * day, now = t0 + 100 * day))
    }

    @Test fun quietForAWeekAfterAReminder() {
        val now = t0 + 40 * day
        assertFalse(due(reminded = now - 6 * day, now = now))
        assertTrue(due(reminded = now - 7 * day, now = now))
    }

    @Test fun noBaselineMeansNoReminder() {
        assertFalse(BackupReminder.due(true, 0, 0, 0, t0 + 365 * day))
    }

    @Test fun clockSetBackNeverReminds() {
        assertFalse(due(now = t0 - 5 * day))
        assertFalse(due(reminded = t0 + 90 * day, now = t0 + 40 * day))
    }
}
