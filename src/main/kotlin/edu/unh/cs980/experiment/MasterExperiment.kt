package edu.unh.cs980.experiment

import edu.unh.cs980.Main
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.features.*
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.identity
import edu.unh.cs980.language.*
import edu.unh.cs980.misc.MethodContainer
import edu.unh.cs980.misc.buildResourceDispatcher
import edu.unh.cs980.paragraph.KotlinEmbedding
import edu.unh.cs980.ranklib.KotlinRanklibFormatter
import edu.unh.cs980.ranklib.NormType
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs

/**
 * Class: MasterExperiment
 * Desc: An app used to create ranklib files and run files for all of my methods.
 *       See the train functions below for a description of my methods.
 */
class MasterExperiment(val resources: HashMap<String, Any>) {
    val indexPath: String by resources
    val qrelPath: String by resources
    val queryPath: String by resources
    val descent_data: String by resources
    val paragraphs: String by resources

    val out: String by resources

    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
    val indexer = getIndexSearcher(indexPath)
    val metaAnalyzer = KotlinMetaKernelAnalyzer(paragraphs)
    val embedder = KotlinEmbedding(indexPath)




    // Sorry for the mess... Partially binds my sheaf distance feature so that it's compatible with the
    // signature expected by my formatter.
    fun bindSheafDist(startLayer: Int, measureLayer: Int, reductionMethod: ReductionMethod,
                      normalize: Boolean, mixtureDistanceMeasure: MixtureDistanceMeasure,
                      queryEmbeddingMethod: SheafQueryEmbeddingMethod,
                      filterList: List<String> = emptyList(),
                      ascentType: AscentType = AscentType.ASCENT_SUM) =
        { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featSheafDist(query, tops, indexSearcher, metaAnalyzer, startLayer, measureLayer, reductionMethod,
                    normalize, mixtureDistanceMeasure, queryEmbeddingMethod, filterList, ascentType)
        }

    fun bindEmbedding() =
            { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
                featUseEmbeddedQuery(query, tops, indexSearcher, embedder)
            }

    fun bindEmbeddingExpansion() =
            { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
                featUseExpandedEmbeddedQuery(query, tops, indexSearcher, embedder)
            }


