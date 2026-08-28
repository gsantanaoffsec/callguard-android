package br.dev.callguard

import android.app.Application
import br.dev.callguard.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Aquece os caches usados pelo screening.
 *
 * O Android pode matar o processo entre uma chamada e outra; quando ele volta,
 * `onCreate` roda antes de qualquer servico. Aproveitamos essa janela para deixar as
 * configuracoes e a allowlist prontas em memoria, de modo que a decisao dentro do
 * `CallScreeningService` nao dependa da primeira leitura de disco.
 *
 * Nada aqui e obrigatorio para a correcao: os repositorios tem fallback para leitura
 * direta. Isto e otimizacao do orcamento de 5 s.
 */
class CallGuardApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val settings = ServiceLocator.settingsRepository(this)
        val allowlist = ServiceLocator.allowlistRepository(this)
        val blocklist = ServiceLocator.blocklistRepository(this)
        val customRules = ServiceLocator.customRuleRepository(this)

        applicationScope.launch { runCatching { settings.warmUp() } }
        applicationScope.launch { runCatching { allowlist.warmUp() } }
        applicationScope.launch { runCatching { blocklist.warmUp() } }
        applicationScope.launch { runCatching { customRules.warmUp() } }
        applicationScope.launch {
            runCatching {
                allowlist.observeEntries().collectLatest { allowlist.onEntriesChanged(it) }
            }
        }
        applicationScope.launch {
            runCatching {
                blocklist.observeEntries().collectLatest { blocklist.onEntriesChanged(it) }
            }
        }
        applicationScope.launch {
            runCatching {
                customRules.observeRules().collectLatest { customRules.onRulesChanged(it) }
            }
        }
    }
}
