@file:JvmName("KotRankLibTrainer")
package edu.unh.cs980.ranklib

import edu.unh.cs980.CONTENT
import edu.unh.cs980.KotlinDatabase
import edu.unh.cs980.KotlinGraphAnalyzer
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.features.*
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.language.GramStatType
import edu.unh.cs980.language.KotlinAbstractAnalyzer
import edu.unh.cs980.language.KotlinGramAnalyzer
import edu.unh.cs980.misc.AnalyzerFunctions
import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import info.debatty.java.stringsimilarity.SorensenDice
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.search.similarities.*
import edu.unh.cs980.WordEmbedding.TfIdfSimilarity
import edu.unh.cs980.ranklib.QueryEnum.*
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.lang.Double.sum
import java.util.*
import kotlin.math.abs
import edu.unh.cs980.ranklib.TrainEnum.*
import edu.unh.cs980.ranklib.TrainEnum.SDM_SECTION
import edu.unh.cs980.ranklib.TrainEnum.STRING_SIMILARITY_SECTION
import edu.unh.cs980.ranklib.TrainEnum.TFIDF_SECTION


/**
 * Function: KotlinRankLibTrainer
 * Description: This is used to encapsulate my different query methods, and the training methods I used to
 *              learn their weights.
 */
class KotlinRankLibTrainer(val indexPath: String, val queryPath: String, val qrelPath: String,
                           val hyperlinkPath: String, val abstractPath: String, val gramPath: String ) {

//    val db = if (graphPath == "") null else KotlinDatabase(graphPath)
    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
//    val graphAnalyzer = if (graphPath == "") null else KotlinGraphAnalyzer(formatter.indexSearcher, db!!)



    /**
     * Function: queryCombined
     * Description: Score with weighted combination of BM25, Jaccard string similarity, LM_Dirichlet, and second/third
     *              section headers (trained using RankLib).
     */
    private fun queryCombined() {
//        val weights = listOf(0.3106317698753524,-0.025891305471130843,
//                0.34751201103557083, -0.2358113441529167, -0.08015356975284649)

        val weights = listOf(0.27569142, 0.17437123, 0.20848581, 0.34145153, 0.0, 0.0, 0.0)
//        val weights = listOf(0.61881053, 0.0, 0.0, 0.38118944)
//        val weights = listOf(0.5327952, 0.0, 0.18299106, 0.28421375)

        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE, weight = weights[0])

        formatter.addFeature({ query, tops, indexSearcher ->
            featStringSimilarityComponent(query, tops, indexSearcher)
        }, normType = NormType.ZSCORE, weight = weights[1])

        formatter.addFeature({query, tops, indexSearcher ->
            featUseLucSim(query, tops, indexSearcher, LMDirichletSimilarity())
        }, normType = NormType.ZSCORE, weight = weights[2])

        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE, weight = weights[3])

