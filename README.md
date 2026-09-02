# CallGuard — proteção contra chamadas insistentes

Aplicativo Android que decide localmente quais chamadas podem tocar, usando
exclusivamente a arquitetura oficial `android.telecom.CallScreeningService`.

O app não tem uma regra: tem uma **hierarquia** de controle, toda decidida no aparelho.

| O que você quer dizer | Onde se configura |
|---|---|
| "Sempre confio neste número" | Lista de permitidos |
| "Nunca quero receber deste número" | Bloqueio permanente |
| "Pode ligar, mas não pode insistir" | Regra por número |
| "De madrugada quero regra mais rígida" | Modo noturno |
| "Todo o resto segue o padrão" | Regra geral |

Regra geral padrão: **as 3 primeiras chamadas do mesmo número em 15 minutos passam; a 4ª
é rejeitada.** Janela deslizante — quem para de ligar volta a passar sozinho.

Além das regras, o app traz três ferramentas para quem depende delas: uma **central de
diagnóstico** que responde se a proteção está mesmo funcionando (e deixa testar um
número contra as regras reais, sem gravar nada), **bloqueio opcional por biometria** e
**exportar/importar** as regras num JSON legível.

Sem servidor, sem conta, sem telemetria, sem banco comunitário de spam. O app **não tem
permissão de internet** — essa ausência é a própria garantia arquitetural de privacidade,
e a central de diagnóstico confirma isso lendo as permissões do pacote instalado no
próprio aparelho.

---

## 1. Validação técnica

### O que é possível com as APIs públicas atuais

| Requisito | Situação | Como |
|---|---|---|
| Detectar chamada recebida antes de tocar | ✅ | `CallScreeningService.onScreenCall()` |
| Rejeitar a chamada automaticamente | ✅ | `CallResponse.setDisallowCall(true).setRejectCall(true)` |
| Obter o número | ✅ (com ressalvas) | `Call.Details.getHandle()` |
| Janela deslizante por número | ✅ | Lógica própria + Room |
| Sobreviver à morte do processo/reboot | ✅ | Timestamps persistidos em SQLite |
| Nunca bloquear contatos | ✅ | Basta **não** ter `READ_CONTACTS` |
| Aplicar a regra a contatos | ✅ | Exige `READ_CONTACTS` concedida |
| Nunca bloquear emergência | ✅ | Garantido pelo sistema + checagem própria |
| Allowlist local | ✅ | Room |
| Sem servidor / root / Accessibility | ✅ | — |

### Limitações reais do Android (não contornáveis, e não contornadas)

**1. Chamadas com número não apresentado não são interceptáveis.**

A documentação de `onScreenCall` é explícita:

> Calls with a `Call.Details.getHandlePresentation()` of `PRESENTATION_RESTRICTED`,
> `PRESENTATION_UNKNOWN`, `PRESENTATION_UNAVAILABLE` or `PRESENTATION_PAYPHONE`
> presentation are not provided to the `CallScreeningService`.

E no código do Telecom (`ParcelableCallUtils.toParcelableCallForScreening`):

```java
Uri handle = call.getHandlePresentation() == TelecomManager.PRESENTATION_ALLOWED ?
        call.getHandle() : null;
```

Ou seja: mesmo nas versões em que o serviço chega a ser chamado, o handle vem `null`.
**Não existe chave de agrupamento para chamadas privadas.** O app trata `handle == null`
como `ALLOW` e documenta isso — inventar um número sintético ("privado") juntaria
chamadores diferentes no mesmo contador, o que seria pior do que não fazer nada.

**2. Não é possível saber por qual SIM a chamada entrou.**

O mesmo trecho do Telecom monta o objeto entregue ao screening com:

```java
.setAccountHandle(null)
```

O `PhoneAccountHandle` — a única forma de identificar a conta/SIM — é deliberadamente
apagado. A documentação confirma listando as **únicas** propriedades preenchidas:
`getCallDirection()`, `getCallerNumberVerificationStatus()`, `getConnectTimeMillis()`,
`getCreationTimeMillis()` e `getHandle()`.

Consequência: em aparelhos dual SIM a regra vale **por telefone, independentemente do
SIM**. Isso está alinhado com o objetivo (é a mesma pessoa ligando) e é o que a API
permite. Nada de dual SIM foi inventado.

**3. `setSkipCallLog` não funciona para apps de terceiros.**

Documentado no próprio builder:

> Note: Only the carrier and system call screening apps can use this parameter; this
> parameter is ignored otherwise.
> Note: Calls will still be logged with type `CallLog.Calls.BLOCKED_TYPE`, regardless of
> how this property is set.

A chamada bloqueada **vai aparecer** no histórico do telefone da Samsung marcada como
bloqueada. Isso é bom: é a transparência que o sistema garante ao usuário. O app não
tenta escondê-la.

**4. Só um app por vez detém o papel.**

`ROLE_CALL_SCREENING` é exclusivo. Se o usuário já usa outro app de identificação de
chamadas, ele terá que escolher. Não há como coexistir.

**5. Apenas handles `tel:` chegam ao screening.** Chamadas VoIP de apps
(WhatsApp, Telegram) usam outro caminho e **não passam** por `CallScreeningService`.

---

## 2. Ordem das regras

Esta é a parte mais importante do sistema. Sem uma ordem explícita, cada exceção nova
viraria mais um `if` disputando espaço com as outras, e a precedência acabaria dependendo
de onde alguém escreveu a condição.

A ordem vive num único lugar — `CallScreeningPolicy.resolve()` — e é lida de cima para
baixo. A primeira que se aplicar decide.

| # | Regra | Resultado |
|---|---|---|
| 1 | **Emergência** | `ALLOW` — absoluto, nenhuma configuração sobrescreve |
| 2 | Proteção desligada | `ALLOW` |
| 3 | Número indisponível | `ALLOW` |
| 4 | Lista de permitidos | `ALLOW` |
| 5 | **Bloqueio permanente** | `BLOCK`, sem consultar histórico |
| 6 | Contato salvo (modo 1) | `ALLOW` |
| 7 | Regra do número | janela própria |
| 8 | Modo noturno ativo | janela do período |
| 9 | Regra geral | janela padrão |

**Duas decisões de ordem que merecem explicação:**

A **blocklist vem depois da allowlist** porque as duas são mutuamente exclusivas por
construção — a interface não deixa um número entrar nas duas sem confirmação explícita.

A **blocklist vem antes da proteção de contatos** porque uma ação manual sobre um número
específico é mais específica que a proteção genérica da agenda. Se você tem o João salvo
e mesmo assim o coloca no bloqueio permanente, foi uma decisão consciente sua sobre
aquele número — ela prevalece.

### Por que resolver antes de consultar

`CallScreeningPolicy` separa duas perguntas:

```kotlin
fun resolve(call: IncomingCall): PolicyResolution   // qual regra vale agora?
fun evaluate(call: IncomingCall, previousAttempts: List<Long>): ScreeningDecision
```

`resolve` devolve `Immediate` quando a decisão já sai sem histórico (emergência,
allowlist, blocklist, contato protegido) — e nesse caso **o banco nem é aberto**. Ou
devolve `UseWindow` com a regra a aplicar. Isso mantém o caminho crítico curto e torna a
precedência testável isoladamente.

---

## 3. Arquitetura

Cinco camadas, sem cerimônia inútil:

```
┌───────────────────────────────────────────────────────────────┐
│  ui/          Compose + ViewModel. Não conhece Room nem Telecom │
├───────────────────────────────────────────────────────────────┤
│  screening/   Integração com o Telecom. Traduz Call.Details      │
│               em IncomingCall e ScreeningDecision em CallResponse│
├───────────────────────────────────────────────────────────────┤
│  core/        REGRA DE DECISÃO. Kotlin puro, zero Android.       │
│               CallScreeningPolicy, CallPolicy, SchedulePolicy,   │
│               CustomRule, IncomingCall, ScreeningDecision,       │
│               DiagnosticsAssembler, BackupPayload, PhoneOrigin   │
├───────────────────────────────────────────────────────────────┤
│  data/        SettingsRepository (DataStore) + Room repositories │
├───────────────────────────────────────────────────────────────┤
│  phone/       PhoneNumberUtils e ContactsContract                │
└───────────────────────────────────────────────────────────────┘
```

