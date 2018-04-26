package edu.unh.cs980.ranklib

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import java.io.File
import java.util.*
import edu.unh.cs980.*
import edu.unh.cs980.PID
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.pmap
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Class: ParagraphContainer
 * Description: Represents a scored paragraph (from TopDocs).
 * @param pid: Paragraph Id (obtained from Lucene index)
 * @param qid: Query ID (index of query that yielded the TopDocs)
 * @param isRelevant: whether or not this is a relevant paragraph (obtained from qrels)
 * @param features: Scores (from scoring functions) add added to this array for use in reweighting and rescoring
 * @param docId: Document id that this paragraph belongs to
 * @param score: Used when rescoring and reweighting the features
 */
data class ParagraphContainer(  val pid: String, val qid: Int,
                                val isRelevant: Boolean, val features: ArrayList<Feature>,
                                val docId: Int, var score:Double = 0.0) {

    // Adjust the paragraph's score so that it is equal to the weighted sum of its features.
    fun rescoreParagraph() {
        score = features.sumByDouble(Feature::getAdjustedScore)
    }

    // Convenience override: prints RankLib compatible lines
    override fun toString(): String =
            "${if (isRelevant) 1 else 0} qid:$qid " +
                    (1..features.size).zip(features)
                    .joinToString(separator = " ") { (id,feat) -> "$id:$feat" }

}


/**
 * Class: QueryContainer
 * Description: One is created for each of the query strings in the query .cbor file.
 *              Stores corresponding query string and TopDocs (obtained from BM25)
 */
data class QueryContainer(val query: String, val tops: TopDocs, val paragraphs: List<ParagraphContainer>) {
}


/**
 * Desc: Represents a paragraph that has been scored by a feature.
 * @weight: The amount to adjust the feature's score (used when re-ranking)
 */
data class Feature(val score: Double, val weight: Double) {
    fun getAdjustedScore(): Double = sanitizeDouble(score * weight)
    override fun toString(): String = getAdjustedScore().toString()

}

// Convenience function (turns NaN and infinite values into 0.0)
private fun sanitizeDouble(d: Double): Double { return if (d.isInfinite() || d.isNaN()) 0.0 else d }

/**
 * Enum: NormType
 * Description: Determines if the the values of an added feature should be normalized
 */
enum class NormType {
    NONE,           // No normalization should be used
    SUM,            // Value is divided by total sum of all values in query
    ZSCORE,         // Zscore is calculated for all values in query
    LINEAR          // Value is normalized to: (value - min) / (max - min)
}

/**
 * Class: KotlinRanklibFormatter
 * Description: Used to apply scoring functions to queries (from .cbor file) and print results as features.
 *              The results file is compatible with RankLib.
 * @param queries: List of query string/Topdocs (obtained by QueryRetriever)
 * @param qrelLoc: Location of the .qrels file (if none is given, then paragraphs won't be marked as relevant)
 * @param indexSearcher: An IndexSearcher for the Lucene index directory we will be querying.
 */
