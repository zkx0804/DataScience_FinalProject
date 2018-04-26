@file:JvmName("KotGraph")
package edu.unh.cs980

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import java.util.*
import java.lang.Double.sum
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import java.util.concurrent.ThreadLocalRandom

/**
 * Class: Paragraph Mixture
 * Description: Represents a distribution over entities parameterized by a paragraph from the corpus.
 * @param docId: The Lucene document ID of the paragraph.
 * @param paragraphId: The paragraphId
 * @param score: The score of the Lucene document this mixture is linked to (later used for rescoring)
 * @param mixture: Distribution over entities
 */
data class ParagraphMixture(
        var docId: Int = 0,
        var paragraphId: String = "",
        var score: Double = 0.0,
        var mixture: HashMap<String, Double> = HashMap())


/**
 * Class: KotlinGraphAnalyzer
 * Description: Used to perform random walks over bipartite graph of entities and paragraphs and to retrieve stats.
 */
class KotlinGraphAnalyzer(var indexSearcher: IndexSearcher, val db: KotlinDatabase) {
    private val storedParagraphs = ConcurrentHashMap<String, List<String>>()
    private val storedEntities = ConcurrentHashMap<String, List<String>>()

    /**
     * Function: getParagraphMixture
     * Description: Performs a random walk parameterized by a paragraph and stores info in a paragraph mixture.
     * @param docInfo: Pair (docID, document score)
     */
    fun getParagraphMixture(docInfo: Pair<Int, Float>): ParagraphMixture {
        val doc = indexSearcher.doc(docInfo.first)
        val paragraphId = doc.get(PID)
        val pm = ParagraphMixture(
                docId = docInfo.first,
                paragraphId = paragraphId,
                score = docInfo.second.toDouble(),
                mixture = doWalkModel(paragraphId)
                )
        return pm
    }


    /**
     * Function: doWalkModel
     * Description: Do random walks originating from a particular paragraph
     * @param pid: The paragraph that the random walks originate from.
     * @return: Distribution over entities with respect to paragraph
     */
    fun doWalkModel(pid: String): HashMap<String, Double> {
        val counts = HashMap<String, Double>()
        val nWalks = 200
        val nSteps = 3
        val entitiesFromOrigin = db.parMap[pid]!!.split(" ")

        // Restart random walk multiple times from the origin
        (0 until nWalks).forEach { _ ->
            var score = 1.0
            var curPar = pid            // Start at origin at beginning of each walk
            var first = 0

            // Walk a number of steps for stopping
            (0 until nSteps).forEach { _ ->

                // Retrieve a random entity linked to paragraph (too many paragraphs to memoize)
                val entities = if (curPar == pid) entitiesFromOrigin else db.parMap[curPar]!!.split(" ")
                val entity = entities[ThreadLocalRandom.current().nextInt(entities.size)]

                // Retrieve a random paragraph linked to entity (memoize result)
                val paragraphs = storedParagraphs.computeIfAbsent(entity,
                        { key -> db.entityMap[key]!!.split(" ") })
                curPar = paragraphs[ThreadLocalRandom.current().nextInt(paragraphs.size)]

                // Yeah, this part is hacky... I need to find a better way.
                if (first != 0) {
                    first = 1
                } else {
                    // Penalize score based on outgoing links (also, score decays over the duration of the walk)
                    score *= 1/(ln(entities.size.toDouble()) + ln(paragraphs.size.toDouble()))
                }

                // Updates how often we have seen the entity edge we traversed (with diminishing returns)
                counts.merge(entity, score, ::sum)
            }
        }

        // Only consider the top 20 entities (because this is an incredibly long-tailed distribution)
        val topEntries = counts.entries
            .sortedByDescending{ it.value }
            .take(20)
            .map { it.key }
            .toHashSet()

        counts.removeAll { key, value -> key !in topEntries }

        // Normalize distribution of top 20 entities
        counts.values.sum().let { total ->
            counts.replaceAll({k,v -> v/total})
        }

        return counts
    }

    /**
     * Function: doWalkModelEntity
     * Description: Alternate version of WalkModel where we consider a distribution over entities by starting at a
     *              particular entity.
     */
    fun doWalkModelEntity(entity: String): HashMap<String, Double> {
        val counts = HashMap<String, Double>()
        val nWalks = 400
        val nSteps = 4

        (0 until nWalks).forEach { _ ->
            var score = 1.0
            var curEntity = entity
            var first = 0

            (0 until nSteps).forEach { _ ->
                // Retrieve a random paragrath linked to entity (memoize result)
                val paragraphs = db.entityMap[curEntity]!!.split(" ")
                val paragraph = paragraphs[ThreadLocalRandom.current().nextInt(paragraphs.size)]

                // Retrieve a random entity linked to paragraph (memoize result)
                val entities = db.parMap[paragraph]!!.split(" ")
                curEntity = entities[ThreadLocalRandom.current().nextInt(entities.size)]

                if (first != 0) {
                    first = 1
                } else {
                    score *= 1/(ln(entities.size.toDouble()) + ln(paragraphs.size.toDouble()))
                }

                counts.merge(curEntity, score, ::sum)


            }
        }

        val topEntries = counts.entries.sortedByDescending{ it.value }
            .take(20)
            .map { it.key }
            .toHashSet()

        counts.removeAll { key, value -> key !in topEntries }
        counts.values.sum().let { total ->
            counts.replaceAll({k,v -> v/total})
        }

        return counts
    }


    /**
     * Function: getMixtures
     * Description: Retrieves distributions over entities for each of the paragraphs in the TopDocs.
     */
    fun getMixtures(tops: TopDocs): List<ParagraphMixture> =
            tops.scoreDocs
                    .map { it.doc to it.score }
                    .map({getParagraphMixture(it)})
                    .toList()

}
