@file:JvmName("KotGramAnalyzer")
package edu.unh.cs980.language

import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.identity
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.nlp.NL_Document
import edu.unh.cs980.nlp.NL_Processor
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import java.io.StringReader
import java.lang.Math.log
import kotlin.coroutines.experimental.buildSequence

/**
 * Enum: GramStatType
 * Desc: Used to indicate what type of gram that this language statistic represents.
 */
enum class GramStatType  { TYPE_UNIGRAM, TYPE_BIGRAM, TYPE_BIGRAM_WINDOW }


/**
 * Class: LanguageStat
 * Desc: Abstraction of a -gram statistic for a document or collection.
 * @param docTermCounts: Number of times each -gram appears.
 * @param docTermFreqs: Relative frequency of each -gram.
 * @param type: The type of -gram (unigram, bigram, or windowed bigram)
 */
data class LanguageStat(val docTermCounts: Map<String, Int>,
                        val docTermFreqs: Map<String, Double>,
                        val type: GramStatType)


/**
 * Class: LikelihoodContainer
 * Desc: Contains (log) likelihood values for each type of gram
 * @see LanguageStatContainer
 */
data class LikelihoodContainer(val unigramLikelihood: Double,
                               val bigramLikelihood: Double,
                               val bigramWindowLikelihood: Double)

/**
 * Class: CorpusStat
 * Desc: Represents a -gram model for a collection
 */
data class CorpusStat(val corpusFrequency: Map<String, Double>, val corpusDoc: LanguageStat) {
    val type: GramStatType
        get() = corpusDoc.type
}


/**
 * Class: CorpusStatContainer
 * Desc: Convenience container for each type of -gram model for collection.
 */
data class CorpusStatContainer(
        val unigramStat: CorpusStat,
        val bigramStat: CorpusStat,
        val bigramWindowStat: CorpusStat
)


/**
 * Class: LanguageStatContainer
 * Desc: Represents language stats for a query or document.
 *       Contains a LanguageStat for each type of -gram.
 * @see LanguageStat
 * @see GramStatType
 */
data class LanguageStatContainer(
        val unigramStat: LanguageStat,
        val bigramStat: LanguageStat,
        val bigramWindowStat: LanguageStat) {


    /**
     * Func: getLikelihood
     * Desc: Gets Dirichlet smoothed log likelihood given a query
     * @param queryStat: Represents -gram model of a query, and of associated corpus.
     * @param alpha: Used for smoothing
     */
    private fun getLikelihood(queryStat: CorpusStat, alpha: Double): Double {
        val stat = when(queryStat.type) {
            GramStatType.TYPE_UNIGRAM -> unigramStat
            GramStatType.TYPE_BIGRAM -> bigramStat
            GramStatType.TYPE_BIGRAM_WINDOW -> bigramWindowStat
        }

        val docLength = stat.docTermCounts.values.sum().toDouble()

        val likelihood =
                queryStat.corpusFrequency
                    .map { (term, freq) ->
//                        val pred = docSmooth * (stat.docTermFreqs[term] ?: 0.0) + corpusSmooth * freq
                        val smoothCounts = (stat.docTermCounts[term] ?: 0) + freq * alpha
//                        println("$term : $freq : ${queryStat.type}")
                        log(smoothCounts / (docLength + alpha)).defaultWhenNotFinite(0.0)
                    }
                    .sum()

        return likelihood
    }

    /**
     * Func: getLikelihoodGivenQuery
     * Desc: Calculates likelihood for each type of -gram statistic given a query.
     * @see getLikelihood
     */
    fun getLikelihoodGivenQuery(query: CorpusStatContainer, alpha: Double = 1.0): LikelihoodContainer =
            LikelihoodContainer(
                    unigramLikelihood = getLikelihood(query.unigramStat, alpha),
                    bigramLikelihood = getLikelihood(query.bigramStat, alpha),
                    bigramWindowLikelihood = getLikelihood(query.bigramWindowStat, alpha)
            )
}


/**
 * Class: KotlinGramAnalyzer
 * Desc: Given a corpus indexed with -grams, calculates language model statistics.
 */
class KotlinGramAnalyzer(val indexSearcher: IndexSearcher) {
    constructor(indexLoc: String) : this(getIndexSearcher(indexLoc))
    val analyzer = EnglishAnalyzer()

    // Returns total frequency of a -gram in corpus
    private fun getCorpusGram(gram: String, gramType: String): Long =
            indexSearcher.indexReader.totalTermFreq(Term(gramType, gram))

    /**
     * Func: getLanguageStatContainer
     * Desc: Given text (such as a paragraph body), calculates -gram statistics and returns
     *       stats wrapped in a LanguageStatContainer.
     */
    fun getLanguageStatContainer(text: String): LanguageStatContainer =
        LanguageStatContainer(
                unigramStat = getStats(text, GramStatType.TYPE_UNIGRAM),
                bigramStat = getStats(text, GramStatType.TYPE_BIGRAM),
                bigramWindowStat = getStats(text, GramStatType.TYPE_BIGRAM_WINDOW)
        )