| Componente | Responsabilidade |
|---|---|
| `CallScreeningPolicy` | **O motor.** Duas etapas: `resolve()` diz qual regra vale agora (sem tocar no banco) e `evaluate()` aplica a janela deslizante dessa regra. Nenhum import de Android — é o que o torna testável. |
| `PolicyResolution` | O que `resolve()` devolve: `Immediate` (decisão pronta, banco nem abre) ou `UseWindow` (regra a aplicar sobre o histórico). |
| `CallPolicy` / `PolicySource` | A janela já resolvida, carregando a própria origem (`GLOBAL`, `SCHEDULE`, `CUSTOM`) — assim o motor nunca precisa perguntar de onde veio o limite. |
| `SchedulePolicy` | Modo noturno. Sem serviço, alarme nem timer: quando a chamada chega, pergunta-se que horas são. |
| `CustomRule` | Limite próprio de um número, mais específico que horário e geral. |
| `BlocklistRepository` | Números que nunca devem passar, com cache em memória. |
| `CustomRuleRepository` | Regras por número, com cache do mapa inteiro. |
| `DiagnosticsAssembler` | Monta o laudo da central de diagnóstico. Kotlin puro: julgar "isto é um problema?" é regra de produto e tem teste. |
| `DiagnosticsRepository` | Coleta os fatos do Android e roda a simulação de um número **sem gravar nada**. |
| `BackupCodec` / `BackupRepository` | Exportação e importação das regras em JSON legível, com validação estrita da entrada. |
| `BiometricSupport` | Bloqueio do app por biometria ou senha do aparelho. Falha de disponibilidade libera, nunca tranca. |
| `InsistentCallScreeningService` | Único ponto de contato com `android.telecom`. Orquestra os 12 passos e responde dentro do orçamento. |
| `CallScreeningRoleController` | Estado e solicitação de `ROLE_CALL_SCREENING`. |
| `SettingsRepository` | Preferências em DataStore + cache quente para o screening. |
| `CallHistoryRepository` | Tentativas e bloqueios em Room, com atomicidade nos DAOs. |
| `AllowlistRepository` | Lista de exceções + cache em memória. |
| `TelephonyPhoneNumberNormalizer` | E.164 via `PhoneNumberUtils` + regra do 9º dígito. |
| `ContactLookup` | `PhoneLookup`, só quando muda a decisão. |
| `BlockedCallNotifier` | Aviso silencioso de bloqueio (canal `IMPORTANCE_LOW`, `setSilent`). |
| `CallerIdCodes` | Códigos CLIR (`#31#` / `*31#`) em Kotlin puro, testável. |
| `AnonymousCallScreen` | Aba para ligar com o próprio número oculto, via `ACTION_DIAL`. |
| `PhoneOrigin` | Procedência do número (DDD, região, tipo de linha) em Kotlin puro. |
| `ScreeningLogRepository` | Registro das decisões e geração do arquivo de log legível. |
| `RulesScreen` | Bloqueio permanente, regras por número e modo noturno, com confirmação em cada conflito. |
| `DiagnosticsScreen` | Laudo, teste de número, contagens da base e backup. |
| `SplashScreen` | Abertura desenhada em `Canvas`, sem imagem nem biblioteca de animação. |
| `ui/design/` | O design system: tokens, tipografia e componentes. Nenhuma tela define cor, espaçamento ou forma por conta própria. |
| `CallGuardViewModel` | Estado da UI; reconsulta papel/permissão a cada `ON_RESUME`. |

---

## 4. Fluxo de uma chamada

```
Chamada recebida
   │
   ▼
Telecom: IncomingCallFilterGraph
   │  • pula tudo se for emergência / callback de emergência
   │  • pula o bind se o número está na agenda E não temos READ_CONTACTS
   ▼
bind → InsistentCallScreeningService.onScreenCall(Call.Details)
   │
   ├─ 1. direção != INCOMING?  → return (framework responde por nós)
   ├─ 2. handle "tel:" → rawNumber   (null se apresentação restrita)
   ├─ 3. normalize()  → "+5511999998888"
   ├─ 4. settings.current() + schedule  (cache quente, sem I/O)
   ├─ 5. isEmergencyNumber(raw)
   ├─ 6. allowlist / blocklist .contains()   (caches em memória)
   ├─ 7. customRuleRepository.find()          (cache do mapa inteiro)
   ├─ 8. contactLookup                (só se protegido + modo 1 + tem permissão)
   │
   ▼
policy.resolve(call)  ──► Immediate ──► decisão pronta, banco nem abre
   │                                     (emergência, allowlist, blocklist,
   │                                      contato protegido, proteção off)
   │ UseWindow(policy)   ← qual das regras venceu a precedência da seção 2
   ▼
callAttemptDao.recordAttemptAndGetPrevious()   ◄── @Transaction:
   │   DELETE expirados; SELECT janela; INSERT atual — atômico
   ▼
policy.evaluate(call, previousAttempts)
   │   anteriores na janela < max  → Allow(UNDER_{GLOBAL|CUSTOM|SCHEDULE}_LIMIT)
   │   anteriores na janela >= max → Block({GLOBAL|CUSTOM|SCHEDULE}_LIMIT_EXCEEDED)
   ▼
respondToCall(details, CallResponse)
   │   ALLOW: Builder().build()
   │   BLOCK: setDisallowCall(true).setRejectCall(true).setSkipNotification(true)
   ▼
Telecom desliga a chamada. O telefone nunca toca.
   │
   ▼
efeitos colaterais, JÁ FORA do orçamento de 5 s:
   grava blocked_calls · incrementa contador · notifica em silêncio
```

**Orçamento de tempo:** o framework dá 5 s. O app usa `withTimeoutOrNull(3000)` e,
se estourar, responde `ALLOW`. **A falha é sempre para o lado de permitir** — jamais
derrubar uma ligação legítima por lentidão nossa.

A resposta ao Telecom sai **antes** de gravar o bloqueio e notificar. O relógio de 5 s
para no `respondToCall`; efeitos colaterais não devem disputar esse orçamento nem
atrasar a decisão que o sistema está esperando. Se um deles falhar, a chamada já foi
recusada do mesmo jeito.

---

## 5. Estrutura do projeto

```
callguard-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/
├── gradlew / gradlew.bat
├── local.properties.example
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── schemas/                      (schema do Room, gerado pelo KSP)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── kotlin/br/dev/callguard/
        │   │   ├── CallGuardApplication.kt
        │   │   ├── core/
        │   │   │   ├── CallScreeningPolicy.kt      ← O MOTOR
        │   │   │   ├── CallPolicy.kt               (janela + origem + modo noturno)
        │   │   │   ├── ProtectionSettings.kt
        │   │   │   ├── IncomingCall.kt
        │   │   │   ├── ScreeningDecision.kt
        │   │   │   ├── DiagnosticsReport.kt
        │   │   │   ├── BackupPayload.kt
        │   │   │   ├── PhoneOrigin.kt
        │   │   │   ├── CallerIdCodes.kt
        │   │   │   ├── BrazilPhoneRules.kt
        │   │   │   ├── PhoneNumberMasker.kt
        │   │   │   └── PhoneNumberNormalizer.kt
        │   │   ├── data/
        │   │   │   ├── ServiceLocator.kt
        │   │   │   ├── SettingsRepository.kt
        │   │   │   ├── CallHistoryRepository.kt
        │   │   │   ├── AllowlistRepository.kt
        │   │   │   ├── BlocklistRepository.kt
        │   │   │   ├── CustomRuleRepository.kt
        │   │   │   ├── ScreeningLogRepository.kt
        │   │   │   ├── DiagnosticsRepository.kt
        │   │   │   ├── BackupCodec.kt
        │   │   │   ├── BackupRepository.kt
        │   │   │   └── db/
        │   │   │       ├── CallGuardDatabase.kt    (v3, migrações escritas à mão)
        │   │   │       ├── Entities.kt
        │   │   │       ├── CallAttemptDao.kt
        │   │   │       ├── AllowlistDao.kt
        │   │   │       ├── BlocklistDao.kt
        │   │   │       ├── CustomRuleDao.kt
        │   │   │       ├── ScreeningEventDao.kt
        │   │   │       └── BlockedCallDao.kt
        │   │   ├── phone/
        │   │   │   ├── TelephonyPhoneNumberNormalizer.kt
        │   │   │   └── ContactLookup.kt
        │   │   ├── screening/
        │   │   │   ├── InsistentCallScreeningService.kt
        │   │   │   ├── BlockedCallNotifier.kt
        │   │   │   └── CallScreeningRoleController.kt
        │   │   └── ui/
        │   │       ├── design/                 ← DESIGN SYSTEM
        │   │       │   ├── Tokens.kt            (cor, espaço, forma, tempo)
        │   │       │   ├── Type.kt
        │   │       │   ├── Foundation.kt
        │   │       │   ├── Controls.kt
        │   │       │   ├── ListItems.kt
        │   │       │   └── Dialog.kt
        │   │       ├── MainActivity.kt
        │   │       ├── CallGuardViewModel.kt
        │   │       ├── UiState.kt
        │   │       ├── SplashScreen.kt
        │   │       ├── HomeScreen.kt
        │   │       ├── RulesScreen.kt
        │   │       ├── DiagnosticsScreen.kt
        │   │       ├── BiometricGate.kt
        │   │       ├── AnonymousCallScreen.kt
        │   │       ├── LogsScreen.kt
        │   │       ├── BlockedCallsScreen.kt
        │   │       ├── CallGuardNavigationBar.kt
        │   │       └── theme/Theme.kt
        │   └── res/
        └── test/kotlin/br/dev/callguard/
            ├── core/
            │   ├── CallScreeningPolicyTest.kt      ← precedência e janela
            │   ├── SchedulePolicyTest.kt           ← inclusive a virada da meia-noite
            │   ├── DiagnosticsAssemblerTest.kt
            │   ├── PhoneOriginTest.kt
            │   ├── CallerIdCodesTest.kt
            │   ├── BrazilPhoneRulesTest.kt
            │   ├── PhoneNumberMaskerTest.kt
            │   └── ProtectionSettingsTest.kt
            └── data/
                ├── BackupCodecTest.kt
                └── db/CallAttemptDaoTest.kt        ← prova da atomicidade
```