//        formatter.addFeature({ query, tops, indexSearcher ->
//            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
//        }, normType = NormType.ZSCORE)
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
//        }, normType = NormType.ZSCORE)
    }


    /**
     * Func: queryAverageAbstractScore
     * Desc: First, relevant entities are determined by querying abstract Lucene index.
     *       The top 20 documents are the "relevant" entities, and these are used to score the paragraph documents
     *       by taking the average score of "relevant" entities that the paragraph was annotated with using Spotlight.
     */
    private fun queryAverageAbstractScore() {
        val weights = listOf(0.8974292632642049, -0.10257073673579509)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE, weight = weights[1])
    }


    /**
     * Func: queryHyperlinkLikelihood
     * Desc: The allButParagraphCorpus was parsed for "entity mentions" according to what page was linked for each
     *       given anchor text. This is used to build a model for the likelihood of an entity given the query.
     *       Each document is then scored according to the likelihood of its entities given the query.
     *       This method is basically an implementation of the "popularity linking" method from the entity linking
     *       variations assignment.
     */
    private fun queryHyperlinkLikelihood() {
        val weights = listOf(0.8821131679, -0.11788632077)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)},
                normType = NormType.ZSCORE, weight = weights[1])
    }


    /**
     * Func: querySDM
     * Desc: This is a Sequential Dependence Model method, in which the score of a document is based on likelihood
     *       given query using unigram, bigram, and windowed bigram models. Each of scoring components have been
     *       weighted according to training with RankLib.
     */
    private fun querySDM() {
        val weights = listOf(0.14059688887081667, 0.8594031111291832)
        val gramSearcher = getIndexSearcher(gramPath)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val hGram = KotlinGramAnalyzer(gramSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE, weight = weights[1])
    }


    /**
     * Func: queryAbstractSDM
     * Desc: This method is similar to the SDM method, except that a language model is built from entity abstracts.
     *       These abstracts are the first three paragraphs in the page that the entity represents.
     *       The score of a document is based on the average likelihood score of its annotated entities given an
     *       SDM model.
     */
    private fun queryAbstractSDM() {
        val weights = listOf(0.86553535, 0.134464695)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
        }, normType = NormType.ZSCORE, weight = weights[1])
    }


    /**
     * Func: querySDMExpansion
     * Desc: This method is a variant of SDM where the query is first expanded using Kevin's query entity expansion
     *       method. In effect, it queries the abstract dabase and takes the top entity's abstracts, identifies the
     *       linked entities using Spotlight, and then returns these as a list of tokens.
     *
     *       An SDM is generated for every expanded query term and the results are averaged.
     */
    private fun querySDMExpansion() {
        val weights = listOf(0.9691977861102452, -0.030802213889754796)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        formatter.addFeature({ query, tops, indexSearcher ->
            featSDMWithEntityQueryExpansion(query, tops, indexSearcher,
                    hGram, abstractAnalyzer.indexSearcher, 4.0)},
                normType = NormType.ZSCORE, weight = weights[1])
    }


    /**
     * Func: querySDMSection
     * Desc: Like the section-paths variant of BM25, the SDM method is run on each of a query's sections,
     *       and the score of a document is expressed as a weighted sum of likelihoods given each section.
     */
    private fun querySDMSection() {
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
//        val weights = listOf(0.18040763371108623, 0.053702972763138165, 0.3145376765137826, 0.45135171701199295)
        val weights = listOf(0.08047025663846726, 0.030239885393043505, 0.15642380129849698, 0.45881012321282,
        0.1370279667285861, 0.1370279667285861
        )

        val bindSDM = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }

        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, bindSDM, weights)
        }, normType = NormType.ZSCORE)
    }


    /**
     * Func: queryTFIDF
     * Desc: This takes Bindu's TFIDF metric and applies it to each of the sections of a query.
     *       The final score is a weighted combination of the sections scored with Bindu's TFIDF.
     */
    private fun queryTFIDFSection() {
        val tifdSearcher = getIndexSearcher(indexPath)
        val tifd = TfIdfSimilarity(100, tifdSearcher)
        val bindTIFD = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featTFIFDAverage(query, tops, indexSearcher, tifd)
        }
        val featureWeights = listOf(0.945, -0.054399)
        val tifdWeights = listOf(0.0000484, 0.000018545, 0.00244388, 0.996917, 0.000001823081, 0.000001823081)

        formatter.addBM25(normType = NormType.ZSCORE, weight = featureWeights[0])
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, bindTIFD, secWeights = tifdWeights)},
                normType = NormType.ZSCORE, weight = featureWeights[1])
    }

    /**
     * Func: queryNatSDM
     * Desc: This uses Kevin's natural language processing methods to extract sentence structure from the
     *       query and the text of each of the paragraphs. In this method, I create language models for the nouns
     *       and verbs extracted from the query and document text and do SDM on these models.
     *       Hopefully in the next prototype we can do something a little advantage with this structure.
     */
