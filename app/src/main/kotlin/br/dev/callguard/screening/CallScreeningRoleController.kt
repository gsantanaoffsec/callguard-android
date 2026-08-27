package br.dev.callguard.screening

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/**
 * Envolve o fluxo oficial de concessao do papel de filtragem de chamadas.
 *
 * Nao existe caminho programatico para se auto-conceder `ROLE_CALL_SCREENING`, e nem
 * deveria: quem escolhe o app de identificacao/filtragem e o usuario, na tela do
 * sistema. Tudo o que podemos fazer e perguntar o estado e abrir o dialogo oficial.
 *
 * `RoleManager` existe desde a API 29, que e o `minSdk` do projeto -- sem checagem de
 * versao aqui.
 */
class CallScreeningRoleController(context: Context) {

    private val roleManager: RoleManager? =
        context.applicationContext.getSystemService(RoleManager::class.java)

    /** Alguns aparelhos/perfis simplesmente nao oferecem o papel. */
    fun isRoleAvailable(): Boolean =
        roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true

    fun isRoleHeld(): Boolean =
        roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    /** Intent do dialogo do sistema. `null` quando o papel nao esta disponivel. */
    fun createRequestRoleIntent(): Intent? =
        roleManager
            ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) }
            ?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}
