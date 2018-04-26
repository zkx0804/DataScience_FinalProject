@file:JvmName("KotlinAbstractAnalyzer")
package edu.unh.cs980.language

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.misc.AnalyzerFunctions
import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.index.MultiFields
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.FuzzyQuery
import org.apache.lucene.search.IndexSearcher
import java.io.File
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.buildSequence


/**
 * Class: RelevantEntity
 * Desc: Represents a top20 entity retrieved from the abstract database.
 * @param name: Name of the entity (should be Wikipedia page)
 * @param content: Abstract paragraph associated with page.
 * @param statContainer: Language model of abstract.
 * @param score: Score according to BM25 from query to abstract index.
 * @param rank: Linearized score (according to distance from the top result)
 * @param queryLikelihood: Likelihood model given the query (bigram + unigram + windowed)
 */
data class RelevantEntity(val name: String, val content: String, val statContainer: LanguageStatContainer,
                          val score: Double, val rank: Double, val queryLikelihood: LikelihoodContainer)

class KotlinAbstractAnalyzer(val indexSearcher: IndexSearcher) {
    constructor(indexLoc: String) : this(getIndexSearcher(indexLoc))

    val gramAnalyzer = KotlinGramAnalyzer(indexSearcher)
    val sim = NormalizedLevenshtein()

    // Searching the database is slow, so the documents and language models are stored in case they need to be used
    // again in a different query
    private val memoizedAbstractDocs = ConcurrentHashMap<String, Document?>()
    private val memoizedAbstractStats = ConcurrentHashMap<String, LanguageStatContainer?>()

    /**
     * Func: retrieveEntityStatContainer
     * Desc: Given entity, searches abstract database, retrieves paragraph, and returns language model.
     */
    fun retrieveEntityStatContainer(entity: String): LanguageStatContainer? =
            memoizedAbstractStats.computeIfAbsent(entity, {key ->
                val entityDoc = retrieveEntityDoc(key)
                entityDoc?.let { doc -> gramAnalyzer.getLanguageStatContainer(doc.get("text")) }
            })

    /**
     * Func: retrieveEntityDoc
     * Desc: Given an entity, returns associated document in abstract database if it exists
     */
    fun retrieveEntityDoc(entity: String): Document? {
        //            memoizedAbstractDocs.computeIfAbsent(entity, {key ->
        val nameQuery = buildEntityNameQuery(entity)
        val searchResult = indexSearcher.search(nameQuery, 1)
        val result =    if (searchResult.scoreDocs.isEmpty()) null
                        else indexSearcher.doc(searchResult.scoreDocs[0].doc)
        return result
    }


    /**
     * Func: buildEntityNameQuery
     * Desc: Given an entity, creates a fuzzy query for the "name" field in the abstract database.
     *       Used to retrieved the associated document from the database.
     */
    fun buildEntityNameQuery(entity: String): BooleanQuery =
            BooleanQuery.Builder()
                .apply { add(FuzzyQuery(Term("name", entity), 2, 4), BooleanClause.Occur.SHOULD) }
                .build()


    /**
     * Func: getRelevantEntities
     * Desc: Given a query, searches the abstract database (using BM25) and considers the top 20 results as
     *       "entities relevant to the query". Language models are generated for these entities according to their
     *       abstracts and the results are returned as a list of relevant entities.
     * @see RelevantEntity
     */
    fun getRelevantEntities(query: String, alpha: Double = 1.0): List<RelevantEntity> {
        val cleanedQuery = AnalyzerFunctions
            .createTokenList(query, useFiltering = true)
            .joinToString(" ")

        val booleanQuery = AnalyzerFunctions.createQuery(query, useFiltering = true)
        val tops = gramAnalyzer.indexSearcher.search(booleanQuery, 20)
        val numHits = tops.scoreDocs.size.toDouble()
        val queryModel = gramAnalyzer.getCorpusStatContainer(cleanedQuery)

        return tops.scoreDocs
            .map { scoreDoc -> gramAnalyzer.indexSearcher.doc(scoreDoc.doc) to scoreDoc.score.toDouble()}
            .sortedByDescending { pair -> pair.second }
            .mapIndexed { index, (doc, score) ->
                // Retrieved document is from abstract lucene index. Retrieve name and abstract text.
                val name = doc.get("name")
                val content = doc.get("text")

                // Generate statistical model given language model of query and of abstract text.
                val stat = gramAnalyzer.getLanguageStatContainer(content)
                val likelihood = stat.getLikelihoodGivenQuery(queryModel, alpha = alpha)
                RelevantEntity(name = name,
                        content = content,
                        statContainer = stat,
                        score = score,
                        rank = (numHits - index) / numHits,
                        queryLikelihood = likelihood)}
    }

    /**
     * Func: getMostSimilarRelevantEntity
     * Desc: Given a candidate entity, tries to find the most similar entity (from top20 search results) whose page
     *       name most closely matches the entity. If they are not similar enough, return null. Otherwise, return
     *       the associated RelevantEntity and the similarity of the candidate entity to the page name.
     */
    fun getMostSimilarRelevantEntity(entity: String, rels: List<RelevantEntity>): Pair<Double, RelevantEntity>? =
        rels.map { relevantEntity -> 1.0 - sim.distance(entity, relevantEntity.name) to relevantEntity  }
            .maxBy { (similarity, _) -> similarity }
            ?.let { (similarity, relevantEntity) ->
                if (similarity < 0.9) null
                else Pair(similarity, relevantEntity)
            }


    // Retrieve term frequency for a particular term in the abstract text
    fun retrieveTermStats(term: String): Long =
        indexSearcher.indexReader.totalTermFreq(Term("text", term))

    /**
     * Func: getEntityTokens
     * Desc: Searches for matching entity in abstract database and returns a tokenized instance of the
     *       words in the entity's abstract text.
     */
    fun getEntityTokens(entity: String): List<String>? {
        val cleanName = entity.toLowerCase().replace(" ", "_")
        val query = BooleanQuery
            .Builder()
            .apply { add(FuzzyQuery(Term("name", cleanName)), BooleanClause.Occur.SHOULD) }
            .build()

        val topDocs = indexSearcher.search(query, 1)
        if (topDocs.scoreDocs.isEmpty()) {
            return null
        }

        val entityDoc = indexSearcher.doc(topDocs.scoreDocs[0].doc)
        val content = entityDoc.get("text")
        return AnalyzerFunctions.createTokenList(content)
    }

    /**
     * Func: getTermStats
     * Desc: Given a list of words, return term frequency for words given abstract index.
     */
    fun getTermStats(terms: List<String>): List<Pair<String, Double>> {
        val totalTerms = indexSearcher.indexReader.getSumTotalTermFreq("text").toDouble()
        return terms.map { term ->
            val termFreq = indexSearcher.indexReader.totalTermFreq(Term("text", term)) / totalTerms
            term to termFreq
        }.toList()
    }



    fun runTest() {
    }
}