---

## 6. Dependências

Cada uma está no projeto porque é usada. Não há nada "por via das dúvidas".

| Dependência | Por quê |
|---|---|
| `androidx.core:core-ktx` | `ContextCompat.checkSelfPermission` na checagem de READ_CONTACTS. |
| `androidx.activity:activity-compose` | `setContent`, `enableEdgeToEdge`, `rememberLauncherForActivityResult` (fluxo do RoleManager), `BackHandler`. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `viewModel()` na Activity. |
| `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle`, `LifecycleResumeEffect` — reconsultar o papel ao voltar da tela do sistema. |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `viewModelScope`. |
| `androidx.compose:compose-bom` | Alinha as versões do Compose. |
| `androidx.biometric:biometric` | `BiometricPrompt` do bloqueio opcional do app. Traz `FragmentActivity` junto, que é o host que ele exige. |
| `compose.ui` / `ui-graphics` / `material3` | A interface. |
| `compose.material:material-icons-core` | Os 6 ícones usados. Deliberadamente **não** usamos `material-icons-extended`: são milhares de vetores que inflam o APK e o tempo de build para nada. |
| `compose.ui-tooling` (debug) | Preview no Android Studio. |
| `androidx.room:room-runtime` + `room-ktx` | Histórico de tentativas, allowlist e bloqueios. `room-ktx` traz o suporte a `suspend` e `Flow`. |
| `androidx.room:room-compiler` (KSP) | Geração de código do Room. |
| `androidx.datastore:datastore-preferences` | Preferências do usuário. |
| `junit` (test) | Testes da regra. |
| `robolectric` (test) | Roda Room sobre SQLite real na JVM, o que permite testar a transação do DAO sem emulador. |
| `androidx.test:core` (test) | Fornece o `Context` que o Room exige nesses testes. |
| `kotlinx-coroutines-test` (test) | Dispara as corrotinas concorrentes do teste de race condition. |

**Não há**: Hilt/Dagger (3 repositórios não justificam), Retrofit/OkHttp (não há rede),
navigation-compose (duas telas), WorkManager (nada agendado), Firebase, analytics.

---

## 7. Decisões de armazenamento

Duas tecnologias, cada uma onde faz sentido:

**DataStore Preferences → configurações do usuário**
Meia dúzia de escalares sem relacionamento e sem consulta. Room aqui seria peso morto.
DataStore ainda entrega um `Flow` que a UI observa de graça. Um cache `@Volatile`
alimentado no `Application.onCreate` evita que a primeira leitura de disco caia dentro
do orçamento do screening.

**Room → tentativas, allowlist e bloqueios**
Estes três precisam de consulta indexada por número, remoção por faixa de tempo e
**transação** — exatamente o que o SQLite faz bem e um arquivo de preferências faz mal.

**Por que timestamps e não contador:** um contador não sabe envelhecer. Guardando o
instante de cada tentativa, a janela deslizante se resolve com um `WHERE
timestamp_millis > :windowStart`, e o "reset automático" acontece sozinho, sem
temporizador, sem serviço em background e sem `AlarmManager`.

**Relatório de falha:** o app instala um `Thread.setDefaultUncaughtExceptionHandler` que
grava o rastro de uma queda em `Android/data/br.dev.callguard/files/logs/callguard-falhas.txt`,
ao lado do registro de decisões. Existe porque o app não tem rede: sem Crashlytics e sem
telemetria, uma falha que só acontece no aparelho de quem usa seria invisível — restaria
o diálogo genérico do sistema, que diz que há um bug mas não diz qual. O arquivo não
contém número de telefone (só nomes de classe e linha) e só sai do aparelho se a pessoa
mandar. A aba Registro mostra um aviso no topo quando existe um.

**Retenção:** tentativas são apagadas depois de 6 h ou **2× a maior janela existente no
sistema**, o que for maior, em cada screening. A palavra "maior" faz trabalho aqui: com
uma regra por número de 6 h convivendo com a regra geral de 15 min, podar pela geral
apagaria tentativas que a outra ainda precisa contar. Bloqueios ficam limitados aos 100
mais recentes; o registro de decisões, aos 500. Um app que lida com telefones não deve
acumular telefones indefinidamente.

**Migrações:** o banco está na **v4** e as duas migrações são objetos `Migration`
escritos à mão e conferidos contra o schema JSON que o KSP exporta — `v1→v2` cria
`screening_events`, `v2→v3` cria `blocklist_entries` e `custom_rules`, `v3→v4` cria `pattern_rules`.
`fallbackToDestructiveMigration` não é usado em lugar nenhum: quem já tem o app
instalado perderia listas e configurações.

---

## 8. Concorrência

O ponto onde uma race condition apareceria é a sequência *ler janela → decidir →
gravar*. Duas chamadas quase simultâneas poderiam ler "2 anteriores" cada uma e ambas
serem liberadas.

A solução está em `CallAttemptDao.recordAttemptAndGetPrevious`, um único
`@Transaction`:

```kotlin
@Transaction
open suspend fun recordAttemptAndGetPrevious(...): List<Long> {
    deleteOlderThan(now - retentionMillis)
    val previous = attemptsInWindow(number, now - windowMillis, now)
    insert(CallAttemptEntity(number, now))
    return previous
}
```

Leitura e escrita no mesmo escopo atômico. O SQLite serializa transações de escrita,
então a segunda chamada obrigatoriamente enxerga a tentativa registrada pela primeira.
Não há necessidade de mutex adicional em nível de aplicação — e um mutex sozinho não
resolveria, já que não protegeria contra escritas de outro processo.

**Isto é verificado, não apenas afirmado.** `CallAttemptDaoTest` dispara 20 chamadas
simultâneas do mesmo número contra um Room em memória (SQLite de verdade, via
Robolectric) e exige que as contagens anteriores formem exatamente `0, 1, 2, … 19` sem
repetição. Qualquer valor repetido seria a race condition acontecendo — duas chamadas
lendo o mesmo contador e ambas passando. Um segundo teste faz o mesmo intercalando dois
números diferentes e confirma que os históricos não se contaminam.

**Caches em memória** (`SettingsRepository`, `AllowlistRepository`): são `@Volatile` e
existem apenas para velocidade. Toda leitura tem fallback para a fonte real quando o
cache está frio, e toda escrita atualiza o cache. Nenhuma decisão depende do cache estar
quente — só a latência depende.

**Morte do processo:** as tentativas já estão no SQLite quando a decisão é tomada. Se o
processo morrer entre uma chamada e outra, ou se o aparelho reiniciar, a janela é
reconstruída da tabela na próxima chamada. Nada é mantido apenas em memória.

---

## 9. Privacidade e permissões

**Quatro permissões declaradas. Nenhuma é pedida na abertura do app; cada uma aparece no
primeiro uso real do recurso que depende dela:**

| Permissão | Quando é pedida | O que acontece sem ela |
|---|---|---|
| `READ_CONTACTS` | ao ligar "aplicar a regra também aos contatos salvos" | o Modo 2 não funciona (o próprio Telecom não entrega essas chamadas) |
| `POST_NOTIFICATIONS` | ao ligar "avisar quando bloquear" | os bloqueios acontecem em silêncio |
| `CALL_PHONE` | ao fazer a primeira ligação oculta | a aba cai para o discador do sistema, que não exige permissão |
| `USE_BIOMETRIC` | nunca (é permissão *normal*, concedida na instalação) | — |

`USE_BIOMETRIC` é de nível *normal*: não há diálogo de tempo de execução e ela só é
exercida quando o próprio usuário liga o bloqueio do app. A biblioteca
`androidx.biometric` ainda declara `USE_FINGERPRINT` para atender ao Android 8.1; como o
`minSdk` daqui é 29, ela é removida do manifesto mergeado com `tools:node="remove"` —
a lista que o usuário vê deve descrever o que o app de fato usa.

Isto é possível por causa de um detalhe do Telecom
(`CallScreeningServiceFilter.startFilterLookup`):

```java
if (priorStageResult.contactExists && (!hasReadContactsPermission())) {
    // Binding to the call screening service will be skipped if it does NOT hold
    // READ_CONTACTS permission and the number is in the user's contacts
    return CompletableFuture.completedFuture(priorStageResult);
}
```

Ou seja:

- **Modo 1 (padrão, nunca bloquear contatos):** basta **não ter** a permissão. Chamadas
  de contatos nem chegam ao app. É o sistema garantindo o comportamento — melhor do que
  qualquer código que pudéssemos escrever.
- **Modo 2 (aplicar a contatos):** só existe com `READ_CONTACTS` concedida.

Se o usuário conceder a permissão para o Modo 2 e depois voltar ao Modo 1 sem revogá-la,
as chamadas de contatos continuam chegando — e aí `ContactLookup` as deixa passar
explicitamente. É o único caso em que consultamos a agenda.

### A tela de Permissões