    /**
     * Func: trainClusters
     * Desc: In this method, I divide 15 topics into "clusters" of 3. Distances between the query and a paragraph are
     *       obtained by projecting them onto points on a 3D simplex and taking Manhattan distance.
     */
    fun trainClusters(level: Int = 0, weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        // Of the 21 features, I am partitioning 15 into five "clusters" and treating each as a feature
        val clusters = listOf(
                listOf("Cooking", "Games", "Society"),
                listOf("Warfare", "Biology", "Politics"),
                listOf("Technology", "Travel", "Environments"),
                listOf("Mathematics", "Fashion", "Engineering"),
                listOf("Events", "Organizations", "People") )


        clusters.forEachIndexed { index, cluster ->
            val boundSheaf = bindSheafDist(
                    startLayer = level, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = cluster)

            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }

    /**
     * Func: trainSubClusters
     * Desc: As above, except I treat the dimensions of the simplex are equal to the number of paragraphs that were used
     *       to construct each topic. The number is not exact because some of the paragraphs are "not important" the
     *       topic and were eliminated when decomposing the topics into their basis paragraphs.
     * @see Sheaf
     * @see Sheaf.descend  # to see how I decompose topics into paragraphs, paragraphs into sentences, etc.
     */
    fun trainSubClusters(weights: List<Double>? = null) {
        trainClusters(level = 1, weights = weights) // Level determines which sheaf we should have a distribution over
        // Level 0 = topic sheaves
        // Level 1 = paragraph sheaves
        // Level 2 = sentence sheaves
        // Level 3 = word sheaves
    }


    /**
     * Func: trainMetrics
     * Desc: I experiment with different distance measures between simplexes: Euclinean, Manhattan, Cosine, Minkowski.
     *       I am only considering a subset of topics (Cooking, Games, Society)
     */
    fun trainMetrics(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")

        val distances = listOf(MixtureDistanceMeasure.KLD,
                MixtureDistanceMeasure.EUCLIDEAN,
                MixtureDistanceMeasure.MANHATTAN,
                MixtureDistanceMeasure.COSINE,
                MixtureDistanceMeasure.MINKOWSKI)

        distances.forEachIndexed { index, curDist ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = curDist,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = myFilter)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)

        }
    }

    /**
     * Func: trainQueryEmbeddingMethods
     * Desc: In this experiment, I consider different approaches to embedding the query.
     *       QUERY: just use the query as-is
     *       MEAN:  merge the top 100 documents and project onto simplex. Order documents by their distance to
     *              this simplex (think of it like the "center" of a cluster).
     *       QUERY_EXPANSION: split query into tokens, query Lucene with each token, and merge first top document from
     *                        each search result into one giant query
     */
    fun trainQueryEmbeddingMethods(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")

        val queryEmbeddingMethods = listOf( SheafQueryEmbeddingMethod.QUERY,
                SheafQueryEmbeddingMethod.MEAN, SheafQueryEmbeddingMethod.QUERY_EXPANSION)

        queryEmbeddingMethods.forEachIndexed { index, embeddingMethod ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = embeddingMethod, filterList = myFilter)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }

    /**
     * Func: trainReductionMethods
     * Desc: In this experiment, I consider different approaches to integration by parts.
     *       AVERAGE: Average the similarities from words in query/paragraphs to those in words in bottom-most sheaf.
     *       MAX_MAX:  Sum up the maximum similarities of each word in query/paragraphs to words in  bottom-most sheaf.
     *       SMOOTHED_THRESHOLD: When the size of the query/paragraph is small, its distance to words matters more.
     *                           The similarity score from words in the query/paragraph is boosted when the size of
     *                           the query/paragraph is small.
     */
    fun trainReductionMethods(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")
        val reductions = listOf(ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD, ReductionMethod.REDUCTION_MAX_MAX,
                ReductionMethod.REDUCTION_AVERAGE)


        reductions.forEachIndexed { index, reduction ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = reduction,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = myFilter)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }

    /**
     * Func: trainAscentMethods
     * Desc: The value of a topic with respect to a query/paragraph is normally expressed by the sum of distances
     *       to the topic's words (weighted by how important the words are to the topic).
     *       Other methods are explored below:
     *       SUM: The normal method.
     *       MAX: At each level, take only the maximum similarity score of the sheaf's partitions after adjusting
     *            for the importance of each partition.
     *       THRESHOLD_SUM: Similarity scores are only passed up the sheaf if the sum meets a certain threshold.
     *                      (Think of activation threshold in neural networks)
     *       THRESHOLD_MAX: As above, except the threshold is based on the maximal score returned from the
     *                      lower sheaves.
     */
    fun trainAscentMethods(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")
        val ascentTypes = listOf(AscentType.ASCENT_SUM, AscentType.ASCENT_MAX,
                AscentType.ASCENT_THRESHOLD_SUM, AscentType.ASCENT_THRESHOLD_MAX)


        ascentTypes.forEachIndexed { index, ascentType ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = myFilter,
                    ascentType = ascentType)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }


    /**
     * Func: trainPerturbationEmbedding
     * Desc: Explores the perturbation embedding method (it takes a long time to run)
     */
    fun trainPerturbationEmbedding(weights: List<Double>? = null) {
        embedder.loadTopics(paragraphs)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)
        formatter.addFeature(bindEmbedding(), normType = NormType.ZSCORE, weight = weights?.get(1) ?: 1.0)
    }



    // This part is used to auto-generate required arguments / help for the arg parser.
    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("embedding")
                .help("Collection of features involving the embedding of queries/paragraphs in structures" +
                        " representing topic models.")

            val subparsers = mainParser.addSubparsers()
            val trainParser = subparsers.addParser("train")
                .help("Training methods for RankLib")
            register("train", trainParser)

            val queryParser = subparsers.addParser("query")
                .help("Query methods for RankLib")
            register("query", queryParser)

        }

        fun register(methodType: String, parser: Subparser)  {
            val exec = Main.buildExec { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = MasterExperiment(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<MasterExperiment>
                method.getMethod(methodType, methodName)?.invoke(instance)

                if (methodType == "query") {
                    instance.formatter
                        .apply { rerankQueries() }
                        .writeQueriesToFile(instance.out)
                } else {
                    instance.formatter.writeToRankLibFile("ranklib_results.txt")
                }
            }

//            parser
//                .help("Collection of features involving the embedding of queries/paragraphs in structures" +
//                        " representing topic models.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =

                // This is a DSL I created to specify resources / register methods to be run by the arg parser.
                buildResourceDispatcher {
                    methods<MasterExperiment> {
                        method("train", "hier_ascent") { trainAscentMethods() }
                        method("train", "hier_clusters") { trainClusters() }
                        method("train", "hier_subclusters") { trainSubClusters() }
                        method("train", "hier_query_variations") { trainQueryEmbeddingMethods() }
                        method("train", "hier_reduction_variations") { trainReductionMethods() }
                        method("train", "hier_metrics") { trainMetrics() }
                        method("train", "perturbation_embedding") { trainPerturbationEmbedding() }

                        method("query", "perturbation_embedding") {
                            val weights = listOf(0.9185739320834739, -0.08142606791652596)
                            trainPerturbationEmbedding(weights)
                        }

                        method("query", "hier_ascent") {
                            val weights = listOf(0.7979897527484472, 0.12058376153063591, -0.011997198702279513,
                                    0.05744986750822133, -0.011979419510416237)
                            trainAscentMethods(weights)
                        }
                        method("query", "hier_clusters") {
                            val weights = listOf(0.640247646419486, 0.02381076594399517, 0.15843688614499696,
                                    0.0927719044645985, 0.0769519821421867, 0.007780814884736802)
                            trainClusters(level = 0, weights = weights)
                        }
                        method("query", "hier_subclusters") {
                            val weights = listOf(0.6201414935405611, 0.32760472415596503, 0.003470668087484926,
                                    0.021169221571162626, 0.020374281242115634, 0.0072396114027104665 )
                            trainSubClusters(weights)
                        }
                        method("query", "hier_query_variations") {
                            val weights = listOf(0.7873001566878297, 0.15905487942543212, 0.002995974162120879,
                                    -0.050648989724617476 )
                            trainQueryEmbeddingMethods(weights)
                        }
                        method("query", "hier_reduction_methods") {
                            val weights = listOf(0.8344726387779878, 0.04094915290742655, 0.066957117430156,
                                    0.05762109088442964)
                            trainReductionMethods(weights)
                        }
                        method("query", "hier_metrics") {
                            val weights = listOf(0.5107086526395298, -0.06153874876279956, 0.027977509749829342,
                                    0.11095425188056572, 0.2800721248356613, 0.008748712131614461)
                            trainMetrics(weights)
                        }

                    }

                    resource("indexPath") {
                        help = "Location of the Lucene index database (Default: /trec_data/team_1/myindex"
                        default = "/trec_data/team_1/myindex"
                        loader = ::identity
                    }

                    resource("qrelPath") {
                        help = "Location to qrel file. Not required for querying/creating runfiles. (default: '')"
                        default = ""
                        loader = ::identity
                    }

                    resource("queryPath") {
                        help = "Location to query (.cbor) file."
                        loader = ::identity
                    }

                    resource("out") {
                        help = "Name of query file to create"
                        default = "query_results.run"
                        loader = ::identity
                    }


                    resource("paragraphs") {
                        help = "Location to paragraphs training directory (Default: /trec_data/team_1/paragraphs)"
                        default = "/trec_data/team_1/paragraphs/"
                        loader = ::identity
                    }

                    resource("descent_data") {
                        help = "Location to descent data directory (Default: /trec_data/team_1/descent_data)"
                        default = "/trec_data/team_1/descent_data/"
                        loader = ::identity
                    }

                }
    }

}