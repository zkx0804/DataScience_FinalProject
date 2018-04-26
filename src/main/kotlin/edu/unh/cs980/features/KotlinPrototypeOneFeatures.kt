package edu.unh.cs980.features

import edu.unh.cs980.KotlinGraphAnalyzer
import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.misc.AnalyzerFunctions
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.similarities.Similarity
import java.lang.Double.sum

/**
 * Function: addStringDistanceFunction
 * Description: In this method, I try to the distance (or similarity) between the terms (after splitting)
 *              and the entities in each document.
 * @params dist: StingDistance interface (from debatty stringsimilarity library)
 */
fun featAddStringDistanceFunction(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                  dist: StringDistance): List<Double> {
//        val tokens = retrieveSequence(query)
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)

    // Map this over the score docs, taking the average similarity between query tokens and entities
    return tops.scoreDocs
        .map { scoreDoc ->
//            val doc = formatter.indexSearcher.doc(scoreDoc.doc)
            val doc = indexSearcher.doc(scoreDoc.doc)
            val entities = doc.getValues("spotlight").map { it.replace("_", " ") }
            if (entities.isEmpty()) 0.0 else
            // This is the actual part where I average the results using the distance function
                tokens.flatMap { q -> entities.map { e -> 1 - dist.distance(q, e)  } }
                    .average()
                    .defaultWhenNotFinite(0.0)
        }
}

/**
 * Function: addAverageQueryScore
 * Description: In this method, I tokenize the query and treat each token as an individual query.
 *              I then get the BM25 score of each query to each document and average the results.
 */
fun featAddAverageQueryScore(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
//        val termQueries = retrieveSequence(query)
    val termQueries = AnalyzerFunctions.createQueryList(query, useFiltering = true)
//        .map { token -> TermQuery(Term(CONTENT, token))}
//        .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
//        .toList()

    return tops.scoreDocs.map { scoreDoc ->
        termQueries.map { booleanQuery ->
            indexSearcher.explain(booleanQuery, scoreDoc.doc).value.toDouble() }
            .average()
    }
}


/**
 * Function: addEntityQueries
 * Description: This method calculates the score using only entities
 */
fun featAddEntityQueries(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
//        val entityQuery = retrieveSequence(query)
    val entityQuery = AnalyzerFunctions.createQuery(query, useFiltering = true, field = "spotlight")
//    val entityQuery = AnalyzerFunctions.createTokenList(query, useFiltering = true)
//        .flatMap { token ->
//            listOf(TermQuery(Term("spotlight", token)), TermQuery(Term(CONTENT, token)))
//        }
//        .fold(BooleanQuery.Builder()) { builder, termQuery ->
//            builder.add(termQuery, BooleanClause.Occur.SHOULD) }
//        .build()


    return tops.scoreDocs.map { scoreDoc ->
        indexSearcher.explain(entityQuery, scoreDoc.doc).value.toDouble() }
}


/**
 * Function: useLucSim
 * Description: Takes a Lucene similarity function and uses it to rescore documents.
 */
fun featUseLucSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher, sim: Similarity): List<Double> {
//        val entityQuery = retrieveSequence(query)
    val booleanQuery = AnalyzerFunctions.createQuery(query, useFiltering = true)
//        val entityQuery = AnalyzerFunctions.createTokenList(query, useFiltering = true)
//            .map { token -> TermQuery(Term(CONTENT, token)) }
//            .fold(BooleanQuery.Builder()) { builder, termQuery ->
//                builder.add(termQuery, BooleanClause.Occur.SHOULD) }
//            .build()

    val curSim = indexSearcher.getSimilarity(true)
    indexSearcher.setSimilarity(sim)

    return tops.scoreDocs.map { scoreDoc ->
        indexSearcher.explain(booleanQuery, scoreDoc.doc).value.toDouble() }
        .apply { indexSearcher.setSimilarity(curSim) }  // Gotta remember to set the similarity back
}


/**
 * Function: sectionSplit
 * Description: Splits query up and only uses a particular section for scoring.
 */
fun featSectionSplit(query: String, tops: TopDocs, indexSearcher: IndexSearcher, secIndex: Int): List<Double> {
    val termQueries = query.split("/")
        .map { section -> AnalyzerFunctions
            .createTokenList(section, useFiltering = true)
            .joinToString(" ")}
        .map { section -> AnalyzerFunctions.createQuery(section)}
        .toList()

    if (termQueries.size < secIndex + 1) {
        return (0 until tops.scoreDocs.size).map { 0.0 }
    }

    val boolQuery = termQueries[secIndex]

    return tops.scoreDocs
        .map { scoreDoc ->
            indexSearcher.explain(boolQuery, scoreDoc.doc).value.toDouble()
        }
}



/**
 * Function: addScoreMixtureSims
 * Description: Uses Random Walk model over bipartite graph of entities and paragraphs to rescore paragraphs.
 */