Cada permissão continua sendo pedida no primeiro uso real do recurso — esse é o padrão do
app e é o comportamento correto. Mas quem quer configurar tudo de uma vez tinha que
descobrir onde cada uma morava. A tela **Permissões** (tela inicial → Mais → Permissões,
e o botão do bloco de estado quando falta o papel) resolve isso.

Ela é, antes de tudo, a **divulgação prévia**: cada item diz para que serve e **o que se
perde ao recusar**, antes de qualquer diálogo aparecer. Uma tela de permissões que só
lista benefícios é propaganda; saber o que se perde é o que torna a escolha informada — e
aqui só o papel de filtro é indispensável.

Depois da explicação, um botão pede tudo o que falta. Duas coisas que ele **não** faz, e
que a tela não esconde:

- **Não concede nada.** Nenhum aplicativo Android concede permissões a si mesmo. O botão
  dispara os diálogos oficiais do sistema em sequência (`RequestMultiplePermissions`), e
  quem concede continua sendo o dono do aparelho, item por item.
- **Não liga a regra para contatos.** Conceder o acesso à agenda apenas torna a opção
  possível; ligá-la continua sendo o interruptor da tela inicial. Permissão concedida não
  é comportamento mudado.

O papel de filtro fica fora do lote de propósito: ele não é uma permissão, tem um `Intent`
próprio (`RoleManager.createRequestRoleIntent`) e é pedido logo em seguida, quando a
sequência de diálogos termina. Colocá-lo no lote faria o pedido inteiro falhar.

A tela também lista **o que o app nunca pede** — internet, registro de chamadas,
microfone, câmera, localização, armazenamento, ser discador padrão. Uma lista do que se
quer sem a lista do que não se quer não deixa ninguém julgar se o pedido é proporcional.

**Permissões deliberadamente NÃO pedidas:** `READ_CALL_LOG` (a API de screening já
entrega o necessário), `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `INTERNET`,
`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (o diagnóstico abre a *lista* de otimização, que
não exige permissão, em vez de pedir isenção), e qualquer permissão de armazenamento —
o backup usa o seletor do sistema (SAF), então o arquivo nasce onde o dono mandou sem
que o app tenha acesso a mais nada. O app não é discador padrão e não precisa ser.

**Sobre a segunda permissão no APK:** ao inspecionar o APK aparece também
`br.dev.callguard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Ela é adicionada
automaticamente pelo `androidx.core` para proteger os `BroadcastReceiver` que as próprias
bibliotecas registram em runtime. É de nível *signature*, definida por este app e válida
só dentro dele — não dá acesso a nada e não aparece para o usuário.

**Outras medidas:**
- `android:allowBackup="false"` + regras de extração que excluem tudo: telefones não vão
  para a nuvem nem em transferência entre aparelhos.
- Nenhum log de sistema contém número de telefone — só a decisão (`ALLOW(UNDER_GLOBAL_LIMIT)`).
- Números aparecem mascarados na tela de bloqueios; revelar é escolha explícita.
- Sem rede: não há `INTERNET` no manifesto, então nem por engano. **E isso é verificável
  dentro do app:** a central de diagnóstico lê a lista real de permissões do pacote
  instalado (`PackageManager.GET_PERMISSIONS`) e reporta a ausência. Uma promessa que o
  usuário pode conferir no próprio aparelho vale mais do que uma linha em README.
- O backup exporta **apenas configuração** — ajustes, listas e regras. Histórico de
  chamadas não vai junto: backup existe para recriar as regras em outro aparelho, não
  para levar embora quem ligou para você.

---

## 10. `BIND_SCREENING_SERVICE`: a distinção que importa

```xml
<service
    android:name=".screening.InsistentCallScreeningService"
    android:exported="true"
    android:permission="android.permission.BIND_SCREENING_SERVICE">
```

`android:permission` no `<service>` **não é uma permissão que pedimos.** É uma trava:
ela exige que *quem fizer bind* neste serviço possua essa permissão de assinatura — e
só o processo do sistema (Telecom) a possui.

Se em vez disso escrevêssemos `<uses-permission android:name="android.permission.BIND_SCREENING_SERVICE"/>`,
seria inútil: é uma permissão `signature`, nunca concedida a apps comuns, e o sistema a
ignoraria silenciosamente.

`android:exported="true"` é necessário porque quem faz o bind está em outro processo
(`system_server`). É a permissão acima que mantém isso seguro — sem ela, qualquer app
poderia se conectar ao nosso serviço.

---

## 11. `CallResponse`: por que esta combinação

| Método | O que faz | Usamos? |
|---|---|---|
| `setSilenceCall(true)` | Só não toca o ringtone. **A chamada continua**, vai para o discador e é apresentada. | ❌ Não atende ao objetivo — o pedido é rejeitar, não silenciar. |
| `setDisallowCall(true)` | Bloqueia: a chamada não é apresentada ao usuário. | ✅ Pré-requisito de todo o resto. |
| `setRejectCall(true)` | Desliga como se o usuário tivesse tocado em "Recusar". | ✅ Sem ele a chamada só não apareceria, seguiria ocupando a linha até cair na caixa postal. |
| `setSkipNotification(true)` | Sem notificação de chamada perdida. | ✅ O objetivo do app é não incomodar. |
| `setSkipCallLog(true)` | Ignorado para apps de terceiros; a chamada é registrada como `BLOCKED_TYPE` de qualquer forma. | ❌ Deixamos `false` — o registro visível é transparência. |

Restrição verificada no código do framework (`CallResponse`):

```java
if (!shouldDisallowCall
        && (shouldRejectCall || shouldSkipCallLog || shouldSkipNotification)) {
    throw new IllegalStateException("Invalid response state for allowed call.");
}
```

`setRejectCall`, `setSkipCallLog` e `setSkipNotification` **exigem** `disallowCall = true`.

O app **não** usa `BlockedNumberContract` e **não** adiciona o número à lista permanente
de bloqueados da Samsung/Android. O bloqueio é uma decisão nossa, tomada naquele momento,
que se desfaz sozinha quando a janela esvazia.

---

## 12. Avisos e correção rápida

**Aviso silencioso de bloqueio.** O app usa um canal `IMPORTANCE_LOW` com
`setSilent(true)`: a notificação aparece na barra, sem som e sem vibração, com o número
mascarado e a quantidade de tentativas. Sempre no mesmo id — ela é substituída em vez de
empilhar uma por chamada bloqueada. Tocar nela abre direto a tela de bloqueios.

Sem isso o app agia em silêncio absoluto e o usuário só descobria um bloqueio abrindo a
tela de histórico — o que esconde dele uma informação que é sua, especialmente com a
regra valendo também para contatos salvos.

**"Nunca bloquear este número".** Cada item da tela de bloqueios tem esse botão. O número
ali já está normalizado — foi essa a chave usada para bloquear —, então ele entra na
allowlist como está, sem passar pelo normalizador de novo: reprocessar poderia gerar uma
chave diferente e a exceção não pegaria. Itens já liberados aparecem marcados.

---

## 13. Aba "Ligar oculto" (CLIR)

Permite ligar com **o seu próprio número oculto** — a pessoa vê "Número privado".

Isto não tem nada a ver com falsificar o número de outra pessoa: isso não é possível por
API pública e não é o que este código faz. É a mesma função que a operadora oferece e que
já existe nas configurações do telefone.

### Como funciona

O prefixo `#31#` é um código de serviço suplementar padronizado em **3GPP TS 22.030**.
No AOSP (`GsmMmiCode`), a ação `#` sobre o código de serviço `31` vira:

```java
static final int CLIR_INVOCATION = 1;   // (restrict CLI presentation)
```

E em `GsmCdmaPhone.dialInternal` a chamada é discada assim:

```java
} else if (mmi.isTemporaryModeCLIR()) {
    return mCT.dialGsm(mmi.mDialingNumber, mmi.getCLIRMode(), ...);
}
```

Ou seja: o prefixo **não vai como dígitos** para a rede. A telefonia reconhece o código e
disca o número real pedindo que a identificação não seja apresentada. `*31#` faz o
inverso (força a apresentação), útil para quem deixou a ocultação permanente ligada.

### `ACTION_CALL`, com `ACTION_DIAL` como alternativa

A chamada é iniciada de dentro do app com `ACTION_CALL`, que exige `CALL_PHONE`. A versão
anterior usava `ACTION_DIAL` (sem permissão), mas ele joga o usuário no discador com o
código de serviço digitado na tela — o mecanismo ficava exposto a cada ligação.

A permissão é pedida **apenas no primeiro toque em "Ligar"**, nunca na abertura do app. Se
for negada, o recurso não morre: cai para `ACTION_DIAL`. Nenhuma chamada acontece sem o
usuário tocar no botão.

O prefixo não chega a aparecer: a telefonia reconhece o código, disca o número real, e o
handle da chamada é substituído pelo da conexão (`CallsManager`: `call.setHandle(
connection.getHandle(), …)`, sendo `GsmMmiCode.mDialingNumber` o número **sem** prefixo).
A tela de chamada do sistema mostra só o número limpo.

