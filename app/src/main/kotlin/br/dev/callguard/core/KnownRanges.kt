package br.dev.callguard.core

/**
 * Faixas de números conhecidas no Brasil, prontas para bloquear com um toque.
 *
 * Uma advertência que a interface repete e que vale registrar aqui: os números de
 * atendimento das operadoras (1052, 1056, 1058) são os que **você liga**, não
 * necessariamente de onde **elas ligam**. Uma operadora que faz telemarketing costuma
 * discar de um DDD comum ou de uma faixa não geográfica. Bloqueá-los evita que a
 * operadora retorne por esse canal, mas não é o que barra a campanha de vendas — para
 * isso serve a sugestão calculada a partir do registro real de quem ligou.
 *
 * Dados de domínio público (numeração da ANATEL e centrais divulgadas pelas próprias
 * operadoras). Nada aqui é consultado pela rede: é uma tabela no código.
 */
data class KnownRange(
    val digits: String,
    val label: String,
    val description: String,
    val group: Group,
    /** Quando presente, a interface mostra como aviso antes de bloquear. */
    val caveat: String? = null,
) {
    enum class Group(val label: String) {
        TELEMARKETING("Telemarketing"),
        SERVICE("Serviços e 0800"),
        CARRIER("Operadoras"),
    }

    fun toPattern(): NumberPattern = NumberPattern(
        digits = digits,
        label = label,
        kind = NumberPattern.MatchKind.STARTS_WITH,
    )
}

object KnownRanges {

    val all: List<KnownRange> = listOf(
        KnownRange(
            digits = "0303",
            label = "Telemarketing (0303)",
            description = "Código criado pela ANATEL para identificar telemarketing ativo.",
            group = KnownRange.Group.TELEMARKETING,
            caveat = "Deixou de ser obrigatório em agosto de 2025. Parte do setor ainda " +
                "usa, mas quem não quer ser identificado simplesmente não usa mais.",
        ),
        KnownRange(
            digits = "0800",
            label = "Ligação gratuita (0800)",
            description = "Faixa gratuita. Muito usada para retorno de vendas e cobrança.",
            group = KnownRange.Group.SERVICE,
            caveat = "Bancos, planos de saúde e serviços legítimos também usam 0800. " +
                "Bloquear pega os dois.",
        ),
        KnownRange(
            digits = "0300",
            label = "Custo compartilhado (0300)",
            description = "Faixa de custo compartilhado, comum em centrais de atendimento.",
            group = KnownRange.Group.SERVICE,
        ),
        KnownRange(
            digits = "0500",
            label = "Doações (0500)",
            description = "Faixa reservada para captação de doações por telefone.",
            group = KnownRange.Group.SERVICE,
        ),
        KnownRange(
            digits = "4004",
            label = "Serviço 4004",
            description = "Faixa de atendimento de empresas, discada sem DDD.",
            group = KnownRange.Group.SERVICE,
        ),
        KnownRange(
            digits = "4003",
            label = "Serviço 4003",
            description = "Faixa de atendimento de empresas, discada sem DDD.",
            group = KnownRange.Group.SERVICE,
        ),
        KnownRange(
            digits = "3003",
            label = "Serviço 3003",
            description = "Faixa de atendimento de empresas, discada sem DDD.",
            group = KnownRange.Group.SERVICE,
        ),
        KnownRange(
            digits = "1052",
            label = "Claro",
            description = "Central de relacionamento da Claro.",
            group = KnownRange.Group.CARRIER,
            caveat = CAVEAT_CARRIER,
        ),
        KnownRange(
            digits = "1056",
            label = "TIM",
            description = "Central de relacionamento da TIM.",
            group = KnownRange.Group.CARRIER,
            caveat = CAVEAT_CARRIER,
        ),
        KnownRange(
            digits = "1058",
            label = "Vivo",
            description = "Central de relacionamento da Vivo.",
            group = KnownRange.Group.CARRIER,
            caveat = CAVEAT_CARRIER,
        ),
        KnownRange(
            digits = "10315",
            label = "Vivo (10315)",
            description = "Segundo número de atendimento da Vivo.",
            group = KnownRange.Group.CARRIER,
            caveat = CAVEAT_CARRIER,
        ),
    )

    fun byGroup(): Map<KnownRange.Group, List<KnownRange>> = all.groupBy { it.group }

    /** Já existe uma regra cobrindo esta faixa? */
    fun isBlocked(range: KnownRange, patterns: List<NumberPattern>): Boolean =
        patterns.any { it.enabled && it.matches(range.digits) }
}

private const val CAVEAT_CARRIER =
    "Este é o número que VOCÊ liga para a operadora, não necessariamente de onde ela " +
        "liga para você. Campanhas de venda costumam sair de um DDD comum — para essas, " +
        "use as sugestões do seu registro."