//    private fun queryNatSDM() {
//        System.err.close() // Stanford NLP needs to shut the hell up
//        val weights = listOf(0.62146543079, 0.37853)
//        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
//        val gramSearcher = getIndexSearcher(gramPath)
//        val hGram = KotlinGramAnalyzer(gramSearcher)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featNatSDM(query, tops, indexSearcher, hGram, 4.0)
//        }, normType = NormType.ZSCORE, weight = weights[1])
//    }

    /**
     * Func: queryStringSimilaritySection
     * Desc: The Jaccard, Jaro Winkler, Normalized Levenshtein, and Sorensen Dice string similarity metrics were used to
     *       score a document according to the average similarity of a document's entity names to the term queries.
     *       Weights were trained for this combination using RankLib.
     *       The new feature was then used to score each section in a query, wherein a document's score is now the
     *       weighted sum of sections scored according to this feature. The weights were again trained using RankLib and
     *       the final string_similarity_section feature was generated (used below).
     */
    private fun queryStringSimilaritySection() {
        val weights = listOf(0.8795315, 0.1203685)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])

        formatter.addFeature({ query, tops, indexSearcher ->
            featStringSimilarityComponent(query, tops, indexSearcher)
        }, normType = NormType.ZSCORE, weight = weights[1])
    }

    /**
     * Func: querySuperAwesomeTeamworkQuery
     * Desc: This lovingly crafted feature contains the combined prowess of each teammate. It's sure to be a winner!...
     *       ... this is a RankLib trained combination of nat_sdm, tfidf_section, and sdm_expansion (no bm25 included!)
     * @see trainSuperAwesomeTeamworkQuery
     */
    private fun querySuperAwesomeTeamworkQuery() {
        addTeamworkFeatures(listOf(0.35881632866737, 0.5789712, 0.062212395))
    }

    // Runs associated query method
    fun runRanklibQuery(method: String, out: String) {
        Logger.getRootLogger().level = Level.ERROR
        val queryMethod = QueryEnum.fromString(method)
        when (queryMethod) {
            AVERAGE_ABSTRACT                    -> queryAverageAbstractScore()
            HYPERLINK                           -> queryHyperlinkLikelihood()
            ABSTRACT_SDM                        -> queryAbstractSDM()
            SDM                                 -> querySDM()
//            NAT_SDM                             -> queryNatSDM()
            QueryEnum.SDM_SECTION               -> querySDMSection()
            SDM_EXPANSION                       -> querySDMExpansion()
            QueryEnum.TFIDF_SECTION             -> queryTFIDFSection()
            QueryEnum.STRING_SIMILARITY_SECTION -> queryStringSimilaritySection()
            COMBINED                            -> queryCombined()
            SUPER_AWESOME_TEAMWORK              -> querySuperAwesomeTeamworkQuery()
            null                                -> {println("Unknown method!"); return}
        }

        // After scoring according to method, rerank the queries and write them to a run file
        formatter.rerankQueries()
        formatter.queryRetriever.writeQueriesToFile(formatter.queries, out)
    }




    /**
     * Function: trainCombinedQuery
     * Description: training for combined method.
     * @see queryCombined
     */
    private fun trainCombinedQuery() {
        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featStringSimilarityComponent(query, tops, indexSearcher)
        }, normType = NormType.ZSCORE)

        formatter.addFeature({query, tops, indexSearcher ->
            featUseLucSim(query, tops, indexSearcher, LMDirichletSimilarity())
        }, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
        }, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE)

    }


    /**
     * Func: trainDirichletAlpha
     * Desc: This is my attempt at tuning the alpha parameter of the SDM method I implemented by generating a
     *       lot of instances of the SDM with different values of alpha. The KotlinFeatureSelector class is then used
     *       to do pairwise feature selection (where the first feature is always BM25 and the other feature is one of
     *       the instances of the SDM with a particular alpha), where the best result determines the alpha to use.
     */
    private fun trainDirichletAlpha() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        listOf(2, 8, 16, 32, 64, 128).forEach { alpha ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, alpha.toDouble())
            }, normType = NormType.ZSCORE)
        }

    }

    /**
     * Func: trainAbstractSDMAlpha
     * Desc: This is the same method as trainDirichletAlpha, except I am training the alpha parameter for
     *       the abstract SDM method.
     */
    private fun trainAbstractSDMAlpha() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val abstractSearcher = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractSearcher)
        listOf(2, 8, 16, 32, 64, 128, 256, 512, 1024).forEach { alpha ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, alpha.toDouble())
            }, normType = NormType.ZSCORE)
        }

    }


    /**
     * Func: trainSDMQuery
     * Desc: Combining SDM and BM25 to determine weights for method.
     * @see querySDM
     */
    private fun trainSDMQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE)
    }


    /**
     * Func: trainNatSDMQuery
     * Desc: Combining Nat SDM and BM25 to determine weights for method.
     * @see queryNatSDM
     */
