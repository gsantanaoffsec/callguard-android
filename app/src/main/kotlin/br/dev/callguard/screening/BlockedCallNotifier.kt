package br.dev.callguard.screening

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.dev.callguard.R
import br.dev.callguard.core.PhoneNumberMasker
import br.dev.callguard.ui.MainActivity

/**
 * Aviso silencioso de que uma chamada foi bloqueada.
 *
 * Por que existe: sem isto o app age em silencio absoluto e o usuario so descobre um
 * bloqueio abrindo a tela de historico. Isso e aceitavel para telemarketing e ruim para
 * alguem importante que ligou varias vezes seguidas -- especialmente com a regra valendo
 * tambem para contatos salvos.
 *
 * A notificacao usa IMPORTANCE_LOW e `setSilent(true)`: aparece na barra, sem som e sem
 * vibracao. O proposito do app e nao incomodar; o aviso e para ser visto, nao ouvido.
 *
 * O numero aparece mascarado, coerente com o resto do app.
 */
class BlockedCallNotifier(context: Context) {

    private val appContext = context.applicationContext

    /**
     * `false` quando o usuario nao concedeu POST_NOTIFICATIONS (Android 13+) ou desligou
     * as notificacoes do app. Nesse caso simplesmente nao notificamos -- nunca lancamos.
     */
    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    fun notifyBlockedCall(normalizedNumber: String, attemptsInWindow: Int) {
        if (!canNotify()) return

        ensureChannel()

        val openApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_BLOCKED_CALLS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_blocked)
            .setContentTitle(appContext.getString(R.string.notification_blocked_title))
            .setContentText(
                appContext.getString(
                    R.string.notification_blocked_text,
                    PhoneNumberMasker.mask(normalizedNumber),
                    attemptsInWindow,
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Sempre o mesmo id: a notificacao e substituida em vez de empilhar uma por
        // chamada bloqueada. Quem esta sendo perseguido por um insistente nao precisa de
        // vinte avisos na barra.
        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.notification_channel_blocked),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(R.string.notification_channel_blocked_description)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN_BLOCKED_CALLS = "br.dev.callguard.OPEN_BLOCKED_CALLS"

        private const val CHANNEL_ID = "blocked_calls"
        private const val NOTIFICATION_ID = 1001
    }
}
