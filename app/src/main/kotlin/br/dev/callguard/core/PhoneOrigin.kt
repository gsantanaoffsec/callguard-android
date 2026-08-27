package br.dev.callguard.core

/**
 * O que da para dizer sobre a procedencia de um numero **sem rede e sem permissao**.
 *
 * Deliberadamente NAO existe campo "operadora". Desde a portabilidade numerica (Brasil,
 * 2008) o prefixo deixou de indicar a operadora: um numero originalmente Vivo pode estar
 * na Claro hoje. Descobrir a operadora atual exige consultar a base da ABR Telecom, o que
 * pede internet e credencial -- fora do escopo de um app que nao acessa a rede. Preencher
 * esse campo pelo prefixo seria informacao errada com cara de certa.
 */
data class PhoneOrigin(
    val country: String?,
    val areaCode: String?,
    /** Estado/regiao do DDD, quando reconhecido. */
    val region: String?,
    val lineType: LineType,
) {
    /** Uma linha curta para o log e para a tela. */
    fun describe(): String = buildList {
        region?.let { add(it) }
        if (region == null) country?.let { add(it) }
        add(lineType.label)
    }.joinToString(" · ")

    enum class LineType(val label: String) {
        MOBILE("celular"),
        LANDLINE("fixo"),
        TOLL_FREE("0800 / gratuito"),
        SHARED_COST("serviço 0300/0500"),
        SPECIAL_SERVICE("serviço especial"),
        SHORT_CODE("número curto"),
        INTERNATIONAL("internacional"),
        UNKNOWN("tipo desconhecido"),
    }

    companion object {

        private const val BRAZIL = "+55"

        /**
         * DDDs brasileiros conforme o Plano Geral de Codigos Nacionais da ANATEL.
         * 67 codigos. O 61 cobre o Distrito Federal e o entorno goiano.
         */
        private val BRAZIL_AREA_CODES: Map<String, String> = mapOf(
            "11" to "São Paulo · SP", "12" to "Vale do Paraíba · SP",
            "13" to "Baixada Santista · SP", "14" to "Bauru · SP",
            "15" to "Sorocaba · SP", "16" to "Ribeirão Preto · SP",
            "17" to "São José do Rio Preto · SP", "18" to "Presidente Prudente · SP",
            "19" to "Campinas · SP",
            "21" to "Rio de Janeiro · RJ", "22" to "Campos dos Goytacazes · RJ",
            "24" to "Volta Redonda · RJ",
            "27" to "Vitória · ES", "28" to "Cachoeiro de Itapemirim · ES",
            "31" to "Belo Horizonte · MG", "32" to "Juiz de Fora · MG",
            "33" to "Governador Valadares · MG", "34" to "Uberlândia · MG",
            "35" to "Poços de Caldas · MG", "37" to "Divinópolis · MG",
            "38" to "Montes Claros · MG",
            "41" to "Curitiba · PR", "42" to "Ponta Grossa · PR",
            "43" to "Londrina · PR", "44" to "Maringá · PR",
            "45" to "Foz do Iguaçu · PR", "46" to "Francisco Beltrão · PR",
            "47" to "Joinville · SC", "48" to "Florianópolis · SC", "49" to "Chapecó · SC",
            "51" to "Porto Alegre · RS", "53" to "Pelotas · RS",
            "54" to "Caxias do Sul · RS", "55" to "Santa Maria · RS",
            "61" to "Brasília · DF", "62" to "Goiânia · GO", "64" to "Rio Verde · GO",
            "63" to "Palmas · TO",
            "65" to "Cuiabá · MT", "66" to "Rondonópolis · MT",
            "67" to "Campo Grande · MS",
            "68" to "Rio Branco · AC", "69" to "Porto Velho · RO",
            "71" to "Salvador · BA", "73" to "Itabuna · BA", "74" to "Juazeiro · BA",
            "75" to "Feira de Santana · BA", "77" to "Barreiras · BA",
            "79" to "Aracaju · SE",
            "81" to "Recife · PE", "87" to "Petrolina · PE",
            "82" to "Maceió · AL", "83" to "João Pessoa · PB", "84" to "Natal · RN",
            "85" to "Fortaleza · CE", "88" to "Juazeiro do Norte · CE",
            "86" to "Teresina · PI", "89" to "Picos · PI",
            "91" to "Belém · PA", "93" to "Santarém · PA", "94" to "Marabá · PA",
            "92" to "Manaus · AM", "97" to "Coari · AM",
            "95" to "Boa Vista · RR", "96" to "Macapá · AP",
            "98" to "São Luís · MA", "99" to "Imperatriz · MA",
        )

        /** Prefixos de servico discados sem DDD. */
        private val SERVICE_PREFIXES: List<Pair<String, LineType>> = listOf(
            "0800" to LineType.TOLL_FREE,
            "0300" to LineType.SHARED_COST,
            "0500" to LineType.SHARED_COST,
            "4004" to LineType.SPECIAL_SERVICE,
            "4003" to LineType.SPECIAL_SERVICE,
            "3003" to LineType.SPECIAL_SERVICE,
        )

        fun of(normalizedNumber: String?): PhoneOrigin {
            val numero = normalizedNumber?.trim().orEmpty()
            if (numero.isEmpty()) {
                return PhoneOrigin(null, null, null, LineType.UNKNOWN)
            }

            val digitos = numero.filter { it.isDigit() }

            SERVICE_PREFIXES.firstOrNull { digitos.startsWith(it.first) }?.let { (_, tipo) ->
                return PhoneOrigin("Brasil", null, null, tipo)
            }

            if (digitos.length in 3..4 && !numero.startsWith("+")) {
                return PhoneOrigin(null, null, null, LineType.SHORT_CODE)
            }

            if (numero.startsWith(BRAZIL)) {
                return brazilian(digitos.removePrefix("55"))
            }

            if (numero.startsWith("+")) {
                return PhoneOrigin("Exterior", null, null, LineType.INTERNATIONAL)
            }

            // Sem "+": tratamos como nacional, que e o caso de uso do app.
            return brazilian(digitos)
        }

        private fun brazilian(nacional: String): PhoneOrigin {
            if (nacional.length < 10) {
                return PhoneOrigin("Brasil", null, null, LineType.UNKNOWN)
            }
            val ddd = nacional.take(2)
            val assinante = nacional.drop(2)
            val tipo = when {
                assinante.length == 9 && assinante.startsWith("9") -> LineType.MOBILE
                assinante.length == 8 && assinante.first() in '2'..'5' -> LineType.LANDLINE
                assinante.length == 8 && assinante.first() in '6'..'9' -> LineType.MOBILE
                else -> LineType.UNKNOWN
            }
            return PhoneOrigin(
                country = "Brasil",
                areaCode = ddd,
                region = BRAZIL_AREA_CODES[ddd],
                lineType = tipo,
            )
        }
    }
}