**Limite:** o app mostra uma tela própria de "Ligando…", mas quem desenha a interface da
chamada em curso (mudo, viva-voz, desligar) é o sistema. Substituí-la exigiria ser o
**discador padrão** do aparelho (`InCallService` + `ROLE_DIALER`), o que faria este app
assumir TODAS as chamadas — decisão bem maior do que a de ocultar o próprio número.

Verificado também que o Telecom não barra este caso: em
`NewOutgoingCallIntentBroadcaster`, apenas códigos MMI **perigosos** são restritos a apps
de discagem padrão, e `MmiUtils.isDangerousMmiOrVerticalCode` considera perigosos apenas
os de **desvio de chamada**. `#31#` não está nessa categoria.

### Limitações, ditas na própria tela

- **Depende da operadora.** Nem toda linha tem a ocultação por chamada habilitada.
- **Número privado é muito rejeitado.** Vários aparelhos têm "bloquear números
  desconhecidos" ligado; a ligação pode nem chegar.
- **Emergência sempre transmite a identidade.** A rede ignora o CLIR nesses casos. A tela
  detecta números de emergência com `TelephonyManager.isEmergencyNumber` (que não exige
  permissão) e recusa o caminho oculto, explicando o porquê, em vez de oferecer algo que
  não funcionaria.
- **A ironia útil:** o próprio CallGuard não consegue filtrar chamadas ocultas — sem
  número não há como contar tentativas. Quem liga oculto passa por qualquer app de
  filtragem, inclusive este.

### Ocultar sempre

Para todas as chamadas saírem ocultas não é preciso app nenhum:
**Telefone → ⋮ → Configurações → Serviços suplementares → Mostrar meu ID de chamada →
Ocultar número.**

---

## 14. Aba "Logs"

Registro legível do que o app decidiu — **toda** chamada analisada, permitida ou
bloqueada. Sem as permitidas não daria para responder "por que essa não foi bloqueada?".

### O arquivo

```
Android/data/br.dev.callguard/files/logs/callguard-registro.txt
```

`getExternalFilesDir()`: visível nos gerenciadores de arquivos (o "Meus Arquivos" da
Samsung), **sem exigir permissão de armazenamento**, e a partir do Android 11 não legível
por outros aplicativos. É texto puro, escrito para ser lido por gente:

```
27/08/2026 20:14:03
  Número....: +5511999998888
  Procedência: São Paulo · SP · celular
  DDD.......: 11
  Verificação da rede: aprovada — a rede confirma que o número é legítimo
  Resultado.: BLOQUEADA
  Motivo....: Limite de chamadas excedido
  Tentativas recentes: 4
```

Gerado sob demanda, não a cada chamada: escrever em disco dentro do fluxo de screening
seria trabalho desnecessário num caminho que tem orçamento de tempo. A aba também mostra
os registros direto na tela, para o caso comum de só querer conferir.

Abrir e compartilhar usam **`FileProvider`** — desde o Android 7 entregar um caminho
`file://` a outro app lança `FileUriExposedException`. O provider expõe somente a pasta de
logs, é `exported="false"` e funciona só com a permissão pontual concedida no Intent.

### Procedência do número

Tudo derivado offline, sem rede e sem permissão:

| Campo | Como |
|---|---|
| País | Código do país no E.164 |
| DDD e região | Tabela dos 67 códigos do Plano Geral de Códigos Nacionais da ANATEL |
| Tipo de linha | Estrutura do número: celular, fixo, 0800, 0300/0500, serviço, número curto |
| Verificação da rede | `Call.Details.getCallerNumberVerificationStatus()` (STIR/SHAKEN) |

A verificação da rede é especialmente útil aqui: é o operador dizendo se o número de
origem foi autenticado ou se pode estar **falsificado**. Existe a partir do Android 11 —
abaixo disso o campo fica nulo e o app diz "não disponível neste Android", em vez de
inventar um valor.

### Por que NÃO existe campo "operadora"

Foi pedido, e é o único item que não dá para entregar honestamente.

Com a **portabilidade numérica** (Brasil, 2008), o prefixo deixou de indicar a operadora:
um número originalmente Vivo pode estar na Claro hoje. Descobrir a operadora atual exige
consultar a base da ABR Telecom — internet e credencial, ambas fora do escopo de um app
que não acessa a rede.

Preencher esse campo pelo prefixo seria informação errada com cara de certa, e num app
sobre chamadas indesejadas isso levaria a conclusões erradas. O arquivo e a tela dizem
explicitamente por que a operadora não aparece.

---

## 14-A. Bloqueio por faixa de números

Existe por um limite do Android que **não dá para contornar**: em
`ParcelableCallUtils.toParcelableCallForScreening`, o sistema anula `callerDisplayName`,
`contactDisplayName` e `name` antes de entregar a chamada a um serviço de filtragem. Só o
número passa — e mesmo ele apenas quando a apresentação é permitida. Ou seja: **"bloquear
tudo que aparece como Claro" é impossível por construção.** O app nunca vê essa palavra.

O que ele vê é o número. E quem liga em volume não usa um número: usa uma faixa. Bloquear
o prefixo pega a faixa inteira, inclusive os números que ainda não ligaram.

| Campo | O que faz |
|---|---|
| Dígitos | 2 a 15 dígitos; qualquer formatação digitada é descartada |
| Começa com | o número inicia por esses dígitos — é como faixas de telefonia são organizadas |
| Contém | os dígitos aparecem em qualquer posição; rede de arrasto |

**A comparação acontece contra duas formas do mesmo número**, e isso não é preciosismo. O
normalizador produz `+5511999998888` para um celular comum, mas devolve só os dígitos
(`03031234567`) para códigos não geográficos, que não são E.164 válidos. Um padrão `0303`
jamais casaria com a primeira forma; um padrão `11` jamais casaria com a segunda. Testar as
duas é o que faz o recurso se comportar como a pessoa espera sem ela precisar saber o que
é E.164. E o `55` só é removido quando o número **declara** o Brasil com `+55` — tirá-lo
de qualquer sequência transformaria `5512345678` em `12345678` e criaria casamentos falsos.

**A prévia antes de salvar** mostra quantos dos registros recentes seriam recusados, e
quais (mascarados). Um prefixo de dois dígitos é um DDD inteiro; sem a prévia, o estrago só
apareceria depois, como ligações que deixaram de tocar sem explicação. A tela avisa
explicitamente nesse caso.

**Duas garantias que os testes fixam:** emergência vence a faixa (uma faixa larga poderia
pegar um número de emergência por acidente), e a lista de permitidos vence a faixa — é o
escape que permite bloquear um DDD inteiro e liberar um número específico dentro dele.

### Faixas prontas: sugestões e catálogo

Descobrir os dígitos na mão só funciona para quem já sabe o que procurar. A tela oferece
duas ajudas, e a **ordem entre elas não é arbitrária**.

**Sugestões do seu registro vêm primeiro**, porque são as que resolvem. Uma operadora
fazendo campanha não liga do número da central: liga de um DDD comum, e a faixa muda por
região e por campanha. Ninguém consegue enumerar isso no código — mas o aparelho já tem a
resposta, no registro de quem ligou. `PatternSuggester` agrupa os números recebidos por
prefixo de 4 a 7 dígitos e propõe os que aparecem em dois números distintos ou mais.

Quatro regras que o algoritmo respeita, todas com teste:

- **Nunca sugere um prefixo que pegaria um número da lista de permitidos.** Seria o app
  discordando de uma decisão explícita do usuário — e discordando com aparência de
  recomendação, que é pior.
- **Nunca sugere menos de 4 dígitos.** Um prefixo de 2 é um DDD inteiro e de 3 é meia
  região; oferecer isso é um tiro no pé com cara de conselho.
- **Entre prefixos que pegam o mesmo conjunto, escolhe o mais longo.** É igualmente eficaz
  e menos abrangente.
- **Usa a forma nacional, não a internacional.** O algoritmo original propunha `5511400`
  em vez de `114004`: equivalente para a máquina, ilegível para quem pensa em DDD — e uma
  sugestão que a pessoa não entende ela não confere, ela só aceita.

**O catálogo conhecido** (`KnownRanges`) cobre telemarketing (`0303`), faixas de serviço
(`0800`, `0300`, `0500`, `4004`, `4003`, `3003`) e as centrais das operadoras (Claro
`1052`, TIM `1056`, Vivo `1058` e `10315`). Cada uma diz o que é, e as que merecem
ressalva a exibem antes de bloquear:

- **Operadoras:** esse é o número que **você liga**, não necessariamente de onde **elas
  ligam**. Bloquear evita o retorno por esse canal, mas não barra a campanha de vendas.
- **0800:** bancos, planos de saúde e serviços legítimos também usam. Bloquear pega os dois.

