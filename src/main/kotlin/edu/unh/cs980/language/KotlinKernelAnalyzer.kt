package edu.unh.cs980.language

import edu.unh.cs980.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED
import edu.unh.cs980.misc.GradientDescenter
import edu.unh.cs980.misc.PartitionDescenter
import org.apache.commons.math3.distribution.NormalDistribution
import smile.math.matrix.Matrix
import smile.math.matrix.PageRank
import smile.math.special.Erf.erf
import java.io.File
import java.lang.Double.sum
import java.lang.Math.*
import java.util.*
import kotlin.math.log2
import kotlin.math.roundToInt

// Sorry... I couldn't find the energy to fully document this portion of my code. It's very messy...

/**
 * Enum: MixtureDistanceMeasure
 * Desc: Describes what method to use when measuring distances between two mixtures.
 */
enum class MixtureDistanceMeasure {
    EUCLIDEAN, DELTA_SIM, MINKOWSKI, COSINE, DELTA_DENSITY, MANHATTAN, EUCLIDEAN_SIM, KLD
}

/**
 * Class: TopicMixtureResult
 * Desc: Stores the results of a mixture model of topics. Think of these like points on a simplex.
 */
data class TopicMixtureResult(val results: SortedMap<String, Double>, val kld: Double) {

    fun reportResults() {
        results.entries
            .filter { (_, weight) -> weight > 0.0 }
            .sortedByDescending { (_, weight) -> weight }
            .forEach { (topicName, weight) ->
                println("$topicName : $weight")}

        println("KLD: $kld\n\n")
    }

    fun distance(other: TopicMixtureResult, distType: MixtureDistanceMeasure = MixtureDistanceMeasure.MINKOWSKI) =
        when(distType) {
            MixtureDistanceMeasure.EUCLIDEAN -> euclideanDistance(other)
            MixtureDistanceMeasure.EUCLIDEAN_SIM -> euclideanSim(other)
            MixtureDistanceMeasure.DELTA_SIM -> deltaSim(other)
            MixtureDistanceMeasure.COSINE -> cosineSim(other)
            MixtureDistanceMeasure.MINKOWSKI -> minkowskiDistance(other)
            MixtureDistanceMeasure.DELTA_DENSITY -> deltaDensity(other)
            MixtureDistanceMeasure.MANHATTAN -> manhattenDistance(other)
            MixtureDistanceMeasure.KLD -> kld(other)
        }

    fun euclideanDistance(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> (pow(x - y, 2.0)) }
                .apply { sqrt(this)  }

    fun euclideanSim(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> (pow(x - y, 2.0)) }
                .apply { (1 / sqrt(this)).defaultWhenNotFinite(0.0)  }

    fun minkowskiDistance(other: TopicMixtureResult): Double =
        results.values.zip(other.results.values)
            .sumByDouble { (x, y) -> (pow(abs(x - y), results.values.size.toDouble())) }
            .apply { pow(abs(this), 1 / results.values.size.toDouble())  }

    fun deltaSim(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> (x * y) }

    fun weirdSim(other: TopicMixtureResult): Double {
        val delta = deltaSim(other)
        val manhatten = euclideanDistance(other)
        return delta * (1 - manhatten)
    }

    fun weirdSim2(other: TopicMixtureResult): Double {
        val delta = deltaSim(other)
        val manhatten = euclideanDistance(other)
        return delta / max(0.00001, manhatten)
    }

    fun kld(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) ->
                    val (xM, yM) = max(0.0001, x) to max(0.0001, y)
                    xM * log2(xM / yM)
                }

    fun deltaDensity4(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> y * (abs(y - x)) }

    fun deltaDensity2(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> (x * y) / max(0.001, (abs(x - y))) }

    fun deltaDensity(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> x * (x - y) + y * (y - x) }

    fun manhattenDistance(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> abs(x - y) }

    fun cosineSim(other: TopicMixtureResult): Double {
        val totalProduct = results.values.zip(other.results.values)
            .sumByDouble { (x, y) -> (x * y) }

        val xProduct = results.values.sumByDouble { value -> value * value }.apply { sqrt(this) }
        val yProduct = other.results.values.sumByDouble { value -> value * value }.apply { sqrt(this) }
        return totalProduct / (xProduct * yProduct)

    }

}

/**
 * Class: WordKernel
 * Desc: A distribution over words with respect to a word
 */
class WordKernel(val word: String) {
    val distribution = HashMap<String, Double>()
    var frequency = 0.0

    fun normalize() {
        val total = distribution.values.sum()
        distribution.forEach { (k,v) -> distribution[k] = v / total }
    }


}

/**
 * Class KernelDist
 * Desc: A distribution over distributions of words.
 */
