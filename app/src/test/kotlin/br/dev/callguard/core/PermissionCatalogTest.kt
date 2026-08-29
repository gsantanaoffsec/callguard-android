package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O catálogo decide o que é dito ao usuário e o que é pedido ao sistema.
 *
 * Errar aqui tem duas consequências ruins e opostas: pedir uma permissão que o app não
 * usa, ou deixar de pedir uma que ele usa e depois falhar em silêncio. Ambas são
 * testáveis sem aparelho.
 */
class PermissionCatalogTest {

    private val tudoConcedido = AppPermission.entries.associateWith { PermissionStatus.GRANTED }
    private val nadaConcedido = AppPermission.entries.associateWith { PermissionStatus.MISSING }

    @Test
    fun `abaixo do Android 13 as notificacoes nem sao oferecidas`() {
        val itens = PermissionCatalog.disclosures(sdkInt = 32)
        assertTrue(itens.none { it.id == AppPermission.POST_NOTIFICATIONS })
    }

    @Test
    fun `a partir do Android 13 as notificacoes aparecem`() {
        val itens = PermissionCatalog.disclosures(sdkInt = 33)
        assertTrue(itens.any { it.id == AppPermission.POST_NOTIFICATIONS })
    }

    @Test
    fun `nada e pedido quando tudo ja esta concedido`() {
        val pedidos = PermissionCatalog.runtimePermissionsToRequest(tudoConcedido, sdkInt = 34)
        assertTrue(pedidos.isEmpty())
    }

    @Test
    fun `o lote nao inclui o papel de filtro`() {
        // O papel nao e permissao: tem Intent proprio e precisa ser pedido em seguida.
        // Coloca-lo no lote faria o pedido inteiro falhar.
        val pedidos = PermissionCatalog.runtimePermissionsToRequest(nadaConcedido, sdkInt = 34)
        assertFalse(pedidos.any { it.contains("ROLE") })
    }

    @Test
    fun `o lote nao inclui permissao concedida na instalacao`() {
        val pedidos = PermissionCatalog.runtimePermissionsToRequest(nadaConcedido, sdkInt = 34)
        // Biometria e permissao normal; nao ha dialogo para ela.
        assertFalse(pedidos.any { it.contains("BIOMETRIC") })
    }

    @Test
    fun `o lote pede exatamente as tres permissoes de runtime quando falta tudo`() {
        val pedidos = PermissionCatalog.runtimePermissionsToRequest(nadaConcedido, sdkInt = 34)
        assertEquals(
            listOf(
                PermissionCatalog.READ_CONTACTS,
                PermissionCatalog.POST_NOTIFICATIONS,
                PermissionCatalog.CALL_PHONE,
            ),
            pedidos,
        )
    }

    @Test
    fun `no Android 12 o lote nao pede notificacoes`() {
        val pedidos = PermissionCatalog.runtimePermissionsToRequest(nadaConcedido, sdkInt = 31)
        assertEquals(
            listOf(PermissionCatalog.READ_CONTACTS, PermissionCatalog.CALL_PHONE),
            pedidos,
        )
    }

    @Test
    fun `so o papel de filtro e essencial`() {
        val essenciais = PermissionCatalog.disclosures(34).filter { it.essential }
        assertEquals(listOf(AppPermission.CALL_SCREENING_ROLE), essenciais.map { it.id })
    }

    @Test
    fun `toda permissao pedida explica o que se perde ao recusar`() {
        // Uma tela que so lista beneficios e propaganda, nao divulgacao.
        PermissionCatalog.disclosures(34)
            .filterNot { it.installTime }
            .forEach { item ->
                assertTrue("${item.id} sem 'sem ela'", item.withoutIt.length > 20)
                assertTrue("${item.id} sem proposito", item.purpose.length > 20)
            }
    }

    @Test
    fun `a lista do que nunca e pedido menciona a internet`() {
        // E a garantia arquitetural do app; se sair daqui, a divulgacao fica incompleta.
        assertTrue(PermissionCatalog.neverRequested.any { it.contains("Internet") })
    }

    @Test
    fun `permissao ja concedida sai do lote`() {
        val misto = nadaConcedido.toMutableMap().apply {
            put(AppPermission.READ_CONTACTS, PermissionStatus.GRANTED)
        }
        val pedidos = PermissionCatalog.runtimePermissionsToRequest(misto, sdkInt = 34)
        assertFalse(pedidos.contains(PermissionCatalog.READ_CONTACTS))
        assertTrue(pedidos.contains(PermissionCatalog.CALL_PHONE))
    }
}
