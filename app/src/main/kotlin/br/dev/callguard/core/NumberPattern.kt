package br.dev.callguard.core

/**
 * Uma regra que casa com uma FAIXA de números, e não com um número só.
 *
 * Existe por um limite concreto do Android: o sistema **apaga** o nome de quem liga antes
 * de entregar a chamada a um serviço de filtragem. Em
 * `ParcelableCallUtils.toParcelableCallForScreening`, `callerDisplayName`,
 * `contactDisplayName` e `name` chegam todos nulos, e só o número passa — e mesmo ele
 * apenas quando a apresentação é permitida. Então "bloquear tudo que aparece como Claro"
 * é impossível por construção: o app nunca vê essa palavra.
 *
 * O que sobra, e resolve o mesmo problema, é a forma do número. Quem liga em volume não
 * usa um número: usa uma faixa. Bloquear o prefixo pega a faixa inteira, inclusive os
 * números que ainda não ligaram.
 */
data class NumberPattern(
    /** Só dígitos. Normalizado na criação para que a comparação nunca dependa de formato. */
    val digits: String,
    val label: String,
    val kind: MatchKind = MatchKind.STARTS_WITH,
    val enabled: Boolean = true,
) {
    enum class MatchKind(val label: String) {
        /** O número COMEÇA com estes dígitos. É como faixas de telefonia são organizadas. */
        STARTS_WITH("Começa com"),

        /** Os dígitos aparecem em qualquer posição. Rede de arrasto; usar com cuidado. */
        CONTAINS("Contém"),
    }

    /**
     * Este padrão pega o número dado?
     *
     * A comparação acontece contra DUAS formas do mesmo número, e isso não é preciosismo:
     * o normalizador produz `+5511999998888` para um celular comum, mas devolve só os
     * dígitos (`03031234567`) para códigos não geográficos, que não são E.164 válidos.
     * Um padrão `0303` digitado pela pessoa jamais casaria com a primeira forma, e um
     * padrão `11` jamais casaria com a segunda. Testar as duas é o que faz o recurso se
     * comportar como a pessoa espera, sem ela precisar saber o que é E.164.
     */
    fun matches(normalizedNumber: String?): Boolean {
        if (!enabled || digits.isEmpty()) return false
        val numero = normalizedNumber?.trim().orEmpty()
        if (numero.isEmpty()) return false

        return candidateForms(numero).any { candidato ->
            when (kind) {
                MatchKind.STARTS_WITH -> candidato.startsWith(digits)
                MatchKind.CONTAINS -> candidato.contains(digits)
            }
        }
    }

    /** Frase curta para a interface. */
    fun describe(): String = "${kind.label} $digits"

    /**
     * Quão perigoso é este padrão.
     *
     * Um prefixo de dois dígitos é um DDD inteiro: bloquearia São Paulo. A interface usa
     * isto para avisar ANTES de salvar, porque o estrago só apareceria depois, na forma
     * de ligações que deixaram de tocar sem a pessoa entender por quê.
     */
    fun breadth(): Breadth = when {
        digits.length <= 2 -> Breadth.VERY_BROAD
        digits.length <= 3 -> Breadth.BROAD
        else -> Breadth.NARROW
    }

    enum class Breadth { VERY_BROAD, BROAD, NARROW }

    companion object {

        /**
         * As formas de um número contra as quais se compara.
         *
         * Duas, e isso não é preciosismo: o normalizador produz `+5511999998888` para um
         * celular comum, mas devolve só os dígitos (`03031234567`) para códigos não
         * geográficos, que não são E.164 válidos. Um padrão `0303` jamais casaria com a
         * primeira forma, e um padrão `11` jamais casaria com a segunda.
         *
         * O `55` só é removido quando o número **declara** o Brasil com `+55`. Removê-lo
         * de qualquer sequência transformaria `5512345678` em `12345678` e criaria um
         * casamento falso com o DDD 12.
         */
        fun candidateForms(normalizedNumber: String?): List<String> {
            val numero = normalizedNumber?.trim().orEmpty()
            if (numero.isEmpty()) return emptyList()
            val completos = numero.filter { it.isDigit() }
            if (completos.isEmpty()) return emptyList()
            val nacional = if (numero.startsWith("+55")) completos.removePrefix("55") else null
            return listOfNotNull(completos, nacional).distinct()
        }

        /** Menor padrão aceito. Um dígito só casaria com quase tudo. */
        const val MIN_DIGITS = 2
        const val MAX_DIGITS = 15

        /**
         * Constrói a partir do que a pessoa digitou, aceitando qualquer formatação.
         *
         * @return `null` quando não sobra dígito suficiente para formar uma regra.
         */
        fun from(
            raw: String,
            label: String,
            kind: MatchKind = MatchKind.STARTS_WITH,
        ): NumberPattern? {
            val digitos = raw.filter { it.isDigit() }
            if (digitos.length !in MIN_DIGITS..MAX_DIGITS) return null
            return NumberPattern(
                digits = digitos,
                label = label.trim().ifBlank { digitos },
                kind = kind,
            )
        }
    }
}

/**
 * O primeiro padrão que pega o número, ou `null`.
 *
 * Kotlin puro e fora da classe porque quem decide é a política; a lista vive num cache em
 * memória alimentado pelo banco.
 */
fun List<NumberPattern>.firstMatching(normalizedNumber: String?): NumberPattern? =
    firstOrNull { it.matches(normalizedNumber) }