class KernelDist(val mean: Double, val std: Double, val doCondition: Boolean = true,
                 val analyzer: AnalyzerFunctions.AnalyzerType = ANALYZER_ENGLISH) {
    val kernels = HashMap<String, WordKernel>()

    val distMappings = getDistMappings(mean, std)

    private fun updateKernels(w1: String, w2: String, dist: Int) {
        updateKernel(w1, w2, dist)
        updateKernel(w2, w1, dist)
    }

    private fun updateKernel(kernelWord: String, destWord: String, dist: Int) {
        kernels.computeIfAbsent(kernelWord, { WordKernel(kernelWord) })
            .distribution
            .merge(destWord, distMappings[dist], ::sum)
    }

    private fun getDistMappings(mean: Double, sd: Double): List<Double> {
        val norm = NormalDistribution(mean, sd)
        return (0 .. 10).map { param -> norm.cumulativeProbability(param.toDouble()) }
            .windowed(2)
            .map { (v1, v2) -> v2 - v1 }
    }

    fun analyzeDocument(text: String, letterGram: Boolean = false) {
        val filterPattern = "[\\d+]".toRegex()
        val filteredText = filterPattern.replace(text, "")
        val terms = AnalyzerFunctions.createTokenList(filteredText, analyzerType = ANALYZER_ENGLISH_STOPPED)

        if (letterGram) {
            terms.forEach { term ->
                term.windowed(4, partialWindows = false)
                    .forEach { lGram -> kernels.computeIfAbsent(lGram, { WordKernel(term) }).frequency += 1.0 }
            }
            return
        }

        terms.forEach { term -> kernels.computeIfAbsent(term, { WordKernel(term) }).frequency += 1.0 }
        if (doCondition) {
            terms
                .windowed(8, 1, true)
                .forEach { window ->
                    val firstTerm = window[0]

                    window
                        .slice(1 until window.size)
                        .forEachIndexed { index, secondTerm ->
                            updateKernels(firstTerm, secondTerm, index)
                        }
                }
        }
    }

    fun analyzePartitionedDocument(text: String, letterGram: Boolean = false) =
            text
                .toLowerCase()
                .split(".")
                .filter { it.length > 3 }
                .forEach { splitText -> analyzeDocument(splitText, letterGram) }


    // Used for embedding
    fun perturb(nSamples: Int = 50): Pair<List<String>, List<List<Double>>> {
        val norm = NormalDistribution(sharedRand, 1.0, 0.001)
//        val norm = NormalDistribution(sharedRand, 0.0, 0.5)
        val (kernelNames, kernelFreqs) = kernels.toList().unzip()

        val perturbations = (0 until nSamples).map { index ->
            norm.sample(kernelFreqs.size)
                .toList()
                .zip(kernelFreqs)
                .map { (gaussian, kernelFreq) -> gaussian + kernelFreq.frequency  }.normalize() }
//                    .map { (gaussian, kernelFreq) -> gaussian  * 0.0001 +  kernelFreq.frequency  } }
//                    .map { (gaussian, kernelFreq) -> erf( (index - nSamples/2.0)/20.0 ) *  kernelFreq.frequency  } }
//            .map { (gaussian, kernelFreq) -> gaussian * 0.0001 +  kernelFreq.frequency  } }

        return kernelNames to perturbations
    }



    fun getKernelFreqs() = kernels.map { (word, kernel) -> word to kernel.frequency }.toMap()


    fun normalizeKernels() {
        val total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach{ wordKernel -> wordKernel.normalize()}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }
    }


    fun equilibriumCovariance() {
        kernels.values.forEach { worKernel -> worKernel.normalize() }
        val wordIndices = kernels.keys.mapIndexed { index, key -> key to index  }.toMap()
        val reverseIndices = wordIndices.map { (k,v) -> v to k }.toMap()
        val covarianceMatrix = Matrix.newInstance(wordIndices.size, wordIndices.size, 0.0)
        val base = wordIndices.map { (word, index) -> kernels[word]!!.frequency }.toDoubleArray()

        kernels.values.map { kernel ->
            val i = wordIndices[kernel.word]!!
            kernel.distribution.forEach { (word,covariance) ->
                val j = wordIndices[word]!!
                covarianceMatrix[i,j] = covariance
            }
        }

        val ranked = PageRank
            .pagerank(covarianceMatrix, base, 0.9, 0.00000001, 1000)
                .map { if (it < 0.0) 1 / wordIndices.size.toDouble() else it }

        val total = ranked.sum()
        ranked.forEachIndexed { index, d -> kernels[reverseIndices[index]]!!.frequency = d / total  }
    }
}

/**
 * Class: KotlinKernelAnalyzer
 * Desc: Given a directory containing topics, creates distributions representing each topic (unigram freqs).
 */
class KotlinKernelAnalyzer(val mean: Double, val std: Double, val corpus: (String) -> Double?,
                           val partitioned: Boolean = false) {
    val topics = HashMap<String, KernelDist>()

    fun analyzeTopicDirectories(mainDirectory: String, filterList: List<String> = listOf(), smooth: Boolean = false) {
        File(mainDirectory)
            .listFiles()
            .filter(File::isDirectory)
            .filter { file -> filterList.isEmpty() || file.name in filterList  }
            .forEach { file ->  analyzeTopic(file.name, file, smooth)}
    }

    private fun analyzeTopic(topic: String, directory: File, smooth: Boolean) {
        val kernelDist = KernelDist(mean, std, doCondition = smooth)
        topics[topic] = kernelDist
        directory.listFiles()
            .forEach { file ->  if (partitioned) kernelDist.analyzePartitionedDocument(file.readText())
                                else kernelDist.analyzeDocument(file.readText())}
    }

    fun createTopicFromParagraph(topic: String, paragraph: String, smooth: Boolean) {
        val kernelDist = KernelDist(mean, std, doCondition = smooth)
        topics[topic] = kernelDist
        kernelDist.analyzePartitionedDocument(paragraph)
    }


    fun normalizeTopics() {
        topics.values.forEach { topic -> topic.normalizeKernels() }
    }

    fun retrieveTopicFrequencies() =
            topics.map { (topic, kernel) ->
                topic to kernel.getKernelFreqs()
            }

    // Run gradient descent on perturbed versions of target distribution
    fun classifyByDomainSimplex2(integrals: List<Pair<String, List<Double>>>, nIterations: Int = 500, smooth: Boolean = false): Triple<List<String>, List<Double>, Double> {
        val identityFreq = integrals.find { it.first == "identity" }!!.second
        val (featureNames, featureFreqs) = integrals.filter { it.first != "identity" }.unzip()

        val stepper = GradientDescenter(identityFreq, featureFreqs)
        val (weights, kld) = stepper.startDescent(nIterations)
        return Triple(featureNames, weights, kld)
    }
}
