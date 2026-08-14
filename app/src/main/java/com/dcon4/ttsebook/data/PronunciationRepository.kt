package com.dcon4.ttsebook.data

import com.dcon4.ttsebook.debug.DebugLogger
import kotlinx.coroutines.flow.Flow
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PronunciationRepository @Inject constructor(
    private val dao: PronunciationDao
) {

    @Volatile
    private var entries: List<PronunciationEntity> = emptyList()

    @Volatile
    private var patterns: List<Pair<Pattern, String>> = emptyList()

    fun getAll(): Flow<List<PronunciationEntity>> = dao.getAll()

    suspend fun add(word: String, replacement: String): Boolean {
        val w = word.trim()
        val r = replacement.trim()
        if (w.isEmpty() || r.isEmpty()) return false
        dao.upsert(PronunciationEntity(word = w, replacement = r))
        reload()
        DebugLogger.log("PronunciationRepository", "Added: '$w' -> '$r'")
        return true
    }

    suspend fun remove(id: Long) {
        dao.delete(id)
        reload()
        DebugLogger.log("PronunciationRepository", "Removed entry id=$id")
    }

    suspend fun setMatchCase(id: Long, matchCase: Boolean) {
        dao.setMatchCase(id, matchCase)
        reload()
        DebugLogger.log("PronunciationRepository", "Set matchCase=$matchCase for entry id=$id")
    }

    suspend fun reload() {
        entries = dao.getAll().let { flow ->
            var list = emptyList<PronunciationEntity>()
            flow.collect { list = it }
            list
        }
        patterns = entries.map { entry ->
            val quoted = Pattern.quote(entry.word)
            val flag = if (entry.matchCase) "" else "(?i)"
            val pattern = if (isSingleWord(entry.word)) {
                Pattern.compile("$flag\\b$quoted\\b")
            } else {
                Pattern.compile("$flag$quoted")
            }
            pattern to entry.replacement
        }
        DebugLogger.verbose("PronunciationRepository", "Loaded ${entries.size} pronunciation entries")
    }

    fun applyTo(text: String): String {
        val pairs = patterns
        if (pairs.isEmpty() || text.isEmpty()) return text
        var result = text
        for ((pattern, replacement) in pairs) {
            result = pattern.matcher(result).replaceAll(Matcher.quoteReplacement(replacement))
        }
        return result
    }

    private fun isSingleWord(word: String): Boolean {
        return word.isNotEmpty() && word.none { it == ' ' || it == '\t' || it == '\n' }
    }
}
