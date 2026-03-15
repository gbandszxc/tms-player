package com.github.gbandszxc.tvmediaplayer.lyrics

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LrcTimeline(
    val lines: List<LyricLine>,
    val offsetMs: Long
)

object LrcParser {
    private val timeTag = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<LyricLine> {
        return parseTimeline(content).lines
    }

    fun parseTimeline(content: String): LrcTimeline {
        val offset = offsetTag.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return content.lineSequence().flatMap { line ->
            val text = line.replace(timeTag, "").trim()
            timeTag.findAll(line).map { match ->
                val minute = match.groupValues[1].toLong()
                val second = match.groupValues[2].toLong()
                val milliText = match.groupValues[3]
                val milli = if (milliText.isBlank()) 0 else milliText.padEnd(3, '0').take(3).toLong()
                LyricLine(
                    timestampMs = minute * 60_000 + second * 1_000 + milli,
                    text = text
                )
            }
        }.sortedBy { it.timestampMs }.toList().let { LrcTimeline(it, offset) }
    }

    fun findCurrentLineIndex(lines: List<LyricLine>, playbackPositionMs: Long, offsetMs: Long = 0): Int {
        if (lines.isEmpty()) return -1
        val target = playbackPositionMs - offsetMs
        var left = 0
        var right = lines.lastIndex
        var answer = -1
        while (left <= right) {
            val mid = (left + right) ushr 1
            if (lines[mid].timestampMs <= target) {
                answer = mid
                left = mid + 1
            } else {
                right = mid - 1
            }
        }
        return answer
    }
}
