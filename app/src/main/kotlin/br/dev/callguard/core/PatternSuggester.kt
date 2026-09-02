package br.dev.callguard.core

/**
 * Uma faixa que o próprio registro do usuário sugere bloquear.
 *
 * @param digits o prefixo proposto
 * @param distinctNumbers quantos números DIFERENTES do registro cairiam nele
 * @param samples alguns desses números, para a tela mostrar (mascarados) antes de confirmar
 */
data class PatternSuggestion(
    val digits: String,
    val distinctNumbers: Int,
    val samples: List<String>,
)

/**
 * Descobre faixas a partir de quem realmente ligou.
 *
 * É a parte que resolve o problema de verdade. Um catálogo de números conhecidos ajuda
 * pouco contra uma operadora fazendo campanha: ela não liga do número da central, liga de
 * um DDD comum, e a faixa que ela usa varia por região e por campanha. Ninguém pode
 * enumerar isso no código — mas o aparelho da pessoa já tem a resposta, no registro de
 * quem ligou.
 *
 * Kotlin puro e sem rede: só olha o que já está no banco local.
 */
object PatternSuggester {

    /** Abaixo disso não é padrão, é coincidência. */
    const val DEFAULT_MIN_DISTINCT = 2

    /**
     * Comprimentos testados.
     *
     * Começa em 4 de propósito. Um prefixo de 2 é um DDD inteiro e de 3 é meia região —
     * sugerir isso seria oferecer um tiro no pé com aparência de recomendação.
     */
    val PREFIX_LENGTHS = 4..7

    fun suggest(
        numbers: List<String>,
        protectedNumbers: Set<String> = emptySet(),
        existingPatterns: List<NumberPattern> = emptyList(),
        minDistinct: Int = DEFAULT_MIN_DISTINCT,
        limit: Int = 5,
    ): List<PatternSuggestion> {
        val distintos = numbers.filter { it.isNotBlank() }.distinct()
        if (distintos.size < minDistinct) return emptyList()

        // Prefixo -> números que cairiam nele.
        //
        // UMA forma por número, a nacional quando existe. Gerar prefixos das duas formas
        // fazia o algoritmo propor "5511400" em vez de "114004": tecnicamente equivalente,
        // porque a comparação testa as duas, mas ilegível para quem pensa em DDD — e uma
        // sugestão que a pessoa não entende ela não confere, ela só aceita.
        val porPrefixo = LinkedHashMap<String, MutableSet<String>>()
        distintos.forEach { numero ->
            val forma = NumberPattern.candidateForms(numero).lastOrNull() ?: return@forEach
            PREFIX_LENGTHS.forEach { tamanho ->
                if (forma.length > tamanho) {
                    porPrefixo.getOrPut(forma.take(tamanho)) { linkedSetOf() }.add(numero)
                }
            }
        }

        val candidatos = porPrefixo
            .filter { (_, casados) -> casados.size >= minDistinct }
            .filterNot { (prefixo, _) -> pegaProtegido(prefixo, protectedNumbers) }
            .filterNot { (prefixo, _) -> jaCoberto(prefixo, existingPatterns) }

        // Entre prefixos que pegam exatamente o mesmo conjunto, fica o MAIS LONGO: ele é
        // igualmente eficaz e menos abrangente, então tem menos chance de pegar alguém
        // que a pessoa queria receber.
        val maisEspecificos = candidatos
            .entries
            .groupBy { it.value }
            .map { (_, entradas) -> entradas.maxBy { it.key.length } }

        return maisEspecificos
            .sortedWith(
                compareByDescending<Map.Entry<String, MutableSet<String>>> { it.value.size }
                    .thenByDescending { it.key.length },
            )
            .take(limit)
            .map { (prefixo, casados) ->
                PatternSuggestion(
                    digits = prefixo,
                    distinctNumbers = casados.size,
                    samples = casados.take(3).toList(),
                )
            }
    }

    /**
     * Um prefixo que pegaria um número da lista de permitidos nunca é sugerido.
     *
     * Sugerir uma regra que contradiz uma decisão explícita do usuário seria o app
     * discordando dele — e, pior, discordando com aparência de recomendação.
     */
    private fun pegaProtegido(prefixo: String, protegidos: Set<String>): Boolean {
        if (protegidos.isEmpty()) return false
        val padrao = NumberPattern(prefixo, prefixo)
        return protegidos.any { padrao.matches(it) }
    }

    private fun jaCoberto(prefixo: String, existentes: List<NumberPattern>): Boolean =
        existentes.any { it.enabled && it.matches(prefixo) }
}
