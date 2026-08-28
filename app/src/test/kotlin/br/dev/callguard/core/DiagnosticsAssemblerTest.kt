package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * O laudo diz a verdade sobre o estado do app?
 *
 * Esta e a unica tela cujo erro e silencioso nos dois sentidos: dizer "tudo certo" com a
 * protecao desligada engana o usuario, e dizer "quebrado" com tudo funcionando o faz
 * mexer no que estava bom. Por isso cada condicao tem teste.
 */
class DiagnosticsAssemblerTest {

    private val regraPadrao = CallPolicy(
        maxAllowedCalls = 3,
        windowMillis = TimeUnit.MINUTES.toMillis(15),
        source = PolicySource.GLOBAL,
    )

    private fun entrada(
        roleAvailable: Boolean = true,
        roleHeld: Boolean = true,
        protectionEnabled: Boolean = true,
        applyToContacts: Boolean = false,
        hasContactsPermission: Boolean = false,
        notifyOnBlock: Boolean = true,
        canPostNotifications: Boolean = true,
        ignoringBatteryOptimizations: Boolean? = true,
        hasInternetPermission: Boolean = false,
        activePolicy: CallPolicy = regraPadrao,
        scheduleActiveNow: Boolean = false,
        customRuleCount: Int = 0,
        blocklistCount: Int = 0,
        allowlistCount: Int = 0,
    ) = DiagnosticsInput(
        roleAvailable = roleAvailable,
        roleHeld = roleHeld,
        protectionEnabled = protectionEnabled,
        applyToContacts = applyToContacts,
        hasContactsPermission = hasContactsPermission,
        notifyOnBlock = notifyOnBlock,
        canPostNotifications = canPostNotifications,
        ignoringBatteryOptimizations = ignoringBatteryOptimizations,
        hasInternetPermission = hasInternetPermission,
        activePolicy = activePolicy,
        scheduleActiveNow = scheduleActiveNow,
        customRuleCount = customRuleCount,
        blocklistCount = blocklistCount,
        allowlistCount = allowlistCount,
    )

    private fun laudo(input: DiagnosticsInput) = DiagnosticsReport(
        checks = DiagnosticsAssembler.build(input),
        storage = StorageStats(0, 0, 0, 0, 0, 0, 0, 3),
        activePolicy = input.activePolicy,
    )

    @Test
    fun `configuracao saudavel nao produz nenhum bloqueio`() {
        val r = laudo(entrada())
        assertTrue(r.isProtecting)
        assertEquals(CheckLevel.OK, r.worstLevel)
    }

    @Test
    fun `sem o papel de filtro o laudo diz que nao esta protegendo`() {
        val r = laudo(entrada(roleHeld = false))
        assertFalse(r.isProtecting)
        val papel = r.checks.first { it.title == "Filtro de chamadas" }
        assertEquals(CheckLevel.BLOCKING, papel.level)
        assertEquals(DiagnosticFix.REQUEST_ROLE, papel.fix)
    }

    @Test
    fun `aparelho sem o papel disponivel nao oferece correcao`() {
        val papel = DiagnosticsAssembler.build(entrada(roleAvailable = false))
            .first { it.title == "Filtro de chamadas" }
        assertEquals(CheckLevel.BLOCKING, papel.level)
        // Nao ha o que corrigir: o botao seria uma promessa que o aparelho nao cumpre.
        assertNull(papel.fix)
    }

    @Test
    fun `protecao desligada bloqueia o laudo`() {
        val r = laudo(entrada(protectionEnabled = false))
        assertFalse(r.isProtecting)
        assertEquals(
            DiagnosticFix.ENABLE_PROTECTION,
            r.checks.first { it.title == "Proteção" }.fix,
        )
    }

    @Test
    fun `modo 2 sem permissao de agenda e bloqueio, nao aviso`() {
        // O usuario pediu para aplicar a contatos e isso simplesmente nao acontece sem
        // a permissao -- as ligacoes nem chegam ao app. Chamar de "aviso" seria mentira.
        val check = DiagnosticsAssembler.build(
            entrada(applyToContacts = true, hasContactsPermission = false),
        ).first { it.title == "Contatos salvos" }
        assertEquals(CheckLevel.BLOCKING, check.level)
        assertEquals(DiagnosticFix.GRANT_CONTACTS, check.fix)
    }

    @Test
    fun `modo 1 sem permissao de agenda esta correto`() {
        val check = DiagnosticsAssembler.build(
            entrada(applyToContacts = false, hasContactsPermission = false),
        ).first { it.title == "Contatos salvos" }
        assertEquals(CheckLevel.OK, check.level)
    }

    @Test
    fun `aviso ligado sem permissao de notificar degrada mas nao bloqueia`() {
        val r = laudo(entrada(notifyOnBlock = true, canPostNotifications = false))
        // Os bloqueios continuam acontecendo; so o aviso se perde.
        assertTrue(r.isProtecting)
        assertEquals(CheckLevel.ATTENTION, r.worstLevel)
    }

    @Test
    fun `bateria restrita e aviso, nunca bloqueio`() {
        val check = DiagnosticsAssembler.build(entrada(ignoringBatteryOptimizations = false))
            .first { it.title == "Economia de bateria" }
        assertEquals(CheckLevel.ATTENTION, check.level)
        assertEquals(DiagnosticFix.OPEN_BATTERY_SETTINGS, check.fix)
    }

    @Test
    fun `aparelho que nao responde sobre bateria nao gera item`() {
        val itens = DiagnosticsAssembler.build(entrada(ignoringBatteryOptimizations = null))
        assertTrue(itens.none { it.title == "Economia de bateria" })
    }

    @Test
    fun `ausencia da permissao de internet e reportada como correta`() {
        val check = DiagnosticsAssembler.build(entrada(hasInternetPermission = false))
            .first { it.title == "Acesso à rede" }
        assertEquals(CheckLevel.OK, check.level)
    }

    @Test
    fun `presenca da permissao de internet e tratada como falha grave`() {
        // Se isto aparecer, o pacote instalado nao e o que este repositorio produz.
        val r = laudo(entrada(hasInternetPermission = true))
        assertFalse(r.isProtecting)
        assertEquals(
            CheckLevel.BLOCKING,
            r.checks.first { it.title == "Acesso à rede" }.level,
        )
    }

    @Test
    fun `regra em vigor menciona o modo noturno quando ele esta valendo`() {
        val noturna = CallPolicy(1, TimeUnit.MINUTES.toMillis(30), PolicySource.SCHEDULE)
        val check = DiagnosticsAssembler.build(
            entrada(activePolicy = noturna, scheduleActiveNow = true),
        ).first { it.title == "Regra em vigor agora" }
        assertTrue(check.detail.contains("Modo noturno"))
        assertTrue(check.detail.contains("1 chamada em 30 min"))
    }

    @Test
    fun `regra em vigor avisa que regras por numero passam na frente`() {
        val check = DiagnosticsAssembler.build(entrada(customRuleCount = 2))
            .first { it.title == "Regra em vigor agora" }
        assertTrue(check.detail.contains("2 número(s)"))
    }

    @Test
    fun `pior nivel escolhe bloqueio quando ha bloqueio e aviso juntos`() {
        val r = laudo(
            entrada(
                protectionEnabled = false,
                notifyOnBlock = true,
                canPostNotifications = false,
            ),
        )
        assertEquals(CheckLevel.BLOCKING, r.worstLevel)
    }
}
