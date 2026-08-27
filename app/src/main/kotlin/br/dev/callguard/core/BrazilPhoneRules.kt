package br.dev.callguard.core

/**
 * Ajuste de canonizacao especifico do Brasil, aplicado DEPOIS do E.164.
 *
 * Motivo: a ANATEL adicionou o nono digito aos celulares brasileiros, mas ainda circulam
 * numeros no formato antigo (+55 DD 8 digitos comecando em 6-9). Sem este passo,
 * "+5511999998888" e "+551199998888" virariam duas chaves diferentes para o mesmo
 * telefone e o contador da janela seria dividido ao meio.
 *
 * Fixos nao sao tocados: comecam em 2-5 e continuam com 8 digitos.
 * Kotlin puro para poder ser testado sem o framework.
 */
object BrazilPhoneRules {

    private const val BRAZIL_CODE = "+55"
    private const val NINTH_DIGIT = "9"

    /** +55 + DDD (2 digitos) + assinante de 8 digitos iniciando em 6..9 (celular antigo). */
    private val LEGACY_MOBILE = Regex("""^\+55(\d{2})([6-9]\d{7})$""")

    /**
     * Devolve o numero em E.164 canonico. Entradas que nao sejam celulares brasileiros
     * no formato antigo voltam inalteradas.
     */
    fun canonicalize(e164: String): String {
        if (!e164.startsWith(BRAZIL_CODE)) return e164
        val match = LEGACY_MOBILE.matchEntire(e164) ?: return e164
        val (areaCode, subscriber) = match.destructured
        return BRAZIL_CODE + areaCode + NINTH_DIGIT + subscriber
    }
}
