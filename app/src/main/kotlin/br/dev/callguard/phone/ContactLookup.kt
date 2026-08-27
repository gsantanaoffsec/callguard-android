package br.dev.callguard.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Consulta se um numero pertence a agenda.
 *
 * Ponto importante do desenho, porque muda o que o app precisa pedir:
 *
 * O proprio Telecom decide se nos entrega a chamada. Em
 * `CallScreeningServiceFilter.startFilterLookup` o AOSP pula o bind ao servico quando o
 * numero esta na agenda E o app nao tem READ_CONTACTS concedida. Ou seja:
 *
 *  - Modo 1 (nunca bloquear contatos, padrao): basta NAO ter READ_CONTACTS. Chamadas de
 *    contatos nem chegam ate nos. E o sistema garantindo o comportamento, sem permissao
 *    e sem codigo -- o melhor resultado possivel em privacidade.
 *  - Modo 2 (aplicar tambem aos contatos): so e possivel com READ_CONTACTS concedida.
 *
 * Esta classe so faz falta num caso: o usuario concedeu READ_CONTACTS para o Modo 2 e
 * depois voltou para o Modo 1 sem revogar a permissao. Ai as chamadas de contatos
 * continuam chegando e somos nos que precisamos deixa-las passar.
 */
class ContactLookup(context: Context) {

    private val appContext = context.applicationContext

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * `PhoneLookup` e um indice mantido pelo provider justamente para casar telefones
     * em formatos diferentes; e uma consulta rapida, adequada ao orcamento do screening.
     *
     * Devolve `false` sem tocar no provider quando nao temos a permissao.
     */
    fun isSavedContact(rawNumber: String): Boolean {
        if (rawNumber.isBlank() || !hasReadContactsPermission()) return false
        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber),
        )
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null,
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}
