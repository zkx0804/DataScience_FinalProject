@file:JvmName("KotHyperlinkIndexer")
package edu.unh.cs980.context

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import edu.unh.cs980.forEachParallel
import edu.unh.cs980.forEachParallelRestricted
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import java.io.*
import java.util.concurrent.atomic.AtomicInteger


/**
 * Class: HyperlinkIndexer
 * Desc: Given AllButBenchmark corpus, extracts anchor text and links and builds a mention likelihood model.
 */
class HyperlinkIndexer(filename: String, readOnly: Boolean = true) {
    // Database used to store anchor text / candidate entities
    val db = DBMaker
        .fileDB(filename)
        .fileMmapEnable()
        .closeOnJvmShutdown()
        .concurrencyScale(60)
        .apply { if (readOnly) readOnly() }
        .make()

    // Key: (anchor text, linked entity)
    // value: Number of times we have seen anchor text refer to linked entity
    val map = db.treeMap("links")
        .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
        .valueSerializer(Serializer.INTEGER)
        .createOrOpen()

    // Just the entity mentions themselves (used for building dictionary chunker)
    val mentionSet = db.hashSet("mentions")
        .serializer(Serializer.STRING)
        .createOrOpen()


    /**
     * Desc: Increment the numbers of times we've seen this anchor text refer to the linked entity.
     */
    fun addLink(anchorText: String, entity: String) {
        map.compute(arrayOf(anchorText, entity), { key, value -> value?.inc() ?: 1 })
        mentionSet += anchorText
    }

    // Links all of the mentions in the list
    fun addLinks(links: List<Pair<String, String>> ) =
            links.forEach { (anchorText, entity) -> addLink(anchorText, entity) }

    fun getLink(anchorText: String, entity: String): Int =
            map.get(arrayOf(anchorText, entity)) ?: 0

    /**
     * Desc: Given an entity mention, returns the most popular linked entity and its popularity score.
     */
    fun getPopular(anchorText: String): Pair<String, Double> =
            // Prefix submap lets us return all keys that contain the entity mention
            map.prefixSubMap(arrayOf(clean(anchorText)))
                .let { entitiesLinkedByAnchorText ->
                    val total = entitiesLinkedByAnchorText.entries.sumBy { it.value }

                    entitiesLinkedByAnchorText
                        .entries
                        .sortedByDescending { (k,v) -> v }
                        .first()
                        .let { mostPopularEntity ->
                            (mostPopularEntity.key[1] as String) to mostPopularEntity.value / total.toDouble()
                        }
                }


    /**
     * Desc: Given entity mention and linked entity, return probability of linked entity given entity mention
     */
    fun getMentionLikelihood(anchorText: String, linkedEntity: String): Double {
        val cleanedAnchor = clean(anchorText)
        val cleanedEntity = clean(linkedEntity)

        if (!hasEntityMention(cleanedAnchor)) {
            return 0.0
        }

        val prefixMap = map.prefixSubMap(arrayOf(cleanedAnchor))
        val totalMentions = prefixMap.values.sum()
        val mentionsReferringToEntity = map[arrayOf(cleanedAnchor, cleanedEntity)] ?: 0

        return mentionsReferringToEntity / totalMentions.toDouble()
    }

    fun hasEntityMention(mention: String) = mention in mentionSet

    fun clean(text: String): String = text.toLowerCase().replace(" ", "_")

    /**
     * Desc: Retrieves pairs of anchor text and linked entities and updates database in parallel.
     */
    fun indexHyperlinks(filename: String) {
        val f = File(filename).inputStream().buffered(16 * 1024)
        val counter = AtomicInteger()

        DeserializeData.iterableAnnotations(f)
            .forEachParallelRestricted(20) { page ->

                // This is just to keep track of how many pages we've parsed
                counter.incrementAndGet().let {
                    if (it % 100000 == 0) {
                        println(it)
                    }
                }

                // Extract all of the anchors/entities and add them to database
                page.flatSectionPathsParagraphs()
                    .flatMap { psection ->
                        psection.paragraph.bodies
                            .filterIsInstance<Data.ParaLink>()
                            .map { paraLink -> clean(paraLink.anchorText) to clean(paraLink.page) } }
                    .apply(this::addLinks)
            }
    }
}

