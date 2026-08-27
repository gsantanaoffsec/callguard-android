package br.dev.callguard.core

/**
 * Codigos MMI de identificacao de chamada (CLIR), padronizados em 3GPP TS 22.030.
 *
 * `#31#<numero>` pede a rede para NAO apresentar o seu numero naquela chamada.
 * No AOSP isso e reconhecido em `GsmMmiCode`: a acao "#" sobre o codigo de servico "31"
 * vira `CommandsInterface.CLIR_INVOCATION` e a camada de telefonia disca o numero real
 * com esse modo -- o prefixo nao vai como digitos para a rede.
 *
 * `*31#<numero>` faz o contrario (forca a apresentacao), util para quem deixou a
 * ocultacao permanente ligada e quer mostrar o numero em uma ligacao especifica.
 *
 * Isto oculta o SEU proprio numero. Nao tem nada a ver com falsificar o numero de
 * outra pessoa, o que nao e possivel por API publica e nao e o que este codigo faz.
 *
 * Kotlin puro para poder ser testado sem o framework.
 */
object CallerIdCodes {

    const val HIDE_CALLER_ID_PREFIX = "#31#"
    const val SHOW_CALLER_ID_PREFIX = "*31#"

    /**
     * Mantem apenas o que faz sentido discar: digitos e um "+" inicial.
     *
     * Espacos, parenteses, hifens e pontos sao formatacao visual e nao vao para a rede.
     */
    fun sanitizeDialNumber(rawNumber: String): String {
        val trimmed = rawNumber.trim()
        val temPlusInicial = trimmed.startsWith("+")
        val digitos = trimmed.filter { it.isDigit() }
        if (digitos.isEmpty()) return ""
        return if (temPlusInicial) "+$digitos" else digitos
    }

    /**
     * Monta a string a ser discada com o numero oculto.
     *
     * @return `null` quando o texto nao contem um numero utilizavel.
     */
    fun buildHiddenCallerIdNumber(rawNumber: String): String? {
        val numero = sanitizeDialNumber(rawNumber)
        if (numero.isEmpty()) return null
        return HIDE_CALLER_ID_PREFIX + numero
    }
}
