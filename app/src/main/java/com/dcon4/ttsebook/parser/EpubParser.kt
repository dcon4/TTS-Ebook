package com.dcon4.ttsebook.parser

import com.dcon4.ttsebook.data.ChapterLink
import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.EbookImage
import com.dcon4.ttsebook.data.EbookParser
import com.dcon4.ttsebook.debug.DebugLogger
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser : EbookParser {

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val nav: Boolean)

    override fun parse(file: File, displayPath: String): EbookBook {
        val t0 = System.currentTimeMillis()
        return ZipFile(file).use { zip ->
            parseZip(zip, displayPath).also { book ->
                DebugLogger.log("EpubParser", "parsed in ${System.currentTimeMillis() - t0}ms chapters=${book.chapters.size} title=${book.title}")
            }
        }
    }

    override fun supportsFormat(format: String): Boolean {
        return format.equals("epub", ignoreCase = true)
    }

    private fun parseZip(zip: ZipFile, displayPath: String): EbookBook {
        val opfPath = findOpfPath(zip)
        val opf = readXml(zip, opfPath) ?: throw java.io.IOException("EPUB has no content.opf ($opfPath)")
        val opfDir = opfPath.substringBeforeLast('/', "")
        val title = elementText(opf, "title") ?: displayPath.substringAfterLast('/').removeSuffix(".epub")
        val author = elementText(opf, "creator") ?: "Unknown"

        val manifest = readManifest(opf)
        val spineIds = readSpine(opf)
        val hrefToType = manifest.values.associate { resolveEntry(opfDir, it.href) to it.mediaType }

        val spineItems = spineIds.mapNotNull { manifest[it] }
        val ncxItem = findNcxItem(manifest)
        val htmlItems = (if (spineItems.isNotEmpty()) spineItems else manifest.values)
            .filter { isHtmlType(it) }
            .filter { !it.nav || ncxItem == null }
        val chapterHrefs = htmlItems.map { resolveEntry(opfDir, it.href) }
        val hrefToIndex = chapterHrefs.withIndex().associate { (i, href) -> href to i }

        val chapters = mutableListOf<EbookChapter>()
        var index = 0
        for (href in chapterHrefs) {
            val raw = readEntryText(zip, href) ?: continue
            val baseDir = href.substringBeforeLast('/', "")
            val images = extractImages(raw, baseDir, hrefToType)
            val (content, links) = run {
                val scanned = stripHtmlWithLinks(raw, baseDir, hrefToIndex, index)
                val reference = stripHtml(raw)
                if (scanned.first != reference) {
                    DebugLogger.log("EpubParser", "link text extraction diverged from reference in $href; using reference without links")
                    Pair(reference, emptyList<ChapterLink>())
                } else {
                    scanned
                }
            }
            if (content.isNotBlank()) {
                chapters.add(EbookChapter(index, "Chapter ${index + 1}", content, images, links))
                index++
            }
        }

        applyNcxTitles(zip, manifest, opfDir, chapterHrefs, chapters)

        if (chapters.isEmpty()) {
            val allContent = buildString {
                for (href in chapterHrefs) {
                    val text = readEntryText(zip, href)?.let(::stripHtml) ?: continue
                    if (text.isNotBlank()) append("$text\n\n")
                }
            }
            if (allContent.isNotBlank()) chapters.add(EbookChapter(0, title, allContent))
        }
        if (chapters.isEmpty()) throw java.io.IOException("EPUB contains no readable text")

        val hash = computeHash(chapters)
        return EbookBook(
            id = hash,
            title = title,
            author = author,
            chapters = chapters,
            contentHash = hash
        )
    }

    private fun findOpfPath(zip: ZipFile): String {
        val container = findEntry(zip, "META-INF/container.xml")
        val doc = container?.let { readXml(zip, it.name) }
        if (doc != null) {
            val rootFiles = doc.getElementsByTagName("*")
            for (i in 0 until rootFiles.length) {
                val el = rootFiles.item(i) as Element
                if (localNameOf(el.tagName) == "rootfile") {
                    val full = el.getAttribute("full-path")
                    if (full.isNotBlank()) return full.removePrefix("/")
                }
            }
        }
        for (candidate in listOf("OEBPS/content.opf", "content.opf", "OEBPS/package.opf", "package.opf")) {
            if (findEntry(zip, candidate) != null) return candidate
        }
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (!e.isDirectory && e.name.endsWith(".opf", ignoreCase = true)) return e.name
        }
        throw java.io.IOException("EPUB OPF file not found")
    }

    private fun readManifest(opf: Document): Map<String, ManifestItem> {
        val result = LinkedHashMap<String, ManifestItem>()
        val nodes = opf.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (localNameOf(el.tagName) != "item") continue
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            if (id.isBlank() || href.isBlank()) continue
            val nav = el.getAttribute("properties").split(' ').any { it.equals("nav", ignoreCase = true) }
            result[id] = ManifestItem(id, href, el.getAttribute("media-type"), nav)
        }
        return result
    }

    private fun readSpine(opf: Document): List<String> {
        val result = mutableListOf<String>()
        val nodes = opf.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (localNameOf(el.tagName) != "itemref") continue
            val idref = el.getAttribute("idref")
            if (idref.isNotBlank()) result.add(idref)
        }
        return result
    }

    private fun findNcxItem(manifest: Map<String, ManifestItem>): ManifestItem? {
        return manifest.values.firstOrNull {
            it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) ||
                it.href.endsWith(".ncx", ignoreCase = true)
        }
    }

    private fun applyNcxTitles(
        zip: ZipFile,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
        chapterHrefs: List<String>,
        chapters: MutableList<EbookChapter>
    ) {
        val ncxItem = findNcxItem(manifest) ?: return
        val ncxHref = resolveEntry(opfDir, ncxItem.href)
        val ncxDir = ncxHref.substringBeforeLast('/', "")
        val ncx = readXml(zip, ncxHref) ?: return
        val navPoints = mutableListOf<Pair<String, String>>()
        collectNavPoints(ncx.documentElement, navPoints)
        if (navPoints.isEmpty()) return

        val hrefToIndex = chapterHrefs.withIndex().associate { it.value to it.index }
        for ((label, src) in navPoints) {
            if (label.isBlank() || src.isBlank()) continue
            val candidates = listOf(
                resolveEntry(ncxDir, src),
                resolveEntry(opfDir, src),
                src.removePrefix("/")
            ).distinct()
            val idx = candidates.firstNotNullOfOrNull { hrefToIndex[it] } ?: continue
            if (idx in chapters.indices && chapters[idx].title.startsWith("Chapter ")) {
                chapters[idx] = chapters[idx].copy(title = label)
            }
        }
    }

    private fun collectNavPoints(el: Element, out: MutableList<Pair<String, String>>) {
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val child = n as Element
            if (localNameOf(child.tagName) == "navPoint") {
                val label = directChild(child, "navLabel")?.let { directChild(it, "text") }?.textContent?.trim().orEmpty()
                val src = directChild(child, "content")?.getAttribute("src").orEmpty()
                out.add(label to src)
            }
            collectNavPoints(child, out)
        }
    }

    private fun directChild(el: Element, localName: String): Element? {
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val child = n as Element
            if (localNameOf(child.tagName) == localName) return child
        }
        return null
    }

    private fun extractImages(html: String, baseDir: String, hrefToType: Map<String, String>): List<EbookImage> {
        val images = mutableListOf<EbookImage>()
        val imgRegex = Regex("<img[^>]*>", RegexOption.IGNORE_CASE)
        val srcRegex = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val altRegex = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val blockRegex = Regex("(?i)<(p|div|li|h[1-6]|section|article)[\\s>]")
        var anchor = 0
        var scanFrom = 0
        for (match in imgRegex.findAll(html)) {
            var blockCount = 0
            var cursor = scanFrom
            while (true) {
                val b = blockRegex.find(html, cursor) ?: break
                if (b.range.first >= match.range.first) break
                blockCount++
                cursor = b.range.last + 1
            }
            anchor += blockCount
            scanFrom = match.range.first
            val src = srcRegex.find(match.value)?.groupValues?.get(1) ?: continue
            val resolved = resolveEntry(baseDir, src)
            if (resolved.contains("://")) continue
            val alt = altRegex.find(match.value)?.groupValues?.get(1)?.trim().orEmpty()
            val label = alt.ifEmpty { src.substringAfterLast('/') }
            images.add(EbookImage(anchor, label, hrefToType[resolved], resolved))
        }
        return images
    }

    private fun findEntry(zip: ZipFile, name: String): ZipEntry? {
        zip.getEntry(name)?.let { return it }
        zip.getEntry(name.removePrefix("/"))?.let { return it }
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (!e.isDirectory && e.name.equals(name, ignoreCase = true)) return e
        }
        return null
    }

    private fun readEntryText(zip: ZipFile, name: String): String? {
        val entry = findEntry(zip, name) ?: return null
        return try {
            zip.getInputStream(entry).use { it.reader(Charsets.UTF_8).readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun readXml(zip: ZipFile, name: String): Document? {
        val entry = findEntry(zip, name) ?: return null
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isExpandEntityReferences = false
            try {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {
                // features not supported by this parser — safe to continue
            }
            val builder = factory.newDocumentBuilder()
            zip.getInputStream(entry).use { builder.parse(it) }
        } catch (e: Exception) {
            DebugLogger.log("EpubParser", "XML parse failed for $name: ${e.message}")
            null
        }
    }

    private fun elementText(doc: Document, localName: String): String? {
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (localNameOf(el.tagName) == localName) {
                return el.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun resolveEntry(base: String, href: String): String {
        val clean = href.substringBefore('#').trim()
        if (clean.isEmpty()) return base
        if (clean.startsWith("/")) return clean.removePrefix("/")
        if (clean.contains("://")) return clean
        val joined = if (base.isEmpty()) clean else "$base/$clean"
        val parts = mutableListOf<String>()
        for (p in joined.split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }

    private fun localNameOf(tag: String): String = tag.substringAfter(':')

    private fun isHtmlType(item: ManifestItem): Boolean {
        val mt = item.mediaType.lowercase()
        return mt.contains("html") || item.href.endsWith(".html", true) ||
            item.href.endsWith(".htm", true) || item.href.endsWith(".xhtml", true)
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&[a-zA-Z]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isHtmlWs(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

    private fun hrefOf(tag: String): String? {
        val m = Regex("""href\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
            .find(tag) ?: return null
        return m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] }
    }

    private fun stripHtmlWithLinks(
        html: String,
        baseDir: String,
        hrefToIndex: Map<String, Int>,
        currentIndex: Int
    ): Pair<String, List<ChapterLink>> {
        val sb = StringBuilder()
        val hrefStack = ArrayDeque<String>()
        val startStack = ArrayDeque<Int>()
        val links = mutableListOf<ChapterLink>()
        val lower = html.lowercase()

        var lastWasSpace = true
        val emitWs = { if (!lastWasSpace) { sb.append(' '); lastWasSpace = true } }

        var i = 0
        while (i < html.length) {
            if (html[i] == '<') {
                val close = html.indexOf('>', i)
                if (close == -1) {
                    for (k in i until html.length) {
                        val c = html[k]
                        if (isHtmlWs(c)) emitWs() else { sb.append(c); lastWasSpace = false }
                    }
                    break
                }
                val tag = html.substring(i, close + 1)
                val lowerTag = lower.substring(i, close + 1)
                if (lowerTag.startsWith("<script") || lowerTag.startsWith("<style")) {
                    val tagName = lowerTag.substring(1).substringBefore('>').substringBefore(' ')
                    val closing = lower.indexOf("</$tagName", close + 1)
                    val blockEnd = if (closing >= 0) lower.indexOf('>', closing) else -1
                    i = if (blockEnd >= 0) blockEnd + 1 else close + 1
                } else if (lowerTag.startsWith("</a") &&
                    (lowerTag.length == 4 || isHtmlWs(lowerTag[3]))
                ) {
                    if (hrefStack.isNotEmpty()) {
                        val href = hrefStack.removeLast()
                        val start = startStack.removeLast()
                        if (start < sb.length) {
                            val label = sb.substring(start, sb.length)
                            links.add(buildLink(start, sb.length, label, href, baseDir, hrefToIndex, currentIndex))
                        }
                    }
                    i = close + 1
                } else if (lowerTag.startsWith("<a") &&
                    (lowerTag.length == 3 || isHtmlWs(lowerTag[2]))
                ) {
                    val href = hrefOf(tag)
                    if (href != null) {
                        if (hrefStack.isNotEmpty()) {
                            hrefStack.removeLast()
                            startStack.removeLast()
                        }
                        hrefStack.addLast(href)
                        startStack.addLast(sb.length)
                    }
                    i = close + 1
                } else {
                    i = close + 1
                }
            } else if (html[i] == '&') {
                if (html.startsWith("&nbsp;", i)) {
                    emitWs(); i += 6
                } else {
                    val semicolon = html.indexOf(';', i)
                    if (semicolon > i && semicolon - i <= 10 &&
                        html.substring(i + 1, semicolon).isNotEmpty() &&
                        html.substring(i + 1, semicolon).all { it in 'a'..'z' || it in 'A'..'Z' }
                    ) {
                        emitWs(); i = semicolon + 1
                    } else {
                        sb.append('&'); lastWasSpace = false; i++
                    }
                }
            } else if (isHtmlWs(html[i])) {
                emitWs(); i++
            } else {
                sb.append(html[i]); lastWasSpace = false; i++
            }
        }

        if (sb.isNotEmpty() && sb.last() == ' ') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString() to links
    }

    private fun buildLink(
        start: Int,
        end: Int,
        label: String,
        href: String,
        baseDir: String,
        hrefToIndex: Map<String, Int>,
        currentIndex: Int
    ): ChapterLink {
        val clean = href.substringBefore('#').trim()
        val targetIndex: Int? = when {
            clean.isBlank() || clean.startsWith("#") -> currentIndex
            clean.contains("://") -> null
            else -> hrefToIndex[resolveEntry(baseDir, clean)]
        }
        return ChapterLink(start, end, label, href, targetIndex)
    }

    private fun computeHash(chapters: List<EbookChapter>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chapters.take(3).forEach { chapter ->
            digest.update(chapter.content.take(4096).toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(40)
    }
}
