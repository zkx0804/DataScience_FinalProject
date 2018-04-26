@file:JvmName("KotQueryRetriever")
package edu.unh.cs980

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import edu.unh.cs980.misc.AnalyzerFunctions
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import java.io.BufferedWriter
import java.io.File
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.buildSequence

/**
 * Class: QueryRetriever
 * Description: Used to make queries (using BM25) and to parse the results of queries.
 *              Takes an IndexSearcher (or path to an index) for the Lucene index it will query with.
 */
class QueryRetriever(val indexSearcher: IndexSearcher) {
    constructor(indexPath: String) : this(getIndexSearcher(indexPath))

    val analyzer = StandardAnalyzer()


    /**
     * Function: createQueryString
     * Description: Returns string to be used in querying.
     * @param sectionPath: If non-empty, collapses sections into a single query string
     */
    fun createQueryString(page: Data.Page, sectionPath: List<Data.Section>): String =
            page.pageName + sectionPath.joinToString { section -> " " + section.heading  }


//    /**
//     * Class: createTokenSequence
//     * Description: Given a query string, tokenizes it and returns a sequence of String tokens
//     * @return Sequence<String>
//     */
//    fun createTokenSequence(query: String): Sequence<String> {
//        val tokenStream = analyzer.tokenStream("text", StringReader(query)).apply { reset() }
//
//        return buildSequence<String> {
//            while (tokenStream.incrementToken()) {
//                yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
//            }
//            tokenStream.end()
//            tokenStream.close()
//        }
//    }
//
//    /**
//     * Class: createQuery
//     * Description: Given a query string, will create a boolean query by breaking it into tokens.
//     * @return BooleanQuery: (tokens joined with OR clauses)
//     */
//    fun createQuery(query: String): BooleanQuery =
//            createTokenSequence(query)
//                .map { token -> TermQuery(Term(CONTENT, token)) }
//                .fold(BooleanQuery.Builder(), { acc, termQuery ->
//                    acc.add(termQuery, BooleanClause.Occur.SHOULD) })
//                .build()

    /**
     * Function: getPageQueries
     * Description: Given a query location (.cbor file), queries Lucene index with page names.
     * @return List of pairs (query string and the Top 100 documents obtained by doing the query)
     */
    fun getPageQueries(queryLocation: String): List<Pair<String, TopDocs>> =
            DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
                .map { page ->
                    val queryId = page.pageId
                    val queryStr = createQueryString(page, emptyList())
//                    queryId to indexSearcher.search(createQuery(queryStr), 100) }
                    val booleanQuery = indexSearcher
                        .search(AnalyzerFunctions.createQuery(queryStr, useFiltering = true), 100)
                    queryId to booleanQuery }
                .toList()


    /**
     * Function: getSectionQueries
     * Description: Given a query location (.cbor file), queries Lucene index with page name and section names.
     * @return List of pairs (query string and the Top 100 documents obtained by doing the query)
     */
    fun getSectionQueries(queryLocation: String): List<Pair<String, TopDocs>> {
        val seen = ConcurrentHashMap<String, String>()
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()

        return DeserializeData.iterableAnnotations(File(queryLocation).inputStream())
            .flatMap { page ->
                page.flatSectionPaths()
                    .pmap { sectionPath ->
                        val queryId = Data.sectionPathId(page.pageId, sectionPath)
                        var queryStr = createQueryString(page, sectionPath)
                        queryStr = queryStr.replace(replaceNumbers, queryStr)      // remove numbers/enwiki:

//                        val result = queryId to indexSearcher.search(createQuery(queryStr), 100)
                        val query = indexSearcher
                            .search(AnalyzerFunctions.createQuery(queryStr, useFiltering = false), 100)
//                        val result = queryId to indexSearcher.search(createQuery(queryStr), 100)
                        val result = queryId to query
                        result.takeUnless {seen.put(queryId, "") != null}   // remove duplicates
                    }
            }.filterNotNull()
    }


    /**
     * Function: writeRankingsToFile
     * Description: Writes formatted query results to a file (for use with trec_eval)
     */
    private fun writeRankingsToFile(tops: TopDocs, queryId: String, writer: BufferedWriter, queryNumber: Int) {
        (0 until tops.scoreDocs.size).forEach { index ->
            val sd = tops.scoreDocs[index]
            val doc = indexSearcher.doc(sd.doc)
            val paragraphid = doc.get(PID)
            val score = sd.score
            val searchRank = index + 1

            writer.write("$queryId Q$queryNumber $paragraphid $searchRank $score Query\n")
        }
    }

    /**
     * Function: writeQueriesToFile
     * Description: For each pair of query name and top 100 documents, write the results to a file.
     * @see writeRankingsToFile
     */
    fun writeQueriesToFile(queries: List<Pair<String, TopDocs>>, out: String = "results.txt") {
        val writer = File(out).bufferedWriter()
        queries.forEachIndexed { index, (query, tops) -> writeRankingsToFile(tops, query, writer, index)}
        writer.flush()
        writer.close()
    }
}