//    private fun trainNatSDMQuery() {
//        System.err.close() // Stanford NLP needs to shut the hell up
//        formatter.addBM25(normType = NormType.ZSCORE)
//        val gramSearcher = getIndexSearcher(gramPath)
//        val hGram = KotlinGramAnalyzer(gramSearcher)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featNatSDM(query, tops, indexSearcher, hGram, 4.0)
//        }, normType = NormType.ZSCORE)
//    }


    /**
     * Func: trainAbstractSDMQuery
     * Desc: Combining Abstract SDM and BM25 to determine weights for method.
     * @see queryAbstractSDM
     */
    private fun trainAbstractSDMQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
        }, normType = NormType.ZSCORE)
    }


    /**
     * Func: trainSDMComponents
     * Desc: Since the SDM score is based on three features, this method will treat each of the component scores as
     *       a feature (un-normalized), with the result from running RankLib being used to determine the best
     *       combination of these scores for the SDM method.
     * @see querySDM
     */
    private fun trainSDMComponents() {
        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, 4.0, gramType = gram)
            }, normType = NormType.NONE)
        }
    }


    /**
     * Func: trainAbstractSDMComponents
     * Desc: As trainSDMComponents, except for the Abstract SDM method.
     * @see queryAbstractSDM
     * @see trainSDMComponents
     */
    private fun trainAbstractSDMComponents() {
        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, gramType = gram, alpha = 2.0)
            }, normType = NormType.NONE)
        }
    }

    /**
     * Func: trainSectionPath
     * Desc: This was used to train the section-path version of BM25. The weights trained using RankLib
     *       determines the best combination of the section scores given BM25, and this is used to create
     *       the section component feature.
     * @see querySectionComponent
     */
    private fun trainSectionPath() {
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 0) }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 1) }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 2) }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 3) }, normType = NormType.NONE)
    }

    /**
     * Func: trainSectionComponent
     * Desc: This feature represents a linear combination of section scores using BM25.
     * @see trainSectionPath
     * @see querySectionComponent
     */
    private fun trainBM25Section() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE)
    }

    /**
     * Func: trainStringSimilarityComponents
     * Desc: For each of the string similarity metrics (Jaccard, JaroWinkler, NormalizedLevenshtein, SorensenDice),
     *       they are used to score a document according to the average similarity of the document's entities
     *       (annotated using Spotlight) to that of the query's terms. The scores are treated as separate features,
     *       and the weights of which are learned using RankLib.
     */
    private fun trainStringSimilarityComponents() {
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, Jaccard() )
        }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, JaroWinkler() )
        }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, NormalizedLevenshtein() )
        }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, SorensenDice() )
        }, normType = NormType.NONE)
    }


    /**
     * Func: trainStringSimilaritySection
     * Desc: Using a weighted combination of string similarity functions (tained with RankLib),
     *       a new feature is created from this by scoring each of the query's sections and learning a new
     *       set of weights.
     * @see trainStringSimilarityComponents
     */
    private fun trainStringSimilaritySection() {
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(1.0, 0.0, 0.0, 0.0))}, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(0.0, 1.0, 0.0, 0.0))}, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(0.0, 0.0, 1.0, 0.0))}, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(0.0, 0.0, 0.0, 1.0))}, normType = NormType.NONE)
    }

    /**
     * Func: trainStringSimilaritySectionQuery
     * Desc: The finished string_similarity_section method is combined with BM25 and the best linear combination
     *       is learned according to RankLib.
     * @see trainStringSimilarityComponents
     * @see trainStringSimilaritySection
     */
    private fun trainStringSimilaritySectionQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featStringSimilarityComponent(query, tops, indexSearcher)
        }, normType = NormType.ZSCORE)
    }


    /**
     * Func: trainAverageAbstractScore
     * Desc: This is training for the average_abstract method.
     * @see queryAverageAbstractScore
     */
    private fun trainAverageAbstractScore() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE)
    }


    /**
     * Func: trainHyperlinkLikelihood
     * Desc: This is training for the hyperlink method.
     * @see queryHyperlinkLikelihood
     */
    private fun trainHyperlinkLikelihood() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)
    }


    /**
     * Func: trainSDMExpansionQuery
     * Desc: This is training for the SDM expansion method.
     * @see querySDMExpansion
     */
    private fun trainSDMExpansionQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDMWithEntityQueryExpansion(query, tops, indexSearcher, hGram, abstractAnalyzer.indexSearcher, 4.0)
        }, normType = NormType.ZSCORE)
    }


    /**
     * Func: trainSDMEntityQueryExpansionComponents
     * Desc: As the training done with SDM components, except for the SDM entity expansion variant.
     * @see querySDMExpansion
     * @see trainSDMComponents
     */
    private fun trainSDMEntityQueryExpansionComponents() {
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDMWithEntityQueryExpansion(query, tops, indexSearcher,
                        hGram, abstractAnalyzer.indexSearcher, 4.0, gramType = gram)
            }, normType = NormType.NONE)
        }
    }


    /**
     * Func: trainSectionTFIDF
     * Desc: This uses Bindu's TFIDF score and scores each of the section separately, with the new score being the
     *       linear combination of each of the section score trained using RankLib.
     * @see queryTFIDFSection
     */
    private fun trainSectionTFIDF() {
        val tifdSearcher = getIndexSearcher(indexPath)
        val tifd = TfIdfSimilarity(100, tifdSearcher)
        val bindTIFD = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featTFIFDAverage(query, tops, indexSearcher, tifd)
        }

        val makeWeights = { pos: Int ->
            arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0).apply { this[pos] = 1.0 }
        }

        (0 until 6).forEach { sectionWeight ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSplitSim(query, tops, indexSearcher, bindTIFD, secWeights = makeWeights(sectionWeight))},
                    normType = NormType.NONE)
        }
    }


    /**
     * Func: trainTFIDFComponent
     * Desc: After the section-path version of Bindu's TFIDF method is trained, we see how it fairs as a feature
     *       when combined with BM25.
     * @see queryTFIDFSection
     * @see trainSectionTFIDF
     */
    private fun trainSectionTFIDFQuery() {
        // Initialize a new index searcher (Bindu's method sets its similarity to something different)
        val tifdSearcher = getIndexSearcher(indexPath)
        val tifd = TfIdfSimilarity(100, tifdSearcher)
        val bindTIFD = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featTFIFDAverage(query, tops, indexSearcher, tifd)
        }

        val tifdWeights = listOf(0.0000484, 0.000018545, 0.00244388, 0.996917, 0.000001823081, 0.000001823081)

        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, bindTIFD, secWeights = tifdWeights)},
                normType = NormType.ZSCORE)

    }

    /**
     * Func: trainSectionSDM
     * Desc: Given the SDM method, we want to see if we can improve it by treating each section of the query
     *       as a separate SDM model, and to learn a weighted combination of sections (scored with SDM) using
     *       RankLib.
     * @see querySDMSection
     */
    private fun trainSectionSDM() {
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val bindSDM = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, 4.0) }

        val makeWeights = { pos: Int ->
            arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0).apply { this[pos] = 1.0 }
        }

        (0 until 6).forEach { sectionWeight ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSplitSim(query, tops, indexSearcher, bindSDM, secWeights = makeWeights(sectionWeight))},
                    normType = NormType.NONE)
        }
    }


    /**
     * Func: trainSuperAwesomeTeamworkQuery
     * Desc: We gave our very best features to our fleet of worker gnomes who worked non-stop to
     *       combine them in the most awesome ways possible ... also RankLib might have helped.
     */
    private fun trainSuperAwesomeTeamworkQuery() {
        addTeamworkFeatures(listOf(1.0, 1.0, 1.0))
    }


    /**
     * Func: addTeamworkFeatures
     * Desc: Adds sdm_nat, sdm_expansion, and tfidf_section methods as features. Used for training with RankLib, and
     *       also with querying.
     */
    private fun addTeamworkFeatures(weights: List<Double>) {
        System.err.close() // Stanford NLP needs to shut the hell up
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        // Adding Entity Query Expansion feature (sdm_expansion) derived from Kevin's methods
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDMWithEntityQueryExpansion(query, tops, indexSearcher, hGram, abstractAnalyzer.indexSearcher, 4.0)
        }, normType = NormType.ZSCORE, weight = weights[0])