    /**
     * Func: extractNounsVerbs
     * Desc: Using Kevin's NLP, extract nouns and verbs
     */
//    private fun extractNounsVerbs(text: String): Pair<String, String> {
//        val nlDoc = NL_Processor.convertToNL_Document(text)
//        val nouns = nlDoc.allNounsInPara.joinToString(" ")
//        val verbs = nlDoc.allVerbsInPara.joinToString(" ")
//        return nouns to verbs
//    }

//    /**
//     * Func: getNatCorpusStatContainers
//     * Desc: Using Kevin's NLP, extract nouns and verbs and build corpus language models from them.
//     */
//    fun getNatCorpusStatContainers(text: String): Pair<CorpusStatContainer, CorpusStatContainer> {
//        val (nouns, verbs) = extractNounsVerbs(text)
//        return getCorpusStatContainer(nouns) to getCorpusStatContainer(verbs)
//    }
//
//    /**
//     * Func: getNatLanguageStatContainers
//     * Desc: Using Kevin's NLP, extract nouns and verbs and build document language models from them.
//     */
//    fun getNatLanguageStatContainers(text: String): Pair<LanguageStatContainer, LanguageStatContainer> {
//        val (nouns, verbs) = extractNounsVerbs(text)
//        return getLanguageStatContainer(nouns) to getLanguageStatContainer(verbs)
//    }

    /**
     * Func: getCorpusStatContainer
     * Desc: Given a query, returns -gram model statistics for query, and also returns
     *      the collection statistics for each -gram.
     */
    fun getCorpusStatContainer(text: String): CorpusStatContainer =
            CorpusStatContainer(
                    unigramStat = getCorpusStat(text, GramStatType.TYPE_UNIGRAM),
                    bigramStat = getCorpusStat(text, GramStatType.TYPE_BIGRAM),
                    bigramWindowStat = getCorpusStat(text, GramStatType.TYPE_BIGRAM_WINDOW)
            )


    /**
     * Func: getCorpusStat
     * Desc: Returns collection statistic for a given -gram type.
     */
    private fun getCorpusStat(text: String, type: GramStatType): CorpusStat {
        val languageStats = getStats(text, type)

        val field = when(type) {
            GramStatType.TYPE_UNIGRAM -> "unigram"
            GramStatType.TYPE_BIGRAM -> "bigrams"
            GramStatType.TYPE_BIGRAM_WINDOW -> "bigram_windows"
        }

        val totalCorpusFreq = indexSearcher.indexReader
            .getSumTotalTermFreq(field)
            .toDouble()

        val corpusFreqs = languageStats.docTermCounts.keys
            .map { key -> Pair(key, getCorpusGram(key, field) / totalCorpusFreq) }
            .toMap()

        return CorpusStat(corpusFrequency = corpusFreqs, corpusDoc = languageStats)
    }


    /**
     * Func: createLanguageStats
     * Desc: Creates a language model for given -gram type (does not include collection statistics).
     */
    private fun createLanguageStats(counts: Map<String, Int>, type: GramStatType): LanguageStat {
        val totalCount = counts.values
            .sum()
            .toDouble()

        val freqs = counts
            .mapValues { (gram, count) -> count / totalCount }
            .toMap()

        return LanguageStat(counts, freqs, type)
    }


    /**
     * Func: countBigrams
     * Desc: Function used to count number of (stemmed) bigrams in text.
     */
    private fun countBigrams(text: String): Map<String, Int> {
        val terms = AnalyzerFunctions.createTokenList(text, analyzerType = ANALYZER_ENGLISH).toList()
        val docBigramCounts = terms.windowed(2, 1)
            .map { window -> window.joinToString(separator = "") }
            .groupingBy(::identity)
            .eachCount()

        return docBigramCounts
    }


    /**
     * Func: countWindowedBigrams
     * Desc: Function used to count number of (stemmed) windowed bigrams in text.
     */
    private fun countWindowedBigrams(text: String): Map<String, Int> {
        val terms = AnalyzerFunctions.createTokenList(text, analyzerType = ANALYZER_ENGLISH)
        val docBigramWindowCounts = terms
            .windowed(8, 1, true)
            .flatMap { window ->
                            val firstTerm = window[0]
                            window
                                .slice(1  until window.size)
                                .flatMap { secondTerm -> listOf(firstTerm + secondTerm, secondTerm + firstTerm) } }
            .groupingBy(::identity)
            .eachCount()
            .toMap()
        return docBigramWindowCounts
    }


    /**
     * Func: countUnigrams
     * Desc: Functions used to count number of (stemmed) unigrams in text.
     */
    private fun countUnigrams(text: String): Map<String, Int> {
        val terms = AnalyzerFunctions.createTokenList(text, analyzerType = ANALYZER_ENGLISH)

        val docTermCounts = terms
            .groupingBy(::identity)
            .eachCount()
            .toMap()

        return docTermCounts
    }

    /**
     * Func: getStats
     * Desc: Counts -grams of a given type and returns a language model using these stats.
     */
    private fun getStats(text: String, statType: GramStatType): LanguageStat {
        val counts = when (statType) {
            GramStatType.TYPE_UNIGRAM -> countUnigrams(text)
            GramStatType.TYPE_BIGRAM -> countBigrams(text)
            GramStatType.TYPE_BIGRAM_WINDOW -> countWindowedBigrams(text)
        }

        return createLanguageStats(counts, statType)
    }

    fun runTest() {
    }

    /**
     * Func: getQueryLikelihood
     * Desc: Wrapper function around LanguageStatsContainer to run query likelihood and return just that
     *       scores.
     */
    fun getQueryLikelihood(langStat: LanguageStatContainer, corpStat: CorpusStatContainer, alpha: Double)
            : Triple<Double, Double, Double> {
        val queryLikelihoodContainer = langStat.getLikelihoodGivenQuery(corpStat, alpha)
        val uniLike = queryLikelihoodContainer.unigramLikelihood
        val biLike = queryLikelihoodContainer.bigramLikelihood
        val windLike = queryLikelihoodContainer.bigramWindowLikelihood
        return Triple(uniLike, biLike, windLike)
    }
}

