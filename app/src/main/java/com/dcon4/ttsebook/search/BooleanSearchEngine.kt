package com.dcon4.ttsebook.search

import com.dcon4.ttsebook.data.EbookChapter

data class SearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val snippet: String,
    val relevance: Float
)

class BooleanSearchEngine {

    fun search(query: String, chapters: List<EbookChapter>): List<SearchResult> {
        if (query.isBlank() || chapters.isEmpty()) return emptyList()
        val tokens = tokenize(query)
        val results = mutableListOf<SearchResult>()
        for ((ci, chapter) in chapters.withIndex()) {
            val paragraphs = chapter.content.split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            for ((pi, paragraph) in paragraphs.withIndex()) {
                if (evaluate(tokens, paragraph.lowercase())) {
                    val snippet = buildSnippet(paragraph, 120)
                    val relevance = computeRelevance(query, paragraph)
                    results.add(
                        SearchResult(
                            chapterIndex = ci,
                            chapterTitle = chapter.title,
                            paragraphIndex = pi,
                            snippet = snippet,
                            relevance = relevance
                        )
                    )
                }
            }
        }
        return results.sortedByDescending { it.relevance }
    }

    private data class Token(
        val type: TokenType,
        val value: String = "",
        val children: List<Token> = emptyList()
    )

    private enum class TokenType { AND, OR, NOT, PHRASE, WORD, LPAREN, RPAREN }

    private fun tokenize(query: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val s = query.trim()
        while (i < s.length) {
            when {
                s[i] == '(' -> { tokens.add(Token(TokenType.LPAREN)); i++ }
                s[i] == ')' -> { tokens.add(Token(TokenType.RPAREN)); i++ }
                s[i] == '"' -> {
                    val end = s.indexOf('"', i + 1)
                    if (end == -1) { tokens.add(Token(TokenType.PHRASE, s.substring(i + 1).lowercase())); i = s.length }
                    else { tokens.add(Token(TokenType.PHRASE, s.substring(i + 1, end).lowercase())); i = end + 1 }
                }
                s[i] == ' ' || s[i] == '\t' -> i++
                else -> {
                    val end = s.indexOfAny(charArrayOf(' ', '\t', '(', ')'), i).let { if (it == -1) s.length else it }
                    val word = s.substring(i, end).lowercase()
                    when (word.uppercase()) {
                        "AND" -> tokens.add(Token(TokenType.AND))
                        "OR" -> tokens.add(Token(TokenType.OR))
                        "NOT" -> tokens.add(Token(TokenType.NOT))
                        else -> tokens.add(Token(TokenType.WORD, word))
                    }
                    i = end
                }
            }
        }
        return tokens
    }

    private fun evaluate(tokens: List<Token>, text: String): Boolean {
        if (tokens.isEmpty()) return false
        val single = tokens.singleOrNull()
        if (single != null) {
            return when (single.type) {
                TokenType.WORD -> text.contains(single.value)
                TokenType.PHRASE -> text.contains(single.value)
                else -> false
            }
        }
        if (tokens.all { it.type == TokenType.WORD || it.type == TokenType.PHRASE }) {
            return tokens.any { matchToken(it, text) }
        }
        if (tokens.size == 3 && tokens[0].type == TokenType.NOT) {
            return !matchToken(tokens[1], text)
        }
        var hasAnd = tokens.any { it.type == TokenType.AND }
        var hasOr = tokens.any { it.type == TokenType.OR }
        if (hasAnd && !hasOr) {
            val terms = tokens.filter { it.type == TokenType.WORD || it.type == TokenType.PHRASE || it.type == TokenType.NOT }
            var i = 0
            while (i < terms.size) {
                if (terms[i].type == TokenType.NOT && i + 1 < terms.size) {
                    if (matchToken(terms[i + 1], text)) return false
                    i += 2
                } else {
                    if (!matchToken(terms[i], text)) return false
                    i++
                }
            }
            return true
        }
        if (!hasAnd && hasOr) {
            val terms = tokens.filter { it.type == TokenType.WORD || it.type == TokenType.PHRASE }
            return terms.any { matchToken(it, text) }
        }
        return tokens.any { matchToken(it, text) }
    }

    private fun matchToken(token: Token, text: String): Boolean {
        return when (token.type) {
            TokenType.WORD -> text.contains(token.value)
            TokenType.PHRASE -> text.contains(token.value)
            else -> false
        }
    }

    private fun buildSnippet(text: String, maxLen: Int): String {
        if (text.length <= maxLen) return text
        val half = maxLen / 2
        return "..." + text.takeLast(half).takeLast(half) + "..."
    }

    private fun computeRelevance(query: String, text: String): Float {
        val lower = text.lowercase()
        val words = query.lowercase().split(Regex("\\s+")).filter { it !in setOf("and", "or", "not") }
        val matchCount = words.count { lower.contains(it) }
        return matchCount.toFloat() / words.size.toFloat().coerceAtLeast(1f)
    }
}
