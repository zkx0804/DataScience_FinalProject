package edu.unh.cs980.features

import edu.unh.cs980.CONTENT
import edu.unh.cs980.language.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.paragraph.KotlinEmbedding
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs


/**
 * Enum: SheafQueryEmbeddingMethod
 * Desc: Used to specify how we should embed a query. OPtions are:
 *      MEAN: Stick the top 100 BM25 results together and embed that (then take distance of the results to the mean)
 *      QUERY: Just embed the text of the query
 *      QUERY_EXPANSION: For each term in query, retrieve top page using BM25. Stick these together and embed the result.
 */
enum class SheafQueryEmbeddingMethod {
    MEAN, QUERY, QUERY_EXPANSION
}


/**
 * Func: featUseEmbeddedQuery
 * Desc: This makes use of the perturbation embedding method. It's REALLY slow so I can't afford to evaluate many
 *       perturbations per paragraph (retrieved from top 100 BM25 search results). Because of this, it's highly
 *       variable and not-so-good.
 */
fun featUseEmbeddedQuery(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                            embedder: KotlinEmbedding): List<Double> {

    val paragraphs = tops.scoreDocs
        .map { indexSearcher.doc(it.doc).get(CONTENT)}

    val embeddings = paragraphs
        .map { paragraph ->  embedder.embed(paragraph, 50)}

    val queryEmbedding = embedder.embed(query, 50)
    return embeddings.map { projection ->
        projection.manhattenDistance(queryEmbedding)
    }
}

/**
 * Func: expandQuery
 * Desc: Convenience function to retrieve top 1 result for every term in query and concatenate them together.
 */
private fun expandQuery(query: String, indexSearcher: IndexSearcher): String =
    AnalyzerFunctions.createQueryList(query, useFiltering = true)
        .map { booleanQuery ->
            indexSearcher.search(booleanQuery, 1)
                .scoreDocs.firstOrNull()
                ?.let { scoreDoc -> indexSearcher.doc(scoreDoc.doc).get(CONTENT) } ?: " " }
        .joinToString("\n")


/**
 * Func: featUseExpandedEmbeeddedQuery
 * Desc: as featUseEmbeddedQuery, except query expansion is used first
 * @see expandQuery
 */
fun featUseExpandedEmbeddedQuery(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                         embedder: KotlinEmbedding): List<Double> {

    val paragraphs = tops.scoreDocs
        .map { indexSearcher.doc(it.doc).get(CONTENT)}

    val embeddings = paragraphs
        .map { paragraph ->  embedder.embed(paragraph, 50)}

    val expanded = expandQuery(query, indexSearcher)

    val queryEmbedding = embedder.embed(expanded, 50)
    return embeddings.map { projection -> projection.manhattenDistance(queryEmbedding)}
}


/**
 * Func: featSheafDist
 * Desc: This is the primary function for exploring variations of the hierarchical embedding that I describe in
 *       the report. Why am I calling these sheaves in my program? There are mathy reason. :)
 */
fun featSheafDist(query: String, tops: TopDocs, indexSearcher: IndexSearcher, analyzer: KotlinMetaKernelAnalyzer,
                  startLayer: Int, measureLayer: Int, reductionMethod: ReductionMethod,
                  normalize: Boolean, mixtureDistanceMeasure: MixtureDistanceMeasure,
                  queryEmbeddingMethod: SheafQueryEmbeddingMethod, filterList: List<String>,
                  ascentType: AscentType): List<Double> {

    val paragraphs = tops.scoreDocs
        .map { indexSearcher.doc(it.doc).get(CONTENT)}

    val queryText = when (queryEmbeddingMethod) {
        SheafQueryEmbeddingMethod.MEAN -> paragraphs.joinToString("\n")
        SheafQueryEmbeddingMethod.QUERY -> query
        SheafQueryEmbeddingMethod.QUERY_EXPANSION -> expandQuery(query, indexSearcher) }

    val queryEmbedding = analyzer.inferMetric(
            text = queryText, startingLayer = startLayer, doNormalize = normalize,
            measureLayer = measureLayer, reductionMethod = reductionMethod, filterList = filterList,
            ascentType = ascentType)

    val embeddedParagraphs = paragraphs.map { paragraph ->
        analyzer.inferMetric(text = paragraph, startingLayer = startLayer, doNormalize = normalize,
            measureLayer = measureLayer, reductionMethod = reductionMethod, filterList = filterList,
                ascentType = ascentType) }

    return embeddedParagraphs
        .map { embeddedParagraph ->
            queryEmbedding.distance(embeddedParagraph, mixtureDistanceMeasure)
        }

}
