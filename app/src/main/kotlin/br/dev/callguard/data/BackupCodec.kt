package br.dev.callguard.data

import br.dev.callguard.core.BackupError
import br.dev.callguard.core.BackupNumber
import br.dev.callguard.core.BackupPayload
import br.dev.callguard.core.BackupRule
import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.core.SchedulePolicy
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Traducao entre [BackupPayload] e o texto do arquivo.
 *
 * JSON com indentacao, e nao um formato binario: o usuario pode abrir o backup em
 * qualquer editor e ver exatamente o que esta levando embora. Um arquivo de exportacao
 * que o dono nao consegue inspecionar e um pedido de confianca sem contrapartida.
 *
 * `org.json` (que ja vem no Android) em vez de kotlinx-serialization: sao seis campos,
 * lidos uma vez por importacao. Uma dependencia e um processador de anotacoes a mais no
 * build nao se pagariam aqui.
 */
object BackupCodec {

    private const val MAGIC = "br.dev.callguard.backup"

    fun encode(payload: BackupPayload): String {
        val raiz = JSONObject()
        raiz.put("app", MAGIC)
        raiz.put("formatVersion", payload.formatVersion)
        raiz.put("exportedAt", payload.exportedAtMillis)
        raiz.put("appVersion", payload.appVersionName)

        raiz.put(
            "settings",
            JSONObject().apply {
                put("protectionEnabled", payload.settings.protectionEnabled)
                put("maxAllowedCalls", payload.settings.maxAllowedCalls)
                put("windowMinutes", payload.settings.windowMinutes)
                put("applyToContacts", payload.settings.applyToContacts)
                put("notifyOnBlock", payload.settings.notifyOnBlock)
                put("biometricLock", payload.settings.biometricLockEnabled)
            },
        )

        raiz.put(
            "schedule",
            JSONObject().apply {
                put("enabled", payload.schedule.enabled)
                put("startMinuteOfDay", payload.schedule.startMinuteOfDay)
                put("endMinuteOfDay", payload.schedule.endMinuteOfDay)
                put("maxAllowedCalls", payload.schedule.maxAllowedCalls)
                put("windowMillis", payload.schedule.windowMillis)
                put(
                    "activeDays",
                    JSONArray().apply { payload.schedule.activeDays.forEach { put(it.name) } },
                )
            },
        )

        raiz.put("allowlist", payload.allowlist.toJsonArray())
        raiz.put("blocklist", payload.blocklist.toJsonArray())
        raiz.put(
            "customRules",
            JSONArray().apply {
                payload.customRules.forEach { regra ->
                    put(
                        JSONObject().apply {
                            put("number", regra.normalizedNumber)
                            put("label", regra.label)
                            put("maxAllowedCalls", regra.maxAllowedCalls)
                            put("windowMillis", regra.windowMillis)
                            put("enabled", regra.enabled)
                        },
                    )
                }
            },
        )

        return raiz.toString(2)
    }

    /**
     * Le um arquivo escolhido pelo usuario.
     *
     * Trata a entrada como hostil por principio: e um arquivo qualquer do
     * armazenamento, que pode estar truncado, editado a mao ou nem ser nosso. Valores
     * fora de faixa sao corrigidos pelas mesmas rotinas que ja protegem a persistencia
     * ([ProtectionSettings.sanitized] e os `coerceIn`), e o que nao da para corrigir
     * vira erro nomeado -- nunca uma excecao vazando para a interface.
     */
    fun decode(text: String): Result<BackupPayload> {
        val raiz = runCatching { JSONObject(text) }.getOrElse {
            return Result.failure(BackupException(BackupError.NOT_JSON))
        }
        if (raiz.optString("app") != MAGIC) {
            return Result.failure(BackupException(BackupError.WRONG_APP))
        }
        val versao = raiz.optInt("formatVersion", -1)
        if (versao <= 0) return Result.failure(BackupException(BackupError.CORRUPTED))
        if (versao > BackupPayload.CURRENT_FORMAT_VERSION) {
            return Result.failure(BackupException(BackupError.FUTURE_VERSION))
        }

        val ajustes = raiz.optJSONObject("settings")
            ?: return Result.failure(BackupException(BackupError.CORRUPTED))

        val settings = runCatching {
            ProtectionSettings.sanitized(
                protectionEnabled = ajustes.optBoolean(
                    "protectionEnabled",
                    ProtectionSettings.DEFAULT_PROTECTION_ENABLED,
                ),
                maxAllowedCalls = ajustes.optInt(
                    "maxAllowedCalls",
                    ProtectionSettings.DEFAULT_MAX_ALLOWED_CALLS,
                ),
                windowMinutes = ajustes.optInt(
                    "windowMinutes",
                    ProtectionSettings.DEFAULT_WINDOW_MINUTES,
                ),
                applyToContacts = ajustes.optBoolean(
                    "applyToContacts",
                    ProtectionSettings.DEFAULT_APPLY_TO_CONTACTS,
                ),
                notifyOnBlock = ajustes.optBoolean(
                    "notifyOnBlock",
                    ProtectionSettings.DEFAULT_NOTIFY_ON_BLOCK,
                ),
                biometricLockEnabled = ajustes.optBoolean(
                    "biometricLock",
                    ProtectionSettings.DEFAULT_BIOMETRIC_LOCK,
                ),
            )
        }.getOrElse { return Result.failure(BackupException(BackupError.CORRUPTED)) }

        val schedule = raiz.optJSONObject("schedule").toSchedulePolicy()

        val payload = BackupPayload(
            formatVersion = versao,
            exportedAtMillis = raiz.optLong("exportedAt", 0L),
            appVersionName = raiz.optString("appVersion", "desconhecida"),
            settings = settings,
            schedule = schedule,
            allowlist = raiz.optJSONArray("allowlist").toNumbers(),
            blocklist = raiz.optJSONArray("blocklist").toNumbers(),
            customRules = raiz.optJSONArray("customRules").toRules(),
        )

        val vazio = payload.allowlist.isEmpty() &&
            payload.blocklist.isEmpty() &&
            payload.customRules.isEmpty() &&
            !payload.schedule.enabled
        // Ajustes sozinhos ainda sao conteudo util; so recusamos o arquivo sem NADA.
        if (vazio && !raiz.has("settings")) {
            return Result.failure(BackupException(BackupError.EMPTY))
        }

        return Result.success(payload)
    }

