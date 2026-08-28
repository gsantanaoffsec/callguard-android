# CallGuard — proteção contra chamadas insistentes

Aplicativo Android que rejeita automaticamente chamadas de quem liga repetidamente,
usando exclusivamente a arquitetura oficial `android.telecom.CallScreeningService`.

Regra padrão: **as 3 primeiras chamadas do mesmo número em 15 minutos passam; a 4ª é
rejeitada.** Janela deslizante — quem para de ligar volta a passar sozinho.

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

## 2. Arquitetura

Cinco camadas, sem cerimônia inútil:

```
┌───────────────────────────────────────────────────────────────┐
│  ui/          Compose + ViewModel. Não conhece Room nem Telecom │
├───────────────────────────────────────────────────────────────┤
│  screening/   Integração com o Telecom. Traduz Call.Details      │
│               em IncomingCall e ScreeningDecision em CallResponse│
├───────────────────────────────────────────────────────────────┤
│  core/        REGRA DE DECISÃO. Kotlin puro, zero Android.       │
│               InsistentCallPolicy, ProtectionSettings,           │
│               IncomingCall, ScreeningDecision, BrazilPhoneRules  │
├───────────────────────────────────────────────────────────────┤
│  data/        SettingsRepository (DataStore) + Room repositories │
├───────────────────────────────────────────────────────────────┤
│  phone/       PhoneNumberUtils e ContactsContract                │
└───────────────────────────────────────────────────────────────┘
```

| Componente | Responsabilidade |
|---|---|
| `InsistentCallPolicy` | **A regra.** Recebe telefone, horário, histórico, configurações e allowlist; devolve `Allow`/`Block`. Nenhum import de Android — é o que a torna testável. |
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
| `CallGuardViewModel` | Estado da UI; reconsulta papel/permissão a cada `ON_RESUME`. |

---

## 3. Fluxo de uma chamada

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
   ├─ 4. settings.current()          (cache quente, sem I/O)
   ├─ 5. isEmergencyNumber(raw)
   ├─ 6. allowlist.contains()        (cache em memória)
   ├─ 7. contactLookup               (só se protegido + modo 1 + tem permissão)
   │
   ▼
policy.preScreen(call)  ─── decidiu? ──► ALLOW, sem tocar no banco
   │ null (precisa do histórico)
   ▼
callAttemptDao.recordAttemptAndGetPrevious()   ◄── @Transaction:
   │   DELETE expirados; SELECT janela; INSERT atual — atômico
   ▼
policy.evaluate(call, previousAttempts)
   │   anteriores na janela < max  → Allow(UNDER_LIMIT)
   │   anteriores na janela >= max → Block(CALL_LIMIT_EXCEEDED)
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

## 4. Estrutura do projeto

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
        │   │   │   ├── InsistentCallPolicy.kt      ← A REGRA
        │   │   │   ├── ProtectionSettings.kt
        │   │   │   ├── IncomingCall.kt
        │   │   │   ├── ScreeningDecision.kt
        │   │   │   ├── BrazilPhoneRules.kt
        │   │   │   ├── PhoneNumberMasker.kt
        │   │   │   └── PhoneNumberNormalizer.kt
        │   │   ├── data/
        │   │   │   ├── ServiceLocator.kt
        │   │   │   ├── SettingsRepository.kt
        │   │   │   ├── CallHistoryRepository.kt
        │   │   │   ├── AllowlistRepository.kt
        │   │   │   └── db/
        │   │   │       ├── CallGuardDatabase.kt
        │   │   │       ├── Entities.kt
        │   │   │       ├── CallAttemptDao.kt
        │   │   │       ├── AllowlistDao.kt
        │   │   │       └── BlockedCallDao.kt
        │   │   ├── phone/
        │   │   │   ├── TelephonyPhoneNumberNormalizer.kt
        │   │   │   └── ContactLookup.kt
        │   │   ├── screening/
        │   │   │   ├── InsistentCallScreeningService.kt
        │   │   │   ├── BlockedCallNotifier.kt
        │   │   │   └── CallScreeningRoleController.kt
        │   │   └── ui/
        │   │       ├── MainActivity.kt
        │   │       ├── CallGuardViewModel.kt
        │   │       ├── UiState.kt
        │   │       ├── HomeScreen.kt
        │   │       ├── BlockedCallsScreen.kt
        │   │       └── theme/Theme.kt
        │   └── res/
        └── test/kotlin/br/dev/callguard/
            ├── core/
            │   ├── InsistentCallPolicyTest.kt      ← os 10 casos
            │   ├── BrazilPhoneRulesTest.kt
            │   ├── PhoneNumberMaskerTest.kt
            │   └── ProtectionSettingsTest.kt
            └── data/db/
                └── CallAttemptDaoTest.kt           ← prova da atomicidade
