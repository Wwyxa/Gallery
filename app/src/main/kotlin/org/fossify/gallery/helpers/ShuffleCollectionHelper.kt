package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import kotlin.random.Random

/**
 * Builds shuffle play sequences that keep filename collections together: files in one
 * folder sharing a name prefix and differing only by a trailing sequence number
 * ("trip 01.jpg", "album（2）.jpg", "clip-3.mp4" ...) play as one contiguous, in-order
 * unit instead of being scattered by the shuffle.
 *
 * A trailing number joins a collection when it looks deliberately numbered: it is
 * bracketed ("(1)", "（1）", "[1]"), zero padded ("01") or has a value of at most 999
 * ("1", "10", "-2", " 3"), glued to the prefix or not. Numbers more likely incidental
 * to the name, like 4-digit camera counters ("DSC_1234") or year suffixes, stay out,
 * and the 1-4 digit pattern cannot even match timestamp tails. Missing members are
 * ordinary (files get deleted after numbering), so gaps and any start number, like
 * "00" or "03", are fine. A number elsewhere in the name, e.g. a middle "(5)" marking
 * which collection a file belongs to, is just part of the prefix and never the order.
 */
object ShuffleCollectionHelper {

    // the trailing sequence part of a title (a filename without its extension):
    // an optional run of separators, then either a bracketed or a bare 1-4 digit
    // number, full-width digits included
    private val trailingNumberPattern =
        Regex("""[ _\-·.~]*(?:[（(\[【]([0-9０-９]{1,4})[)）\]】]|([0-9０-９]{1,4}))$""")
    private const val MAX_UNPADDED_NUMBER = 999

    // the name prefix a collection is keyed by, plus the parsed sequence value
    private class ParsedNumber(val prefix: String, val number: Int)
    private class GroupMember(val medium: Medium, val number: Int)

    /** shuffles media keeping every detected collection contiguous and in order */
    fun buildShuffledSequence(media: List<Medium>, random: Random = Random.Default): ArrayList<Medium> =
        flatten(buildShuffledBlocks(media, random))

    /**
     * shuffles media as units: every detected collection becomes one block whose
     * members are ordered by their sequence number, the rest become single-item
     * blocks; only the block order is random
     */
    fun buildShuffledBlocks(media: List<Medium>, random: Random = Random.Default): List<List<Medium>> {
        val singletons = ArrayList<Medium>()
        val groups = HashMap<String, MutableList<GroupMember>>()
        for (medium in media) {
            val parsed = parseTrailingNumber(medium)
            if (parsed == null) {
                singletons.add(medium)
            } else {
                groups.getOrPut("${medium.parentPath}\u0000${parsed.prefix}") { ArrayList() }
                    .add(GroupMember(medium, parsed.number))
            }
        }

        val blocks = ArrayList<List<Medium>>()
        for (group in groups.values) {
            if (group.size >= 2) {
                blocks.add(group.sortedWith(compareBy({ it.number }, { it.medium.name })).map { it.medium })
            } else {
                singletons.add(group.first().medium)
            }
        }
        singletons.forEach { blocks.add(listOf(it)) }
        return blocks.shuffled(random)
    }

    fun flatten(blocks: List<List<Medium>>): ArrayList<Medium> {
        val media = ArrayList<Medium>(blocks.sumOf { it.size })
        for (block in blocks) {
            media.addAll(block)
        }
        return media
    }

    private fun parseTrailingNumber(medium: Medium): ParsedNumber? {
        val title = medium.name.substringBeforeLast('.', medium.name)
        // a match always ends with a digit or a closing bracket, anything else can
        // skip the regex entirely
        val last = title.lastOrNull() ?: return null
        if (last !in '0'..'9' && last !in '０'..'９' && last !in ")）]】") {
            return null
        }

        val match = trailingNumberPattern.find(title) ?: return null
        val digits = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val number = parseDigits(digits) ?: return null

        val bracketed = match.groupValues[1].isNotEmpty()
        val zeroPadded = digits.length > 1 && (digits.first() == '0' || digits.first() == '０')
        if (!bracketed && !zeroPadded && number > MAX_UNPADDED_NUMBER) {
            return null
        }

        val prefix = title.substring(0, match.range.first)
        // a blank prefix means the title is basically just the number, names like
        // "123.jpg" would all collapse into one fake collection
        if (prefix.isBlank()) {
            return null
        }

        return ParsedNumber(prefix, number)
    }

    private fun parseDigits(digits: String): Int? {
        var value = 0
        for (char in digits) {
            val digit = when (char) {
                in '0'..'9' -> char - '0'
                in '０'..'９' -> char - '０'
                else -> return null
            }
            value = value * 10 + digit
        }
        return value
    }
}