    private fun List<BackupNumber>.toJsonArray(): JSONArray = JSONArray().apply {
        this@toJsonArray.forEach { entrada ->
            put(
                JSONObject().apply {
                    put("number", entrada.normalizedNumber)
                    put("label", entrada.label)
                },
            )
        }
    }

    private fun JSONArray?.toNumbers(): List<BackupNumber> {
        if (this == null) return emptyList()
        val saida = ArrayList<BackupNumber>(length())
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val numero = item.optString("number").trim()
            // Uma entrada sem numero nao tem como ser aplicada; descartamos a entrada,
            // e nao o arquivo inteiro.
            if (numero.isEmpty()) continue
            saida += BackupNumber(numero, item.optString("label").trim())
        }
        return saida
    }

    private fun JSONArray?.toRules(): List<BackupRule> {
        if (this == null) return emptyList()
        val saida = ArrayList<BackupRule>(length())
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val numero = item.optString("number").trim()
            if (numero.isEmpty()) continue
            saida += BackupRule(
                normalizedNumber = numero,
                label = item.optString("label").trim(),
                maxAllowedCalls = item.optInt("maxAllowedCalls", 1).coerceIn(1, 50),
                windowMillis = item.optLong("windowMillis", TimeUnit.MINUTES.toMillis(15))
                    .coerceIn(TimeUnit.MINUTES.toMillis(1), TimeUnit.HOURS.toMillis(24)),
                enabled = item.optBoolean("enabled", true),
            )
        }
        return saida
    }

    private fun JSONObject?.toSchedulePolicy(): SchedulePolicy {
        if (this == null) return SchedulePolicy()
        val dias = optJSONArray("activeDays")
        val conjunto = if (dias == null) {
            DayOfWeek.entries.toSet()
        } else {
            buildSet {
                for (i in 0 until dias.length()) {
                    runCatching { DayOfWeek.valueOf(dias.optString(i)) }.getOrNull()?.let(::add)
                }
            }.ifEmpty { DayOfWeek.entries.toSet() }
        }
        return SchedulePolicy(
            enabled = optBoolean("enabled", false),
            startMinuteOfDay = optInt("startMinuteOfDay", SchedulePolicy.DEFAULT_START_MINUTE)
                .coerceIn(0, 24 * 60 - 1),
            endMinuteOfDay = optInt("endMinuteOfDay", SchedulePolicy.DEFAULT_END_MINUTE)
                .coerceIn(0, 24 * 60 - 1),
            activeDays = conjunto,
            maxAllowedCalls = optInt("maxAllowedCalls", 1).coerceIn(1, 50),
            windowMillis = optLong("windowMillis", TimeUnit.MINUTES.toMillis(30))
                .coerceAtLeast(TimeUnit.MINUTES.toMillis(1)),
        )
    }
}

/** Erro de leitura de backup, com a causa ja traduzida para o usuario. */
class BackupException(val error: BackupError) : Exception(error.message)