```

---

## 5. Dependências

Cada uma está no projeto porque é usada. Não há nada "por via das dúvidas".

| Dependência | Por quê |
|---|---|
| `androidx.core:core-ktx` | `ContextCompat.checkSelfPermission` na checagem de READ_CONTACTS. |
| `androidx.activity:activity-compose` | `setContent`, `enableEdgeToEdge`, `rememberLauncherForActivityResult` (fluxo do RoleManager), `BackHandler`. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `viewModel()` na Activity. |
| `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle`, `LifecycleResumeEffect` — reconsultar o papel ao voltar da tela do sistema. |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `viewModelScope`. |
| `androidx.compose:compose-bom` | Alinha as versões do Compose. |
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

## 6. Decisões de armazenamento

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

**Retenção:** tentativas são apagadas depois de 6 h (ou 2× a janela, o que for maior),
em cada screening. Bloqueios ficam limitados aos 100 mais recentes. Um app que lida com
telefones não deve acumular telefones indefinidamente.

---

## 7. Concorrência

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

## 8. Privacidade e permissões

**Duas permissões declaradas, ambas solicitadas só quando fazem falta:**

- **`READ_CONTACTS`** — pedida apenas ao ligar "aplicar a regra também aos contatos salvos".
- **`POST_NOTIFICATIONS`** — pedida apenas ao ligar "avisar quando bloquear". Sem ela o
  app simplesmente não notifica; nada mais muda.

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

**Permissões deliberadamente NÃO pedidas:** `READ_CALL_LOG` (a API de screening já
entrega o necessário), `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `CALL_PHONE`,
`INTERNET`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`.
O app não é discador padrão e não precisa ser.

**Sobre a segunda permissão no APK:** ao inspecionar o APK aparece também
`br.dev.callguard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Ela é adicionada
automaticamente pelo `androidx.core` para proteger os `BroadcastReceiver` que as próprias
bibliotecas registram em runtime. É de nível *signature*, definida por este app e válida
só dentro dele — não dá acesso a nada e não aparece para o usuário.

**Outras medidas:**
- `android:allowBackup="false"` + regras de extração que excluem tudo: telefones não vão
  para a nuvem nem em transferência entre aparelhos.
- Nenhum log contém número de telefone — só a decisão (`ALLOW(UNDER_LIMIT)`).
- Números aparecem mascarados na tela de bloqueios; revelar é escolha explícita.
- Sem rede: não há `INTERNET` no manifesto, então nem por engano.

---

## 9. `BIND_SCREENING_SERVICE`: a distinção que importa

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

## 10. `CallResponse`: por que esta combinação

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

## 11. Avisos e correção rápida

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

## 12. Aba "Ligar oculto" (CLIR)

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

## 13. Aba "Logs"

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

## 14. Instalação

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

## 15. Teste real com outro telefone

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
I CallGuardScreening: Screening decidiu: ALLOW(UNDER_LIMIT)
I CallGuardScreening: Screening decidiu: BLOCK(CALL_LIMIT_EXCEEDED, tentativas=2)
```

---

## 16. Troubleshooting Samsung

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

## 17. Estado verificado da build

Compilado e testado nesta máquina antes da entrega:

```
> Task :app:compileDebugKotlin        (sem erros)
> Task :app:kspDebugKotlin            (Room gerou os DAOs)
> Task :app:testDebugUnitTest         56 testes, 0 falhas
> Task :app:assembleRelease           (R8 + shrinkResources)
BUILD SUCCESSFUL
```

| Suíte | Testes | Falhas |
|---|---|---|
| `InsistentCallPolicyTest` | 22 | 0 |
| `PhoneOriginTest` | 11 | 0 |
| `CallAttemptDaoTest` | 6 | 0 |
| `CallerIdCodesTest` | 6 | 0 |
| `BrazilPhoneRulesTest` | 5 | 0 |
| `PhoneNumberMaskerTest` | 3 | 0 |
| `ProtectionSettingsTest` | 3 | 0 |

APK release: ~2,7 MB (com R8). O APK debug fica em ~31 MB por carregar ferramental de
desenvolvimento — serve para depurar, não para distribuir.

Após a minificação, foi conferido que as classes instanciadas pelo sistema **por nome**
sobreviveram ao R8 (`InsistentCallScreeningService`, `CallGuardApplication`,
`MainActivity`, `CallGuardDatabase_Impl`). É o risco real de um build minificado: o
compilador não vê essas referências, elas vêm do manifesto.

Permissões efetivamente presentes no APK (via `aapt2 dump permissions`):

```
uses-permission: name='android.permission.READ_CONTACTS'
uses-permission: name='br.dev.callguard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

Serviço registrado no manifesto mergeado:

```
service br.dev.callguard.screening.InsistentCallScreeningService
  permission = android.permission.BIND_SCREENING_SERVICE
  action     = android.telecom.CallScreeningService
minSdkVersion=29  targetSdkVersion=36
```

Versões usadas: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21, KSP 2.2.21-2.0.5,
Compose BOM 2026.06.01, Room 2.8.4, DataStore 1.2.1, compileSdk/targetSdk 36, JDK 17.

O que **não** foi possível verificar aqui, por não haver aparelho: o comportamento em
tempo de execução no Samsung. Os passos 12 e 13 existem exatamente para isso.

---

## 18. Licença e escopo

Uso pessoal. Todos os dados ficam no aparelho; nada é enviado para lugar nenhum.
