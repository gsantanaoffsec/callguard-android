package br.dev.callguard.core

/**
 * Como uma janela de tempo é escrita na interface.
 *
 * Virou peça própria quando a janela deixou de ser uma lista curta de opções redondas e
 * passou a aceitar qualquer valor até 24 h: "90 min" é tecnicamente certo e humanamente
 * ruim, e "1 h" some com a meia hora. Formatar isso em três lugares diferentes acabaria
 * com três respostas diferentes para o mesmo número.
 *
 * Kotlin puro, com teste: é texto que o usuário lê para decidir, não enfeite.
 */
object WindowFormat {

    /** Limites do que a interface aceita. O motor aceitaria mais; a UI é curadora. */
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 24 * 60

    /**
     * Forma curta, para caber num botão: `45 min`, `2 h`, `1 h 30`.
     *
     * A meia hora não vira "1,5 h" de propósito — número quebrado com vírgula obriga a
     * pessoa a converter de cabeça, que é exatamente o trabalho que o rótulo deveria
     * poupar.
     */
    fun short(minutes: Int): String {
        val m = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val horas = m / 60
        val resto = m % 60
        return when {
            horas == 0 -> "$resto min"
            resto == 0 -> "$horas h"
            else -> "$horas h $resto"
        }
    }

    /** Forma por extenso, para frases: `45 minutos`, `2 horas`, `1 hora e 30 minutos`. */
    fun long(minutes: Int): String {
        val m = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val horas = m / 60
        val resto = m % 60
        val parteHora = when (horas) {
            0 -> null
            1 -> "1 hora"
            else -> "$horas horas"
        }
        val parteMinuto = when (resto) {
            0 -> null
            1 -> "1 minuto"
            else -> "$resto minutos"
        }
        return listOfNotNull(parteHora, parteMinuto).joinToString(" e ").ifEmpty { "1 minuto" }
    }

    /** Quantas horas inteiras cabem no valor, para alimentar o seletor personalizado. */
    fun wholeHours(minutes: Int): Int = (minutes / 60).coerceAtLeast(1)

    /** `true` quando o valor não está entre as opções prontas e precisa aparecer como personalizado. */
    fun isCustom(minutes: Int, presets: List<Int>): Boolean = minutes !in presets
}
