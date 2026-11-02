package com.downshift.app.core.probe

enum class ProbeStatus(val ko: String) {
    WAITING("대기"),
    RECEIVED("수신"),
    CHANGING("변화 확인"),
    DENIED("권한 거부"),
    UNSUPPORTED("미지원"),
}

data class ProbeRow(
    val id: String,
    val label: String,
    val source: String,
    val status: ProbeStatus,
    val lastValue: String?,
    val count: Int,
    val lastAtMs: Long?,
    val note: String?,
)

/** What each probed signal did during the session: never arrived, arrived, changed, or was refused. */
class ProbeLedger {
    private class Entry(val id: String, val label: String, val source: String) {
        var count = 0
        var lastValue: String? = null
        var lastAtMs: Long? = null
        val distinct = LinkedHashSet<String>()
        var blocked: ProbeStatus? = null
        var note: String? = null
    }

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun expect(id: String, label: String, source: String) {
        entries.getOrPut(id) { Entry(id, label, source) }
    }

    @Synchronized
    fun record(id: String, value: String, atMs: Long) {
        val e = entries.getOrPut(id) { Entry(id, id, "") }
        e.count++
        e.lastValue = value
        e.lastAtMs = atMs
        if (e.distinct.size < MAX_DISTINCT) e.distinct.add(value)
    }

    @Synchronized
    fun deny(id: String, reason: String) = block(id, ProbeStatus.DENIED, reason)

    @Synchronized
    fun unsupported(id: String, reason: String) = block(id, ProbeStatus.UNSUPPORTED, reason)

    private fun block(id: String, status: ProbeStatus, reason: String) {
        val e = entries.getOrPut(id) { Entry(id, id, "") }
        e.blocked = status
        e.note = reason
    }

    @Synchronized
    fun rows(): List<ProbeRow> = entries.values.map { e ->
        val status = when {
            e.distinct.size >= 2 -> ProbeStatus.CHANGING
            e.count > 0 -> ProbeStatus.RECEIVED
            else -> e.blocked ?: ProbeStatus.WAITING
        }
        ProbeRow(e.id, e.label, e.source, status, e.lastValue, e.count, e.lastAtMs, e.note)
    }

    fun toMarkdown(): String = buildString {
        appendLine("| 신호 | 출처 | 상태 | 마지막 값 | 횟수 | 메모 |")
        appendLine("|---|---|---|---|---|---|")
        rows().forEach { r ->
            appendLine("| ${cell(r.label)} | ${cell(r.source)} | ${r.status.ko} | ${cell(r.lastValue ?: "-")} | ${r.count} | ${cell(r.note ?: "")} |")
        }
    }

    private fun cell(s: String) = s.replace('|', '/').replace('\n', ' ')

    private companion object {
        const val MAX_DISTINCT = 64
    }
}
