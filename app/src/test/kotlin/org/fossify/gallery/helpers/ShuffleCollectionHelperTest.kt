package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleCollectionHelperTest {
    private val parent = "/storage/emulated/0/Pictures"

    private fun medium(name: String, folder: String = parent) =
        Medium(null, name, "$folder/$name", folder, 0L, 0L, 0L, TYPE_IMAGES, 0, false, 0L, 0L, 0)

    private fun shuffledOf(vararg media: Medium, seed: Int = 1) =
        ShuffleCollectionHelper.buildShuffledSequence(media.toList(), Random(seed))

    private fun blocksOf(vararg media: Medium, seed: Int = 1) =
        ShuffleCollectionHelper.buildShuffledBlocks(media.toList(), Random(seed))

    @Test
    fun collectionStaysContiguousAndInOrder() {
        val sequence = shuffledOf(
            medium("unrelated.jpg"),
            medium("trip 01.jpg"), medium("trip 02.jpg"), medium("trip 03.jpg"),
            medium("trip 04.jpg"), medium("trip 05.jpg"),
            medium("other.png"),
        )

        val startIndex = sequence.indexOfFirst { it.name == "trip 01.jpg" }
        val names = sequence.subList(startIndex, startIndex + 5).map { it.name }
        assertEquals(
            listOf("trip 01.jpg", "trip 02.jpg", "trip 03.jpg", "trip 04.jpg", "trip 05.jpg"),
            names
        )
    }

    @Test
    fun groupsBracketedNumbers() {
        val result = blocksOf(
            medium("album（2）.jpg"),
            medium("album（1）.jpg"),
            medium("album（3）.jpg"),
        )

        assertEquals(
            listOf("album（1）.jpg", "album（2）.jpg", "album（3）.jpg"),
            result.single { it.size == 3 }.map { it.name }
        )
    }

    @Test
    fun groupsMixedExtensionsIntoOneCollection() {
        val result = blocksOf(
            medium("trip 02.png"),
            medium("trip 01.jpg"),
            medium("trip 03.jpg"),
        )

        assertEquals(
            listOf("trip 01.jpg", "trip 02.png", "trip 03.jpg"),
            result.single { it.size == 3 }.map { it.name }
        )
    }

    @Test
    fun ordersNumericallyNotLexicographically() {
        // a complete 1-10 series also proves "2" sorts before "10", which a plain
        // name comparison would get backwards
        val result = blocksOf(
            *(1..10).map { medium("set %d.jpg".format(it)) }.toTypedArray()
        )

        assertEquals(
            (1..10).map { "set %d.jpg".format(it) },
            result.single { it.size == 10 }.map { it.name }
        )
    }

    @Test
    fun parsesFullWidthDigits() {
        val result = blocksOf(
            medium("漫画（２）.jpg"),
            medium("漫画（１）.jpg"),
        )

        assertEquals(
            listOf("漫画（１）.jpg", "漫画（２）.jpg"),
            result.single { it.size == 2 }.map { it.name }
        )
    }

    @Test
    fun toleratesGapsAndNonStandardStarts() {
        // members got deleted after numbering, and a collection may start at 00 or 03
        // instead of 1, so gaps never break a group apart
        val result = blocksOf(
            medium("abc 03.jpg"),
            medium("abc 30.jpg"),
            medium("abc 00.jpg"),
            medium("abc 07.jpg"),
        )

        assertEquals(
            listOf("abc 00.jpg", "abc 03.jpg", "abc 07.jpg", "abc 30.jpg"),
            result.single { it.size == 4 }.map { it.name }
        )
    }

    @Test
    fun sparseBracketedNumbersFormACollection() {
        val result = blocksOf(
            medium("photo (2).jpg"),
            medium("photo (8).jpg"),
        )

        assertEquals(
            listOf("photo (2).jpg", "photo (8).jpg"),
            result.single { it.size == 2 }.map { it.name }
        )
    }

    @Test
    fun cameraTimestampNamesAreNotCollections() {
        val result = blocksOf(
            medium("IMG_20241025_123301.jpg"),
            medium("IMG_20241025_123302.jpg"),
            medium("IMG_20241025_123303.jpg"),
        )

        assertEquals(3, result.size)
        assertTrue(result.all { it.size == 1 })
    }

    @Test
    fun largeSeparatedNumbersAreNotCollections() {
        val result = blocksOf(
            medium("DSC_1234.jpg"),
            medium("DSC_1300.jpg"),
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.size == 1 })
    }

    @Test
    fun collectionsNeverCrossFolders() {
        val result = blocksOf(
            medium("trip 01.jpg", folder = "$parent/A"),
            medium("trip 02.jpg", folder = "$parent/B"),
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.size == 1 })
    }

    @Test
    fun barePrefixFileStaysSingleton() {
        val result = blocksOf(
            medium("abc.jpg"),
            medium("abc 01.jpg"),
            medium("abc 02.jpg"),
        )

        assertEquals(2, result.size)
        assertEquals(
            listOf("abc 01.jpg", "abc 02.jpg"),
            result.single { it.size == 2 }.map { it.name }
        )
    }

    @Test
    fun gluedNumbersAfterAMiddleMarkerFormSeparateCollections() {
        // the middle "(5)" marks which collection a file belongs to and is never the
        // order, the glued trailing numbers are
        val result = blocksOf(
            medium("xxxx(5)xxxx02.jpg"),
            medium("xxxx(4)xxxx01.jpg"),
            medium("xxxx(5)xxxx01.jpg"),
            medium("xxxx(4)xxxx02.jpg"),
        )

        val collections = result.filter { it.size == 2 }
        assertEquals(2, collections.size)
        assertEquals(
            listOf("xxxx(4)xxxx01.jpg", "xxxx(4)xxxx02.jpg"),
            collections.first { it.first().name.startsWith("xxxx(4)") }.map { it.name }
        )
        assertEquals(
            listOf("xxxx(5)xxxx01.jpg", "xxxx(5)xxxx02.jpg"),
            collections.first { it.first().name.startsWith("xxxx(5)") }.map { it.name }
        )
    }

    @Test
    fun gluedUnpaddedNumbersFormACollection() {
        // glued order digits without padding and past 09 must group too: 10 and 11
        // are two-digit numbers without a leading zero
        val result = blocksOf(
            medium("xxxx(5)xxxx10.jpg"),
            medium("xxxx(5)xxxx2.jpg"),
            medium("xxxx(5)xxxx1.jpg"),
        )

        assertEquals(
            listOf("xxxx(5)xxxx1.jpg", "xxxx(5)xxxx2.jpg", "xxxx(5)xxxx10.jpg"),
            result.single { it.size == 3 }.map { it.name }
        )
    }

    @Test
    fun unpaddedBareDigitsFormACollection() {
        val result = blocksOf(
            medium("photo1.jpg"),
            medium("photo2.jpg"),
        )

        assertEquals(
            listOf("photo1.jpg", "photo2.jpg"),
            result.single { it.size == 2 }.map { it.name }
        )
    }

    @Test
    fun numberOnlyNamesStaySingletons() {
        val result = blocksOf(
            medium("123.jpg"),
            medium("456.jpg"),
        )

        assertEquals(2, result.size)
    }

    @Test
    fun sequenceKeepsEveryMedium() {
        val media = listOf(
            medium("trip 01.jpg"), medium("trip 02.jpg"),
            medium("IMG_20241025_123301.jpg"), medium("solo.png"),
            medium("album（1）.jpg"), medium("album（2）.jpg"),
        )

        val sequence = ShuffleCollectionHelper.buildShuffledSequence(media, Random(42))

        assertEquals(media.size, sequence.size)
        assertEquals(media.map { it.path }.toSet(), sequence.map { it.path }.toSet())
    }
}
