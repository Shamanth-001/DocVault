package com.personal.docvault

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.docvault.data.ExpiryDue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Expiry alerts must work while the vault is locked, so the expiry dates and the family member's
 * chosen label are kept outside it, with no document name, number or type. The alert text says
 * only whose document it is and how many days are left. Excluded from Android backups by the
 * manifest.
 *
 * Android doesn't let an app stay above newer notifications. So an alert raised in the background
 * stays until DocVault is unlocked, and pops up again each morning until then.
 */
object Notifier {
    // Android can't raise an existing channel's importance, so high priority needs a new channel
    private const val OLD_CHANNEL = "expiry"
    private const val CHANNEL = "expiry_high"
    private const val ALERT_ID = 1
    private const val TEST_ID = 2

    private fun prefs(context: Context) = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    private fun readDates(raw: String?) = raw.orEmpty().split(',').filter(String::isNotBlank)
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun enabled(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Called from the background or at launch, while the vault may be locked. */
    fun start(context: Context, repeatPending: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Expiry reminders", NotificationManager.IMPORTANCE_HIGH),
        )
        check(context, sticky = true, repeatPending = repeatPending)
        schedule(context)
    }

    /** Called only while the vault is open, so the user has now seen their documents. */
    fun sync(context: Context, expiries: List<Pair<String, String>>) {
        prefs(context).edit()
            .putString("dates", expiries.joinToString(",") { it.first })
            .putString("labels", ExpiryDue.encodeLabels(expiries))
            .remove("pending")
            .apply()
        NotificationManagerCompat.from(context).cancel(ALERT_ID)
        check(context, sticky = false, repeatPending = false)
    }

    /** Runs again each morning at 9. Inexact on purpose, since Android may delay it to save battery. */
    fun schedule(context: Context) {
        val now = LocalDate.now().atTime(9, 0)
        val next = if (now.isAfter(LocalDateTime.now())) now else now.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    private fun check(context: Context, sticky: Boolean, repeatPending: Boolean) {
        // Wait until alerts can be shown, so a denied permission can't silently use one up
        if (!enabled(context)) return

        val p = prefs(context)
        val today = LocalDate.now()
        val dates = readDates(p.getString("dates", ""))
        val sent = p.getString("sent", "").orEmpty().split(',').filter(String::isNotBlank)
        val result = ExpiryDue.check(dates, sent, today)
        p.edit().putString("sent", result.sent.joinToString(",")).apply()
        val labels = ExpiryDue.decodeLabels(p.getString("labels", "").orEmpty())

        if (!sticky) {
            ExpiryDue.message(result.daysLeft)?.let {
                show(context, ExpiryDue.withName(it, result.dates, labels), ALERT_ID, sticky = false)
            }
            return
        }

        val pending = ExpiryDue.stillPending(readDates(p.getString("pending", "")) + result.dates, dates, today)
        p.edit().putString("pending", pending.joinToString(",")).apply()
        val daysLeft = if (repeatPending) ExpiryDue.daysLeft(pending, today) else result.daysLeft
        val shownDates = if (repeatPending) pending else result.dates
        ExpiryDue.message(daysLeft)?.let {
            show(context, ExpiryDue.withName(it, shownDates, labels), ALERT_ID, sticky = true)
        }
        if (pending.isEmpty()) NotificationManagerCompat.from(context).cancel(ALERT_ID)
    }

    fun sendTest(context: Context) = show(context, "Expiry alerts are working.", TEST_ID, sticky = false)

    private fun show(context: Context, text: String, id: Int, sticky: Boolean) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DocVault")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(open)
            .setOngoing(sticky)
            .setAutoCancel(!sticky)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications are turned off; Settings offers a way to turn them on
        }
    }
}

/** Fires each morning, and after a restart or app update, which clear scheduled alarms. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Notifier.start(context, repeatPending = true)
}