fun featAddScoreMixtureSims(query: String, tops:TopDocs, indexSearcher: IndexSearcher,
                        graphAnalyzer: KotlinGraphAnalyzer): List<Double> {
    val sinks = HashMap<String, Double>()
    val mixtures = graphAnalyzer!!.getMixtures(tops)

    mixtures.forEach { pm ->
        pm.mixture.forEach { entity, probability ->
            sinks.merge(entity, probability * pm.score, ::sum)
        }
    }

    val total = sinks.values.sum()
    sinks.replaceAll { k, v -> v / total }

    // The score of each paragraph depends on the total value of all scored paragraphs with respect to their
    // distributions over entities.
    return mixtures
        .map { pm -> pm.mixture.entries.sumByDouble { (k, v) -> sinks[k]!! * v } }
        .toList()
}

/**
 * [Query / Training Results]
 *      The following are the query and training functions that I originally used in KotlinRanklibFormatter for
 *      prototype 1. I keep them (commented) down below for reference.
 */

//    /**
//     * Function: querySimilarity
//     * Description: Score with weighted combination of BM25 and string similarity functions (trained using RankLib).
//     */
//    fun querySimilarity() {
//        formatter.addBM25(weight = 0.884669653, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, _ ->
//            addStringDistanceFunction(query, tops, JaroWinkler())}, weight = -0.001055, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, _ ->
//            addStringDistanceFunction(query, tops, Jaccard() )}, weight = 0.11427, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: querySimilarity
//     * Description: Score with weighted combination of BM25 and average_query (trained using RankLib).
//     */
//    private fun queryAverage() {
//        formatter.addBM25(weight = 0.5, normType = NormType.ZSCORE)
//        formatter.addFeature(this::addAverageQueryScore, weight = 0.5, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: querySplit
//     * Description: Score with weighted combination of BM25 and separate section scores (trained using RankLib).
//     */
//    private fun querySplit() {
//        formatter.addBM25(weight = 0.4824247, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 0) }, weight = 0.069, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 1) }, weight = -0.1845, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 2) }, weight = -0.25063, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 3) }, weight = 0.0134, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: queryMixtures
//     * Description: Score with weighted combination of BM25 and mixtures method (trained using RankLib).
//     */
//    private fun queryMixtures() {
//        if (graphAnalyzer == null) {
//            println("You must supply a --graph_database location for this method!")
//            return
//        }
//        formatter.addBM25(weight = 0.9703138257, normType = NormType.ZSCORE)
//        formatter.addFeature(this::addScoreMixtureSims, weight = 0.029686174, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: queryDirichlet
//     * Description: Score with weighted combination of BM25 and LM_Dirichlet method (trained using RankLib)
//     */
//    private fun queryDirichlet() {
//        formatter.addBM25(weight = 0.80067, normType = NormType.ZSCORE)
//        formatter.addFeature({query, tops, indexSearcher ->
//            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, weight = 0.19932975,
//                normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: queryMercer
//     * Description: Score with weighted combination of BM25 and LM_Dirichlet method (trained using RankLib)
//     */
//    private fun queryMercer() {
//        formatter.addBM25(weight = 0.82, normType = NormType.ZSCORE)
//
//        formatter.addFeature({query, tops, indexSearcher ->
//            useLucSim(query, tops, indexSearcher, LMJelinekMercerSimilarity(LMSimilarity.DefaultCollectionModel(),
//                    0.5f))}, weight = 0.1798988, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: trainSimilarity
//     * Description: training for string_similarity method.
//     * @see querySimilarity
//     */
//    private fun trainSimilarity() {
//        formatter.addBM25(normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, _ ->
//            addStringDistanceFunction(query, tops, JaroWinkler())}, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, _ ->
//            addStringDistanceFunction(query, tops, Jaccard() )}, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: trainSplit
//     * Description: training for section_split method.
//     * @see sectionSplit
//     */
//    private fun trainSplit() {
//        formatter.addBM25(normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 0) }, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 1) }, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 2) }, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            sectionSplit(query, tops, indexSearcher, 3) }, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: trainMixtures
//     * Description: training for mixtures method.
//     * @see queryMixtures
//     */
//    private fun trainMixtures() {
//        formatter.addBM25(normType = NormType.ZSCORE)
//        formatter.addFeature(this::addScoreMixtureSims, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: trainAverageQuery
//     * Description: training for average_query method.
//     * @see queryAverage
//     */
//    private fun trainAverageQuery() {
//        formatter.addBM25(normType = NormType.ZSCORE)
//        formatter.addFeature(this::addAverageQueryScore, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: trainDirichSim
//     * Description: training for lm_dirichlet method.
//     * @see queryDirichlet
//     */
//    private fun trainDirichSim() {
//        formatter.addBM25(normType = NormType.ZSCORE)
//        formatter.addFeature({query, tops, indexSearcher ->
//            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, normType = NormType.ZSCORE)
//    }


//    /**
//     * Function: trainJelinekMercerSimilarity
//     * Description: training for lm_mercer method.
//     * @see queryMercer
//     */
//    private fun trainJelinekMercerSimilarity() {
//        formatter.addBM25(normType = NormType.ZSCORE)
//        formatter.addFeature({query, tops, indexSearcher ->
//            useLucSim(query, tops, indexSearcher, LMJelinekMercerSimilarity(LMSimilarity.DefaultCollectionModel(),
//                    0.5f))}, normType = NormType.ZSCORE)
//    }
