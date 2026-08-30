package br.dev.callguard.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * A regra de janela deslizante que vale para uma chamada especifica, ja resolvida.
 *
 * Existe para que o motor nunca precise perguntar "de onde veio esse limite?" no meio da
 * decisao: a resolucao acontece antes, e o resultado carrega a propria origem.
 */
data class CallPolicy(
    val maxAllowedCalls: Int,
    val windowMillis: Long,
    val source: PolicySource,
) {
    val windowMinutes: Int get() = TimeUnit.MILLISECONDS.toMinutes(windowMillis).toInt()

    /**
     * Frase pronta para log e para a interface explicarem a decisao.
     *
     * A formatacao da janela vem de [WindowFormat] e nao daqui: desde que a janela aceita
     * qualquer valor ate 24 h, ter a regra escrita em mais de um lugar produziria "90 min"
     * numa tela e "1 h" em outra para o mesmo numero.
     */
    fun describe(): String {
        val chamadas = if (maxAllowedCalls == 1) "1 chamada" else "$maxAllowedCalls chamadas"
        return "$chamadas em ${WindowFormat.short(windowMinutes)}"
    }
}

/** De onde veio a regra aplicada. Vira motivo no log e rotulo na UI. */
enum class PolicySource(val label: String) {
    GLOBAL("Regra geral"),
    SCHEDULE("Modo noturno"),
    CUSTOM("Regra personalizada"),
}

/**
 * Limite proprio de um numero, mais especifico que qualquer regra geral ou de horario.
 */
data class CustomRule(
    val normalizedNumber: String,
    val maxAllowedCalls: Int,
    val windowMillis: Long,
    val enabled: Boolean = true,
) {
    fun toPolicy(): CallPolicy = CallPolicy(maxAllowedCalls, windowMillis, PolicySource.CUSTOM)
}

/**
 * Politica que vale apenas dentro de uma faixa de horario.
 *
 * Nao existe servico, alarme nem timer por tras disto: quando uma chamada chega, o motor
 * pergunta que horas sao e verifica se o periodo esta ativo. Ligar/desligar e so mudar o
 * relogio do aparelho -- por isso a regra segue o fuso local, e nao um instante em UTC.
 */
data class SchedulePolicy(
    val enabled: Boolean = false,
    /** Minutos desde a meia-noite local. */
    val startMinuteOfDay: Int = DEFAULT_START_MINUTE,
    val endMinuteOfDay: Int = DEFAULT_END_MINUTE,
    val activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val maxAllowedCalls: Int = 1,
    val windowMillis: Long = TimeUnit.MINUTES.toMillis(30),
) {
    fun toPolicy(): CallPolicy = CallPolicy(maxAllowedCalls, windowMillis, PolicySource.SCHEDULE)

    /**
     * O periodo esta valendo neste instante local?
     *
     * Faixas que atravessam a meia-noite (22:00 -> 07:00) sao normais e nao invalidas.
     * Nesse caso o dia da semana considerado e o do INICIO do periodo: as 02:00 de terca,
     * quem manda e a madrugada que comecou na segunda. Sem isso, escolher "segunda a
     * quinta" deixaria a madrugada de segunda desprotegida e a de sexta protegida --
     * exatamente o contrario do que o usuario pediu.
     */
    fun isActiveAt(now: LocalDateTime): Boolean {
        if (!enabled) return false
        val minuto = now.hour * 60 + now.minute

        // Inicio == fim significa periodo vazio, nao periodo de 24 h.
        if (startMinuteOfDay == endMinuteOfDay) return false

        val atravessaMeiaNoite = startMinuteOfDay > endMinuteOfDay
        return if (atravessaMeiaNoite) {
            when {
                minuto >= startMinuteOfDay -> now.dayOfWeek in activeDays
                minuto < endMinuteOfDay -> now.dayOfWeek.minus(1) in activeDays
                else -> false
            }
        } else {
            minuto >= startMinuteOfDay && minuto < endMinuteOfDay && now.dayOfWeek in activeDays
        }
    }

    companion object {
        const val DEFAULT_START_MINUTE = 22 * 60
        const val DEFAULT_END_MINUTE = 7 * 60
    }
}