Sobre o prefixo **0303**: era obrigatório para telemarketing desde 2022, mas a ANATEL
[revogou a obrigatoriedade em agosto de 2025](https://agenciabrasil.ebc.com.br/geral/noticia/2025-08/anatel-revoga-obrigatoriedade-do-uso-do-prefixo-0303-em-ligacoes).
Ainda é usado por parte do setor, mas não dá mais para tratá-lo como regra — motivo a mais
para o recurso ser uma faixa configurável em vez de uma lista fixa no código.

---

## 15. Central de diagnóstico

Existe por causa da pergunta mais cara de um app de filtragem: *"será que está mesmo
funcionando?"*. O usuário não tem como ver o que **não** aconteceu — uma ligação que não
tocou é indistinguível de um app quebrado.

A tela responde com fatos verificáveis no próprio aparelho, ordenados por gravidade:
primeiro o que impede a proteção de existir, depois o que a degrada, e só então o que é
informativo. Quem abre esta tela abriu porque algo parece errado; a resposta tem que
estar em cima.

| Item | Quando vira bloqueio | Quando vira aviso |
|---|---|---|
| Papel de filtro de chamadas | não concedido, ou indisponível no aparelho | — |
| Proteção | interruptor desligado | — |
| Contatos salvos | Modo 2 ligado sem `READ_CONTACTS` | — |
| Aviso de bloqueio | — | avisos ligados sem permissão de notificar |
| Economia de bateria | — | app sujeito à otimização |
| Acesso à rede | `INTERNET` presente no pacote instalado | — |

Duas escolhas que valem explicação:

**Economia de bateria é aviso, nunca bloqueio.** Quem faz o *bind* no serviço de
screening é o sistema, e o serviço vive por poucos segundos — a restrição de background
não o impede. Marcar como quebrado seria alarme falso; omitir seria esconder um fator
real em aparelhos Samsung, onde a gestão agressiva de background é conhecida.

**Modo 2 sem permissão é bloqueio, não aviso.** O usuário pediu para aplicar a regra aos
contatos e isso simplesmente não acontece: o Telecom nem entrega essas chamadas ao app.
Chamar de "aviso" seria mentira.

### Testar um número

O campo de teste roda a decisão **real** — o mesmo `CallScreeningPolicy` que o serviço
usa, contra as regras e o histórico que já estão no aparelho — e mostra o que
aconteceria se aquele número ligasse agora: qual regra pega a ligação, quantas
tentativas já existem na janela, e o veredito.

Duas coisas importam aqui:

- **É o mesmo motor, não uma cópia.** Se a tela usasse uma reimplementação da regra,
  poderia dizer uma coisa e a ligação fazer outra — o pior resultado possível para uma
  ferramenta de diagnóstico.
- **Nada é gravado.** A leitura da janela usa `attemptsInWindow` direto, sem o `insert`
  que o caminho do screening faz. Consultar o próprio app não pode contar como alguém
  ter ligado.

A tela também mostra as contagens da base local (tentativas, listas, regras, decisões
registradas, versão do banco) e oferece zerar as contagens — com confirmação, porque
zerar devolve as chamadas a quem já estava perto do limite.

---

## 16. Bloqueio por biometria

Opcional, desligado por padrão. Quando ligado, o app pede digital, rosto ou a senha do
aparelho ao voltar para o primeiro plano.

O que ele protege é o que o app acumula: quem ligou, quando, e as suas regras — de quem
pega o celular **já destravado**. Não há criptografia envolvida e o README não vai
fingir que há: o banco já está na área privada do app, inacessível a outros aplicativos,
e uma chave guardada no mesmo aparelho não sustentaria a promessa.

Três decisões de projeto:

1. **A senha do aparelho é sempre aceita** (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`). Sem
   isso, um dedo machucado ou um sensor com defeito trancariam o usuário para fora das
   próprias regras, sem nenhuma forma de desligar o recurso.
2. **Falha de disponibilidade libera, não tranca.** Se o aparelho deixar de ter qualquer
   forma de autenticação cadastrada, o app abre. Uma tranca cuja chave deixou de existir
   não protege nada — só impede o dono de entrar.
3. **Tolerância de 30 s ao voltar.** Sem ela, cada ida a uma tela do sistema — conceder
   uma permissão, escolher onde salvar o backup, definir o app como filtro — devolveria
   o usuário a uma tela de bloqueio.

`BIOMETRIC_WEAK` e não `BIOMETRIC_STRONG` porque não há chave criptográfica presa à
autenticação: exigir o nível forte reduziria a quantidade de aparelhos onde o recurso
funciona sem nenhum ganho real de garantia.

É por causa desta tela que `MainActivity` é uma `FragmentActivity` — `BiometricPrompt`
precisa de um host com `FragmentManager` para sobreviver à recriação da tela durante a
autenticação. Não há Fragment nenhum na interface.

---

## 17. Exportar e importar as regras

JSON indentado, com os nomes de campo por extenso, gravado onde o usuário escolher pelo
seletor do sistema (SAF — nenhuma permissão de armazenamento é necessária).

```json
{
  "app": "br.dev.callguard.backup",
  "formatVersion": 1,
  "settings": { "maxAllowedCalls": 3, "windowMinutes": 15, ... },
  "schedule": { "enabled": true, "startMinuteOfDay": 1320, ... },
  "allowlist": [ { "number": "+55...", "label": "Mãe" } ],
  "blocklist": [ ... ],
  "customRules": [ ... ]
}
```

Legível de propósito: o dono do arquivo consegue conferir o que está levando embora sem
ferramenta nenhuma. Um arquivo de exportação que o dono não consegue inspecionar é um
pedido de confiança sem contrapartida.

**A importação substitui, não mescla.** Mesclar parece mais gentil, mas produz um estado
que ninguém escolheu: um número permitido no aparelho antigo e bloqueado no novo teria
que virar um dos dois em silêncio. Substituir é previsível — depois de importar, a
configuração é exatamente a do arquivo — e a tela pede confirmação explícita antes,
mostrando o resumo do que vai entrar.

**A entrada é tratada como hostil.** É um arquivo qualquer do armazenamento, que pode
estar truncado, editado à mão ou nem ser nosso:

| Situação | Resultado |
|---|---|
| não é JSON | recusa nomeada |
| é JSON de outro app | recusa nomeada |
| `formatVersion` maior que a suportada | recusa — abrir arquivo do futuro adivinhando o que mudou é como se perde dado |
| bloco de ajustes ausente | recusa |
| entrada de lista sem número | a **entrada** é descartada, não o arquivo |
| valor fora de faixa | corrigido pelas mesmas rotinas que já protegem a persistência |

Nenhuma exceção vaza para a interface: toda recusa vira uma frase específica.

---

## 18. A abertura do app

Desenhada em `Canvas`, sem imagem, sem Lottie e sem biblioteca de animação. Seis tempos,
preto e branco, alto contraste, sem gradiente, sem neon, sem 3D:

1. um ponto pulsa no centro — a chamada chegando;
2. anel após anel sai dele, cada um durando menos que o anterior — a insistência;
3. um escudo se desenha em volta, do topo para a ponta de baixo, pelos **dois lados ao
   mesmo tempo** (um escudo que se fecha lê diferente de um escudo que é contornado);
4. os anéis seguintes desaceleram ao encostar nele e morrem ali — sem explosão, sem X,
   sem vermelho: o bloqueio do produto também é silencioso;
5. o escudo se enche de branco de baixo para cima, com menisco, e o telefone dentro dele
   inverte para preto — o estado *ligado*, dito por forma e não por rótulo;
6. a marca é revelada por uma cortina da esquerda para a direita, um fio se desenha
   embaixo dela e a assinatura aparece.

Terminado o sexto tempo, **a cena congela exatamente onde parou** e aparece *"clique em
qualquer lugar da tela"*, respirando devagar. Nada mais se move. O app não some sozinho:
sai no toque.

Sobre a fluidez: um único relógio linear comanda a coreografia e cada elemento deriva o
próprio tempo dele. O progresso é lido **apenas** dentro de `Canvas`, de
`graphicsLayer { }` e de `drawWithContent { }` — ou seja, nas fases de desenho e de
layer, nunca na composição. O quadro é refeito sem recompor nada, e a animação acompanha
a taxa real da tela, 120 Hz onde ela existe. `Path`, `PathMeasure` e a medição do
contorno do escudo ficam num cache reconstruído só quando o tamanho da tela muda: alocar
esses objetos a cada quadro geraria lixo suficiente para o coletor causar engasgo
visível.

Esta tela é a única que não passou pelo design system da seção 19: ela é desenhada em
`Canvas` com a própria paleta, que por construção já é o mesmo preto e branco. Mexer nela
durante o redesenho da interface interna só criaria risco sem mudar um pixel.

Nota: não é possível animar a tela do **instalador** do Android. Esta é a abertura do
próprio app.

---

## 19. Design system

A interface foi refeita sobre um sistema visual próprio. A motivação foi concreta: a
versão anterior usava `dynamicDarkColorScheme`, então o app assumia a cor do papel de
parede do usuário, e todo agrupamento era feito com `Card` — um cartão por seção, um por
item de lista, um por linha. O resultado tinha a aparência de um formulário do Android,
não a de um produto.

**Onde mora:** `ui/design/`. Quatro arquivos, um papel cada.

| Arquivo | O que define |
|---|---|
| `Tokens.kt` | `CgColor`, `CgSpace`, `CgShape`, `CgSize`, `CgMotion` |
| `Type.kt` | `CgType` — sete estilos nomeados, mais o mapeamento para os slots do Material |
| `Foundation.kt` | casca de tela, cabeçalho de seção, divisor, aviso, estado vazio, métrica, bloco de estado |
| `Controls.kt` | botões, interruptor, chips de escolha, campo de texto |
| `ListItems.kt` | item de lista, linha de navegação, par rótulo/valor, etiqueta |
| `Dialog.kt` | diálogo e caixa de destaque |

### As decisões que mudam a aparência

**Sem cor dinâmica e sem tema claro.** A identidade é fixa. `MaterialTheme.colorScheme`
continua definido, mas apenas para que qualquer componente do framework que sobre caia na
paleta certa — a fonte de verdade é `CgColor`.

**Preto de verdade** (`#000000`) como fundo, e superfícies acima dele usadas com
parcimônia (`#0E0E0E`, `#161616`, `#1F1F1F`). Quando tudo vira cartão cinza, o preto
deixa de ser protagonista e a tela volta a parecer template.

**Cartões substituídos por seções.** Um rótulo em caixa alta sobre o fundo, itens
diretamente sobre ele, e um traço de 1 dp entre as linhas. Faz o mesmo trabalho de
separação que a moldura fazia, gastando um terço da altura e sem criar uma borda a cada
60 dp de rolagem.

**O título vive no conteúdo, não numa `TopAppBar`.** Ele é grande (30 sp, bold) e rola
junto. A barra superior ficou reduzida a uma faixa fina que só existe quando há voltar ou
ações.

**Tipografia carregando a hierarquia.** Sete estilos, não os quinze slots do Material.
`letterSpacing` negativo nos tamanhos grandes e positivo nos pequenos — títulos grandes
com o espaçamento padrão parecem soltos, e rótulos pequenos em caixa alta ficam
ilegíveis sem respiro. Números de telefone em monoespaçada, para que colunas de dígitos
fiquem alinhadas na vertical e a lista seja escaneável.

**Controles desenhados à mão.** O `Switch` do Material sinaliza o estado ligado com a cor
primária e um ícone de confirmação dentro do polegar; o `FilterChip` traz um contêiner
tingido; o `NavigationBar` desenha uma cápsula atrás do ícone ativo. Todos foram
substituídos. No sistema novo, "isto está ativo" tem um vocabulário só: **inversão de
contraste** — fundo branco, conteúdo preto. É o mesmo do botão primário, do chip
selecionado e do interruptor ligado.

**Cor apenas onde significa.** Verde, vermelho e amarelo existem, dessaturados, e
aparecem em pontos de 8 dp, em barras de 2 dp e em etiquetas pequenas — nunca em blocos
preenchidos. Uma tela de histórico com blocos coloridos vira um carnaval em que nada se
destaca.

**Nenhum estado depende só de cor.** O interruptor muda de posição e de preenchimento; a
aba ativa ganha um traço acima; a verificação do diagnóstico tem o texto do detalhe
dizendo o que a cor diz.

### Movimento

Duas regras valem para tudo em `design/Motion.kt`:

**Toda animação explica alguma coisa.** A entrada de tela diz de onde o conteúdo veio; o
número contando diz que ele mudou; a linha que desliza ao apagar diz que a lista se
fechou. Movimento que não responde a *"o que isto está me dizendo?"* é decoração — e
decoração num app que se abre para resolver um incômodo é atrito.

**O progresso é lido na fase de desenho**, dentro de `graphicsLayer { }`, nunca na
composição. O quadro é refeito sem recompor nada; é o que permite manter 120 Hz numa
lista longa em vez de recompor cada linha a cada quadro.

| O que se move | O que diz |
|---|---|
| Transição entre telas | direção: entrar numa tela mais funda vem da direita, voltar vem da esquerda, trocar de aba é fusão vertical curta |
| Entrada da tela | os blocos sobem 16 dp escalonados; roda **uma vez**, então rolar a lista não faz nada piscar |
| Manchete do estado | quando a proteção liga, desliga ou perde a autorização, o texto funde em vez de trocar seco |
| Total de bloqueadas | conta até o novo valor; um número que salta é indistinguível de erro de leitura |
| Itens de lista | `animateItem()` — ao remover, as linhas de baixo sobem em vez de a lista saltar |
| Botões | cedem 2,5% sob o dedo: o ripple confirma o toque, a escala confirma o alvo |
| Diálogos | crescem de 94%; surgir em tamanho final lê como salto de quadro |
| Resultado da simulação | expande a partir do botão — é resposta ao que a pessoa pediu, não um bloco que sempre esteve ali |
| Etiqueta de permissão | funde ao virar "concedida", confirmando que o pedido funcionou |
| Marca no cabeçalho | o escudo se fecha e o branco sobe junto com a entrada da tela — os dois tempos da abertura, em miniatura |
| Sinal de estado | ondas saem do ponto nos dois estados; o **ritmo** é que muda — protegido respira (2,8 s), sem proteção insiste (1,5 s). A cadência carrega a diferença junto com a cor, para quem não distingue matiz ou só olha de relance |

Durações: 180 ms para chips, 300 ms para interruptores e abas, 440 ms para expansões e
entradas. Sem elasticidade e sem bounce em lugar nenhum.

**A marca é desenhada, não importada.** `design/LogoMark.kt` desenha o escudo com o
telefone recortado em `Canvas` — a mesma silhueta do ícone do launcher e do quadro final
da abertura. Repetir a forma em três lugares é o que faz o app ser reconhecido antes de o
nome ser lido; e, sendo desenho e não recurso, ela acompanha a cor do tema e pode ser
animada por progresso.

Um detalhe de layout que parece bug e não é: as ondas do sinal vão de 5 dp a ~21 dp de
raio a partir de um `Canvas` de 10 dp. O Compose não recorta o desenho às bordas do
layout a menos que se peça, então elas transbordam de propósito — do contrário a linha
inteira teria que reservar 42 dp de largura e empurraria o texto para o lado.

**Movimento reduzido é respeitado de graça.** Quem liga "Remover animações" na
acessibilidade do Android zera `ANIMATOR_DURATION_SCALE`, e o Compose propaga isso pelo
`MotionDurationScale` do recompositor: `Animatable` e `animate*AsState` saltam direto
para o valor final. Nenhum código do app precisa checar essa configuração — mas todo o
movimento aqui é feito com essas APIs justamente por isso.

### Acessibilidade

O minimalismo não é desculpa. Interruptores usam `toggleable` e chips usam `selectable`,
que carregam o estado para o leitor de tela (um `clickable` com `Role.Switch` anuncia o
controle mas não diz como ele está). Todos os alvos de toque têm no mínimo 48 dp, e as
alturas de botão, chip e barra de abas são **mínimas** e não fixas — com a fonte do
sistema ampliada, uma altura fixa cortaria o rótulo.

---

## 20. Instalação

### Abrir no Android Studio

1. Android Studio Ladybug ou mais recente.
2. **File → Open** e selecione a pasta raiz do repositório (`callguard-android/`).
3. O Android Studio detecta o Gradle e cria `local.properties` com o caminho do SDK.
   Se não criar, copie `local.properties.example` e ajuste `sdk.dir`.
4. **File → Sync Project with Gradle Files**. Na primeira vez o Gradle baixa AGP,
   Kotlin e as bibliotecas — leva alguns minutos.
5. Requisitos: **JDK 17+** e **Android SDK Platform 36**.
   (Tools → SDK Manager → SDK Platforms → Android 16 / API 36.)

### Compilar e gerar o APK debug

Pela linha de comando, na raiz do repositório:

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

Rodar os testes da regra:

```bash
./gradlew testDebugUnitTest
```

Relatório em `app/build/reports/tests/testDebugUnitTest/index.html`.

### Instalar no Samsung

No aparelho: **Configurações → Sobre o telefone → Informações de software** e toque
7 vezes em **Número de compilação** para liberar as **Opções do desenvolvedor**. Depois,
em Opções do desenvolvedor, ative **Depuração USB**.

```bash
adb devices          # confirme que o aparelho aparece e autorize o prompt na tela
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou simplesmente **Run ▶** no Android Studio com o aparelho conectado.

> O ADB é usado só para instalar. O app **não** depende de ADB para funcionar.

### Conceder as autorizações

1. Abra o CallGuard. O cartão do topo estará vermelho: *"Proteção inativa"*.
2. Toque em **"Definir como app de filtragem de chamadas"**.
3. O Android abre o diálogo oficial *"Permitir que o CallGuard identifique e bloqueie
   chamadas?"* (na One UI pode aparecer como **App de ID de chamada e proteção contra
   spam**). Confirme.
4. O cartão fica verde: *"Proteção ativa"*.
5. **Permissão de contatos:** só é pedida se você ligar *"Aplicar a regra também aos
   contatos salvos"*. Se não ligar, o app nunca pede — e contatos nunca são bloqueados.

Caminho alternativo pelas Configurações da Samsung:
**Configurações → Aplicativos → ⋮ (menu) → Aplicativos padrão → App de ID de chamada e
proteção contra spam → CallGuard**.

---

## 21. Teste real com outro telefone

Configure primeiro, para não esperar muito:

- Máximo de chamadas: **1**
- Intervalo: **5 minutos**

Assim a **2ª** chamada já é bloqueada e o teste leva menos de um minuto.

**Procedimento seguro** (use um segundo celular seu, ou peça a alguém — nunca teste com
números de emergência ou de terceiros desconhecidos):

1. Garanta que o número de teste **não está na sua agenda** e **não está na allowlist**.
   Se estiver na agenda e você não concedeu `READ_CONTACTS`, a chamada nem chega ao app.
2. **1ª chamada** do telefone B para o telefone A → o telefone A toca normalmente.
   Desligue no telefone B.
3. **2ª chamada** → o telefone A **não toca**. No telefone B a ligação cai
   imediatamente, como se você tivesse recusado.
4. Abra o CallGuard → **Chamadas bloqueadas**. O registro aparece com data/hora,
   quantidade de tentativas e o motivo *"Limite de chamadas excedido"*.
5. Confira também o app Telefone da Samsung: a chamada aparece no histórico marcada
   como bloqueada. Isso é esperado (veja a seção 10).

**Testando o reset automático:** espere passar o intervalo configurado (5 minutos) sem
nenhuma ligação e ligue de novo — a chamada passa, porque as tentativas anteriores
saíram da janela.

**Voltando ao padrão:** máximo **3**, intervalo **15 minutos**. Com essa configuração o
teste seria: 1ª, 2ª e 3ª passam; a 4ª dentro de 15 minutos é rejeitada.

**Acompanhando pelo logcat** (opcional, não mostra números de telefone):

```bash
adb logcat -s CallGuardScreening
```

Saída esperada:

```
I CallGuardScreening: Screening decidiu: ALLOW(UNDER_GLOBAL_LIMIT)
I CallGuardScreening: Screening decidiu: BLOCK(GLOBAL_LIMIT_EXCEEDED, tentativas=4, regra=GLOBAL)
I CallGuardScreening: Screening decidiu: BLOCK(PERMANENT_BLOCKLIST, tentativas=0, regra=null)
```

---

## 22. Troubleshooting Samsung

### O serviço não recebe chamada nenhuma

- Confirme o papel: **Configurações → Aplicativos → ⋮ → Aplicativos padrão → App de ID
  de chamada e proteção contra spam**. Tem que estar o CallGuard.
- Só um app pode ter o papel. Se você usa Truecaller, Whoscall, Hiya ou o próprio
  "Smart Call" da Samsung, ele está ocupando a vaga — escolher o CallGuard tira o outro.
- Verifique com `adb shell dumpsys role | grep -A3 CALL_SCREENING`.
- Chamadas de WhatsApp/Telegram **não passam** por `CallScreeningService`. É outro
  caminho no sistema, sem API pública de filtragem.

### O app não aparece na lista de seleção

- O `<service>` precisa de `android:exported="true"` **e** do intent-filter
  `android.telecom.CallScreeningService`. Confira se a build instalada é a certa:
  `adb shell dumpsys package br.dev.callguard | grep -A5 telecom`.
- Em builds release com R8, a classe do serviço não pode ser renomeada. A regra em
  `proguard-rules.pro` cuida disso — se você alterou o nome do pacote, atualize-a.
- Perfil de trabalho / Samsung Knox: o papel é por perfil. Instale no perfil pessoal.

### As chamadas continuam tocando

- Se a resposta demorar mais de 5 s, o framework ignora o app e a chamada toca. O
  orçamento interno é de 3 s e o app falha para `ALLOW` — então, na dúvida, ele toca.
  Confirme no logcat se saiu `BLOCK` ou `TIMEOUT_FAILSAFE`.
- Verifique se o número não está na allowlist e se o toggle principal está ligado.
- **Número na agenda + Modo 1**: comportamento correto e intencional. O Android nem
  entrega essas chamadas.
- **Número não apresentado** (privado/restrito): não é interceptável. Veja a seção 1.

### Funciona só às vezes

Quase sempre é gerenciamento de energia da One UI matando o processo de forma agressiva:

1. **Configurações → Bateria → Limites de uso em segundo plano** — garanta que o
   CallGuard **não** está em *"Apps em suspensão"* nem em *"Apps em suspensão profunda"*.
2. **Configurações → Aplicativos → CallGuard → Bateria → Irrestrito.**
3. **Configurações → Bateria → ⋮ → Otimização automática → desative "Reiniciar quando
   necessário"**, ou ao menos confirme que o app não é afetado.
4. Desative **"Colocar apps não usados em suspensão"** para este app.

> O app **não** mantém ForegroundService. O Telecom faz bind sob demanda — esse é o
> mecanismo oficial e correto. Mas a "suspensão profunda" da Samsung pode impedir até o
> bind do sistema, e é justamente por isso que os passos acima importam. Um
> ForegroundService eterno não é a solução: ele consumiria bateria e ainda assim poderia
> ser suspenso.

### A permissão de contatos não aparece

Ela **só** é solicitada quando você liga *"Aplicar a regra também aos contatos salvos"*.
É intencional. Se você negou antes, o Android pode não perguntar de novo: vá em
**Configurações → Aplicativos → CallGuard → Permissões → Contatos → Permitir**.

### Um contato salvo não chega ao serviço

Comportamento do sistema, não bug: o Telecom pula o bind quando o número está na agenda
e o app não tem `READ_CONTACTS`. Para que contatos cheguem ao app, ligue o Modo 2 **e**
conceda a permissão.

### O bloqueio "vaza" um toque curto

Alguns aparelhos iniciam o áudio do toque antes de a resposta do screening chegar. Isso
depende da rapidez do bind e não é controlável pela API. Manter o app fora da suspensão
de bateria (acima) reduz bastante o efeito.

---

## 23. Estado verificado da build

Compilado e testado nesta máquina antes da entrega:

```
> Task :app:compileDebugKotlin        (sem erros)
> Task :app:kspDebugKotlin            (Room gerou os DAOs, schema v3 exportado)
> Task :app:testDebugUnitTest         159 testes, 0 falhas
> Task :app:lintVitalRelease          (sem erros fatais)
> Task :app:assembleRelease           (R8 + shrinkResources)
BUILD SUCCESSFUL
```

| Suíte | Testes | Falhas |
|---|---|---|
| `CallScreeningPolicyTest` | 37 | 0 |
| `DiagnosticsAssemblerTest` | 14 | 0 |
| `PhoneOriginTest` | 11 | 0 |
| `SchedulePolicyTest` | 11 | 0 |
| `BackupCodecTest` | 11 | 0 |
| `CallAttemptDaoTest` | 6 | 0 |
| `PermissionCatalogTest` | 11 | 0 |
| `NumberPatternTest` | 12 | 0 |
| `PatternSuggesterTest` | 10 | 0 |
| `WindowFormatTest` | 8 | 0 |
| `HomeScreenInteractionTest` | 9 | 0 |
| `CallerIdCodesTest` | 6 | 0 |
| `BrazilPhoneRulesTest` | 5 | 0 |
| `PhoneNumberMaskerTest` | 3 | 0 |
| `ProtectionSettingsTest` | 5 | 0 |
| **Total** | **159** | **0** |

APK release: **3,7 MB** com R8 (`CallGuard-2.7.0.apk`, versionCode 15). O APK debug fica
em ~31 MB por carregar ferramental de desenvolvimento — serve para depurar, não para
distribuir.

Após a minificação, foi conferido no dex que as classes instanciadas pelo sistema **por
nome** sobreviveram ao R8: `InsistentCallScreeningService`, `CallGuardApplication`,
`MainActivity`, `CallGuardDatabase_Impl`. É o risco real de um build minificado — o
compilador não vê essas referências, elas vêm do manifesto.

Permissões efetivamente presentes no APK (via `aapt2 dump permissions`):

```
uses-permission: name='android.permission.READ_CONTACTS'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.CALL_PHONE'
uses-permission: name='android.permission.USE_BIOMETRIC'
uses-permission: name='br.dev.callguard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

Sem `INTERNET` e sem `USE_FINGERPRINT` — esta última removida do manifesto mergeado
porque o `minSdk` 29 nunca a consultaria.

Serviço registrado no manifesto mergeado:

```
service br.dev.callguard.screening.InsistentCallScreeningService
  permission = android.permission.BIND_SCREENING_SERVICE
  action     = android.telecom.CallScreeningService
minSdkVersion=29  targetSdkVersion=36
```

Assinatura conferida com `apksigner verify --print-certs`: a mesma chave das versões
anteriores, então o APK instala por cima sem desinstalar nem perder dados.

Versões usadas: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21, KSP 2.2.21-2.0.5,
Compose BOM 2026.06.01, Room 2.8.4, DataStore 1.2.1, biometric 1.1.0,
compileSdk/targetSdk 36, JDK 17.

O que **não** foi possível verificar aqui, por não haver aparelho: o comportamento em
tempo de execução no Samsung — incluindo o `BiometricPrompt` real e a suavidade da
abertura em 120 Hz. As seções 20 e 21 existem exatamente para isso.

---

## 24. Licença e escopo

Uso pessoal. Todos os dados ficam no aparelho; nada é enviado para lugar nenhum.
