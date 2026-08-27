package br.dev.callguard.core

/**
 * Converte um numero cru em uma chave estavel de agrupamento.
 *
 * Declarado no `core` como interface para que a politica e os testes nao dependam de
 * `android.telephony.PhoneNumberUtils`. A implementacao real vive em `phone/`.
 */
fun interface PhoneNumberNormalizer {

    /** Devolve a chave canonica, ou `null` quando o numero nao e utilizavel. */
    fun normalize(rawNumber: String?): String?
}