class KotlinRanklibFormatter(queryLocation: String,
                             qrelLoc: String, val indexSearcher: IndexSearcher) {

    /**
     * @param indexLoc: A string pointing to location of index (used to create IndexSearcher if none is given)
     */
    constructor(queryLocation: String, qrelLoc: String, indexLoc: String) :
            this(queryLocation, qrelLoc, getIndexSearcher(indexLoc))


    val queryRetriever = QueryRetriever(indexSearcher)
    val queries = queryRetriever.getSectionQueries(queryLocation)

    // If a qrel filepath was given, reads file and creates a set of query/paragraph pairs for relevancies
    private val relevancies =
            if (qrelLoc == "") null
            else
                File(qrelLoc)
                    .bufferedReader()
                    .readLines()
                    .map { it.split(" ").let { it[0] to it[2] } }
                    .toSet()

    // Maps queries into query containers (stores paragraph and feature information)
    private val queryContainers =
        queries.mapIndexed {index,  (query, tops) ->
            val containers = tops.scoreDocs.map { sc ->
                val pid = indexSearcher.doc(sc.doc).get(PID)

                ParagraphContainer(
                        pid = pid,
                        qid = index + 1,
                        isRelevant = relevancies?.run { contains(Pair(query, pid)) } ?: false,
                        docId = sc.doc,
                        features = arrayListOf())
            }
            QueryContainer(query = query, tops = tops, paragraphs = containers)
        }.toList()


    /**
     * Function: normSum
     * Description: Normalizes list of doubles by each value equal to itself divided by the total
     * @see NormType
     */
    private fun normSum(values: List<Double>): List<Double> =
            values.sum()
                .let { total -> values.map { value ->  value / total } }


    /**
     * Function: normZscore
     * Description: Calculates zscore for doubles in list
     * @see NormType
     */
    private fun normZscore(values: List<Double>): List<Double> {
        val mean = values.average()
        val std = Math.sqrt(values.sumByDouble { Math.pow(it - mean, 2.0) })
        return values.map { ((it - mean) / std) }
    }

    /**
     * Function: normLinear
     * Description: Linearizes each value: (value - min) / (max - min)
     * @see NormType
     */
    private fun normLinear(values: List<Double>): List<Double> {
        val minValue = values.min()!!
        val maxValue = values.max()!!
        return values.map { value -> (value - minValue) / (maxValue - minValue) }
    }


    /**
     * Function: normalizeResults
     * Description: Normalizes a list of doubles according to the NormType
     * @see NormType
     */
    private fun normalizeResults(values: List<Double>, normType: NormType): List<Double> {
        return when (normType) {
            NormType.NONE -> values
            NormType.SUM -> normSum(values)
            NormType.ZSCORE -> normZscore(values)
            NormType.LINEAR -> normLinear(values)
        }
    }

    /**
     * Function: addFeature
     * Description: Accepts a functions that take a string (query string) and TopDocs (from BM25 query)
     *              The function must return a list of Doubles in the same order as the documents they score
     *              The values are stored as a feature for later reranking or for creating a RankLib file.
     *
     * Note: The function, f, is mapped onto all of the queries in parallel. Make sure it is thread-safe.
     *
     * @param f: Function (or method reference) that scores each document in TopDocs and returns it as a list of doubles
     * @param normType: NormType determines the type of normalization (if any) to apply to the new document scores.
     * @param weight: The final list of doubles is multiplies by this weight
     */
    fun addFeature(f: (String, TopDocs, IndexSearcher) -> List<Double>, weight:Double = 1.0,
                   normType: NormType = NormType.NONE) {

        val bar = ProgressBar("Feature Progress", queryContainers.size.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()

        queryContainers
            .pmap { (query, tops, paragraphs) ->
                    // Using scoring function, score each of the paragraphs in our query result
                    val featureResult: List<Double> =
                            f(query, tops, indexSearcher).run { normalizeResults(this, normType) }

                    lock.withLock { bar.step() }
                    featureResult.zip(paragraphs) } // associate the scores with their corresponding paragraphs
            .forEach { results ->
                results.forEach { (score, paragraph) ->
                                   paragraph.features += Feature(score, weight) }}
        bar.stop()
    }


    private fun bm25(query: String, tops:TopDocs, indexSearcher: IndexSearcher): List<Double> {
        return tops.scoreDocs.map { it.score.toDouble() }
    }

    /**
     * Function: addBM25
     * Description: Adds results of the BM25 query as a feature. Since the scores are already contained in the TopDocs,
     *              this simply extracts them as a list of doubles.
     * @see addFeature
     */
    fun addBM25(weight: Double = 1.0, normType: NormType = NormType.NONE) =
            addFeature(this::bm25, weight = weight, normType = normType)

    /**
     * Function: rerankQueries
     * Description: Sums current weighted features together and reranks documents according to their new scores.
     * @see addFeature
     */
    fun rerankQueries() =
        queryContainers.forEach { queryContainer ->
            queryContainer.paragraphs
                .onEach(ParagraphContainer::rescoreParagraph)
                .sortedByDescending(ParagraphContainer::score)
                .forEachIndexed { index, paragraph ->
                    queryContainer.tops.scoreDocs[index].doc = paragraph.docId
                    queryContainer.tops.scoreDocs[index].score = paragraph.score.toFloat()
                }
        }


    /**
     * Function: writeToRankLibFile
     * Desciption: Writes features to a RankLib-compatible file.
     * @param outName: Name of the file to write the results to.
     */
    fun writeToRankLibFile(outName: String) {
        queryContainers
                .flatMap { queryContainer -> queryContainer.paragraphs  }
                .joinToString(separator = "\n", transform = ParagraphContainer::toString)
                .let { File(outName).writeText(it) }
    }

    /**
     * Function: writeQueriesToFile
     * Desciption: Uses query formatter to write current queries to trec-car compatible file
     * @param outName: Name of the file to write the results to.
     */
    fun writeQueriesToFile(outName: String) {
        queryRetriever.writeQueriesToFile(queries, outName)
    }
}
