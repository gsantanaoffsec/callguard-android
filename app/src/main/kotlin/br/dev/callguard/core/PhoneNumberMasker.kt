package br.dev.callguard.core

/**
 * Mascara numeros para exibicao na lista de bloqueios.
 *
 * O app trata telefones o tempo todo; a tela de historico nao precisa mostrar o numero
 * inteiro para ser util. O usuario pode revelar sob demanda na propria tela.
 */
object PhoneNumberMasker {

    private const val VISIBLE_PREFIX = 5
    private const val VISIBLE_SUFFIX = 2
    private const val MASK_CHAR = '•'

    fun mask(number: String): String {
        val digitsAndPlus = number.trim()
        if (digitsAndPlus.length <= VISIBLE_PREFIX + VISIBLE_SUFFIX) {
            return MASK_CHAR.toString().repeat(digitsAndPlus.length.coerceAtLeast(1))
        }
        val prefix = digitsAndPlus.take(VISIBLE_PREFIX)
        val suffix = digitsAndPlus.takeLast(VISIBLE_SUFFIX)
        val hidden = digitsAndPlus.length - VISIBLE_PREFIX - VISIBLE_SUFFIX
        return prefix + MASK_CHAR.toString().repeat(hidden) + suffix
    }
}