//        // Adding Nat SDM feature (nat_sdm) derived from Kevin's methods
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featNatSDM(query, tops, indexSearcher, hGram, 4.0)
//        }, normType = NormType.ZSCORE, weight = weights[1])

        // Adding TIFD feature derived from Bindu's feature
        val tifdSearcher = getIndexSearcher(indexPath)
        val tifd = TfIdfSimilarity(100, tifdSearcher)
        val bindTIFD = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featTFIFDAverage(query, tops, indexSearcher, tifd)
        }


        val tifdWeights = listOf(0.0000484, 0.000018545, 0.00244388, 0.996917, 0.000001823081, 0.000001823081)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, bindTIFD, secWeights = tifdWeights)},
                normType = NormType.ZSCORE, weight = weights[1])
    }


    /**
     * Function: train
     * Description: Add features associated with training method and then writes scored features to a RankLib compatible
     *              file for later use in training weights.
     */
    fun train(method: String, out: String) {
        Logger.getRootLogger().level = Level.ERROR
        val trainMethod = TrainEnum.fromString(method)

        when (trainMethod) {
            HYPERLINK_QUERY -> trainHyperlinkLikelihood()                               // hyperlink + bm25

            AVERAGE_ABSTRACT_QUERY -> trainAverageAbstractScore()                       // average_abstract + bm25

            ABSTRACT_SDM_COMPONENTS -> trainAbstractSDMComponents()                     // learn -gram score weights
            ABSTRACT_SDM_ALPHA -> trainAbstractSDMAlpha()                               // learn alpha param
            ABSTRACT_SDM_QUERY -> trainAbstractSDMQuery()                               // abstract_sdm + bm25

//            NAT_SDM_QUERY -> trainNatSDMQuery()                                         // nat_sdm + bm25

            SDM_ALPHA -> trainDirichletAlpha()                                          // learn alpha param
            SDM_COMPONENTS -> trainSDMComponents()                                      // learn -gram score weights
            SDM_SECTION -> trainSectionSDM()                                            // section version of sdm
            SDM_QUERY -> trainSDMQuery()                                                // sdm + bm25

            TFIDF_SECTION -> trainSectionTFIDF()                                        // section version of TFIDF
            TFIDF_SECTION_QUERY -> trainSectionTFIDFQuery()                             // section TFIDF + bm25

            BM25_SECTION -> trainBM25Section()                                          // section BM25 + bm25

            SDM_EXPANSION_COMPONENTS -> trainSDMEntityQueryExpansionComponents()        // sdm_expansion -gram weights
            SDM_EXPANSION_QUERY -> trainSDMExpansionQuery()                             // sdm_expansion + bm25

            STRING_SIMILARITY_COMPONENTS -> trainStringSimilarityComponents()           // learn similarity weights
            STRING_SIMILARITY_SECTION -> trainStringSimilaritySection()                 // learn section weights
            STRING_SIMILARITY_QUERY -> trainStringSimilaritySectionQuery()              // string_sim_sec + bm25

            COMBINED_QUERY -> trainCombinedQuery()                                      // combines various methods
            SUPER_AWESOME_TEAMWORK_QUERY -> trainSuperAwesomeTeamworkQuery()

            null -> {println("Unknown method!"); return}
        }

        formatter.writeToRankLibFile(out)
    }
}



