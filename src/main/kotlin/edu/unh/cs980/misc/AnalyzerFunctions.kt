package edu.unh.cs980.misc

import edu.unh.cs980.CONTENT
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.TermQuery
import java.io.File
import java.io.StringReader
import kotlin.coroutines.experimental.buildSequence

private fun buildStopWords(): CharArraySet {
    val stops = CharArraySet.copy(EnglishAnalyzer.getDefaultStopSet())
     stops.addAll( File("aggressive_stops.txt").readLines() )
    return stops
}

/**
 * Static Class: AnalyzerFunctions
 * Desc: Contains a collection of tokenizing / query building functions used by other scripts.
 */
object AnalyzerFunctions {
    private val standardAnalyzer = StandardAnalyzer()
    private val englishAnalyzer = EnglishAnalyzer()
    private val englishStopped = EnglishAnalyzer(buildStopWords())

    enum class AnalyzerType { ANALYZER_STANDARD, ANALYZER_ENGLISH, ANALYZER_ENGLISH_STOPPED }

    /**
     * Class: createTokenSequence
     * Description: Given a query string, tokenizes it and returns a list of String tokens
     * @param analyzerType: Type of analyzer (english or standard)
     * @param useFiltering: If true, filter out numbers, enwiki: and other noise from query
     * @return Sequence<String>
     * @see AnalyzerType
     */
    fun createTokenList(query: String,
                        analyzerType: AnalyzerType = AnalyzerType.ANALYZER_STANDARD,
                        useFiltering: Boolean = false): List<String> {
        val analyzer = when (analyzerType) {
            AnalyzerType.ANALYZER_STANDARD -> standardAnalyzer
            AnalyzerType.ANALYZER_ENGLISH  -> englishAnalyzer
            AnalyzerType.ANALYZER_ENGLISH_STOPPED  -> englishStopped
        }

        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        val finalQuery =
                if (useFiltering) query.replace(replaceNumbers, "").replace("/", " ")
                else query


        val tokens = ArrayList<String>()
        val tokenStream = analyzer.tokenStream("text", StringReader(finalQuery)).apply { reset() }
        while (tokenStream.incrementToken()) {
            tokens.add(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
        }
        tokenStream.end()
        tokenStream.close()

        return tokens
    }


    /**
     * Class: createQuery
     * Description: Given a query string, will create a boolean query by breaking it into tokens.
     * @return BooleanQuery: (tokens joined with OR clauses)
     */
    fun createQuery(query: String,
                    field: String = CONTENT,
                    useFiltering: Boolean = false,
                    analyzerType: AnalyzerType = AnalyzerType.ANALYZER_STANDARD): BooleanQuery {

        return createTokenList(query, analyzerType, useFiltering)
            .map { token -> TermQuery(Term(field, token)) }
            .fold(BooleanQuery.Builder(),
                    { builder, termQuery -> builder.add(termQuery, BooleanClause.Occur.SHOULD) })
            .build()
    }

    /**
     * Class: createQueryList
     * Description: As createQuery, except that it returns a list of boolean queries (one for each token)
     */
    fun createQueryList(query: String,
                    field: String = CONTENT,
                    useFiltering: Boolean = false,
                    analyzerType: AnalyzerType = AnalyzerType.ANALYZER_STANDARD): List<BooleanQuery> {

        return createTokenList(query, analyzerType, useFiltering)
            .map { token -> TermQuery(Term(field, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()
    }
}