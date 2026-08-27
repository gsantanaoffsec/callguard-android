package br.dev.callguard.phone

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import br.dev.callguard.core.BrazilPhoneRules
import br.dev.callguard.core.PhoneNumberNormalizer
import java.util.Locale

/**
 * Normalizacao usando a API oficial do Android.
 *
 * `PhoneNumberUtils.formatNumberToE164` e a porta de entrada publica para a
 * libphonenumber que ja vem embarcada no sistema. Ela cuida de espacos, hifens,
 * parenteses, prefixo "+", codigo de pais e codigo de area sem que precisemos empilhar
 * `replace()` -- e, o mais importante, ela sabe que um numero e invalido.
 *
 * Cadeia de tentativas:
 *  1. E.164 pela regiao atual (ex.: "11 99999-9999" + BR -> "+5511999999999");
 *  2. canonizacao do nono digito brasileiro;
 *  3. se o numero nao for valido para a regiao, cai para `normalizeNumber`, que devolve
 *     so os digitos -- suficiente como chave de agrupamento, ainda que menos precisa;
 *  4. ultimo recurso: o proprio texto sem espacos.
 *
 * Nenhuma etapa exige permissao: `getNetworkCountryIso`/`getSimCountryIso` sao publicos
 * e sem `@RequiresPermission`.
 */
class TelephonyPhoneNumberNormalizer(context: Context) : PhoneNumberNormalizer {

    private val appContext = context.applicationContext

    @Volatile
    private var cachedRegion: String? = null

    override fun normalize(rawNumber: String?): String? {
        val raw = rawNumber?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val region = defaultRegion()

        val e164 = runCatching { PhoneNumberUtils.formatNumberToE164(raw, region) }.getOrNull()
        if (!e164.isNullOrBlank()) return BrazilPhoneRules.canonicalize(e164)

        val digits = runCatching { PhoneNumberUtils.normalizeNumber(raw) }.getOrNull()
        if (!digits.isNullOrBlank()) return digits

        return raw.filterNot { it.isWhitespace() }.ifBlank { null }
    }

    /**
     * Regiao ISO 3166-1 alpha-2 em maiusculas.
     *
     * Rede antes do SIM porque em roaming o numero chega no formato local da rede.
     * O `Locale` do aparelho fecha a lista e "BR" e o ultimo fallback, ja que o app
     * nasceu para uso no Brasil.
     */
    private fun defaultRegion(): String {
        cachedRegion?.let { return it }
        val telephony = appContext.getSystemService(TelephonyManager::class.java)
        val region = sequenceOf(
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            runCatching { telephony?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        ).firstOrNull { !it.isNullOrBlank() && it.length == 2 }
            ?.uppercase(Locale.ROOT)
            ?: FALLBACK_REGION
        cachedRegion = region
        return region
    }

    private companion object {
        const val FALLBACK_REGION = "BR"
    }
}
