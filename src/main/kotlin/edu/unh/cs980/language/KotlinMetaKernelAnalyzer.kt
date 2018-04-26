package edu.unh.cs980.language

import edu.unh.cs980.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.GradientDescenter
import edu.unh.cs980.paragraph.KotlinStochasticIntegrator
import info.debatty.java.stringsimilarity.*
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.*
import java.lang.Math.max
import kotlin.math.log2

// Method to use for integrating text from query/paragraphs against sheaves
enum class ReductionMethod {
    REDUCTION_MAX_MAX, REDUCTION_AVERAGE, REDUCTION_SMOOTHED_THRESHOLD
}


enum class AscentType {
    ASCENT_SUM, ASCENT_MAX, ASCENT_THRESHOLD_SUM, ASCENT_THRESHOLD_MAX
}

// Desribes method to partition sheaf and a defines a measure over partitions
data class DescentData(val simFun: (String) -> Map<String, Double>,
                       val partitionFun: ((String) -> List<String>)? = null)


/**
 * Class: Sheaf
 * Desc: Describes a space covering another space. In the context of these experiment, a sheaf is a topic, paragraph,
 *       sentence, or word. We "descend" by describing how a sheaf can be approximated by its partitions. For example,
 *       the best linear combination of paragraphs that describes the unigram frequencies in a topic, and then the
 *       best linear combination of sentences in these paragraphs that describe k-mer frequencies, etc.
 *
 */
class Sheaf(val name: String, val partitions: List<String>, val kld: Double = 1.0, val cover: Sheaf? = null) : Serializable {
    val measure = HashMap<String, Pair<Sheaf, Double>>()


    /**
     * Func: descent
     * Desc: A sheaf in my program is composed of a collection of objects (like paragraphs or sentences).
     *       To perform descent, these objects are partitioned and a measure is assigned to them.
     *       The descentData class specifies the measure to be used (such as unigram frequency) and how the
     *       objects are partitioned (such as splitting a paragraph into sentences).
     *
     *       After the objects are split, they are now sheaves that we can call descend.
     *       It goes topics -> paragraphs -> sentences -> words.
     */
    fun descend(descentData: List<DescentData>)  {
        val (simFun, partitionFun) = descentData.firstOrNull() ?: return
        val leftovers = descentData.subList(1, descentData.size)

        if (partitions.isEmpty()) {
            println("There's a hole in $name, skipping...")
            println(cover!!.partitions)
            return
        }

        // Evaluate frequencies for each of the partitions
        val (partitionSims, partitionTexts) = partitions.mapIndexed { index, text ->
            val pname = "${name}_$index"
            (pname to simFun(text)) to (pname to text) }.unzip()

        // Evaluate total frequency of all partitions
        val coveringSim = partitionSims.flatMap { partition -> partition.second.entries }
            .groupingBy { it.key }
            .fold(0.0) { acc, entry -> acc!! + entry.value }
            .normalize()


        // Run the perturbation method a few times and average the results.
        // We are trying to find the best linear combination of partitions that describes the total
        // frequency of all partitions.
        val (featureNames, weights) = (0 until 3).map {
            doPerturb(coveringSim, partitionSims = partitionSims)}
            .reduce { acc, list -> acc.zip(list).map { (f1, f2) -> f1.first to f2.second + f1.second } }
            .map { it.first to it.second / 3 }.unzip()

        val results = featureNames.zip(weights).toMap()
        val partitionTextMap = partitionTexts.toMap()

        // Report results (not neccessary)
        val mixture =  TopicMixtureResult(results.toSortedMap(), kld)
        mixture.reportResults()

        // For each of the partitions with a non-zero coefficient (i.e. they are somehow important)
        // we perform descent on the partition.
        partitionSims.mapNotNull { (name, _) -> results[name]?.to(name) }
            .filter { it.first > 0.0 }
            .map { (freq, sheafName) ->
                val partitionText = partitionTextMap[sheafName]!!
                // Use descent data's partition function to split this partition
                val newPartitions = partitionFun?.invoke(partitionText) ?: emptyList()
                if (newPartitions.isEmpty()) { println("$sheafName") }

                // After creating the new sheaf, keep track of it in the measure HashMap.
                // This will end up looking like a distribution over distributions over... etc.
                measure[sheafName] = Sheaf(sheafName, newPartitions, kld, this) to freq
            }

        // Descent on our sheaves (while we have descent data remaining)
        measure.forEach { (_, sheafMeasure) ->
            val sheaf = sheafMeasure.first
            sheaf.descend(leftovers)
        }

    }

    /**
     * Func: doPerturb
     * Desc: Perturb covering distribution by additive gaussian noise. Generate 100 perturbed versions this way.
     *       This is then used to find the best mixture of partitions that describe the cover.
     */
    fun doPerturb(coveringSim: Map<String, Double>, partitionSims: List<Pair<String, Map<String, Double>>>): List<Pair<String, Double>> {
        val perturbations = perturb(100, coveringSim)
        val integrator = KotlinStochasticIntegrator(perturbations, partitionSims + (name to coveringSim), {null}, false)
        val integrals = integrator.integrate()

        val identityFreq = integrals.find { it.first == name }!!.second
        val (featureNames, featureFreqs) = integrals.filter { it.first != name }.unzip()

        val stepper = GradientDescenter(identityFreq, featureFreqs)
        val (weights, kld) = stepper.startDescent(800)
        return featureNames.zip(weights)
    }


    /**
     * Func: measurePartition
     * Desc: Given some measure of similarity to text, average the values obtained over partitions in a sheaf.
     *       This is divided by the number of partitions in the sheaf's parent (cover).
     */
    fun measurePartitions(simFun: (String) -> Double): Double  =
            partitions.map { partition -> simFun(partition) }.average()!!.defaultWhenNotFinite(0.00) *
                    (1 / log2(cover!!.partitions.size.toDouble())).defaultWhenNotFinite(0.0)


    /**
     * Func: transferDown
     * Desc: Given a function that measures similarity to strings, descent down to the "leaves" of this hierarchy
     *       and apply the similarity measure. Then go backwards, bringing the measure to the top.
     */
    fun transferDown(depthToGo: Int, simFun: (String) -> Double,
                     ascentType: AscentType = AscentType.ASCENT_SUM): Double {

        // We are at the desired level to measure similarities, so do this for the partitions
        if (depthToGo == 0) return measurePartitions(simFun)


        // We're not at the desired level, so we recursively apply transferDown to our children. This returns a
        // similarity score which we multiply by how "important" our children are (the coefficients obtained by
        // doing the perturbation embedding method).
        val results = measure.values.map { (sheaf, freq) ->
            if (freq < 0.1) 0.0 // This is hacky: I can't afford to descend on all the branches... too slow!
            else sheaf.transferDown(depthToGo - 1, simFun) * freq.defaultWhenNotFinite(1.0)
        }

        val total = results.sum()
        val maximum = results.max() ?: 0.0

        // Now that we've found the total scores of our children and the maximum score of our children, determine
        // how we should backpropagate the similarity score to our cover (parent).
        return when (ascentType) {
            AscentType.ASCENT_SUM -> total
            AscentType.ASCENT_MAX -> maximum
            AscentType.ASCENT_THRESHOLD_MAX -> if (maximum < 1/(max(1.0, partitions.size.toDouble())))
                                               return 0.00 else return maximum
            AscentType.ASCENT_THRESHOLD_SUM -> if (total < 1/(max(1.0, partitions.size.toDouble())))
                                               return 0.00 else return total
        }

    }


    /**
     * Func: retrieveLayer
     * Desc: Used for visualization: retrieves all of the sub-sheaves of a topic at a particular level.
     * Level 1 = paragraph sheaves, level 2 = sentence sheaves, level 3 = word sheaves
     */
    fun retrieveLayer(depth: Int): List<Sheaf> =
        if (depth == 0) listOf(this)
        else measure.values.flatMap { (sheaf, _) -> sheaf.retrieveLayer(depth - 1) }

    companion object {
        fun perturb(nSamples: Int = 50, sims: Map<String, Double>): Pair<List<String>, List<List<Double>>> {
            val norm = NormalDistribution(sharedRand, 1.0, 0.001)
            val (kernelNames, kernelFreqs) = sims.toList().unzip()

            val perturbations = (0 until nSamples).map {
                norm.sample(kernelFreqs.size)
                    .toList()
                    .zip(kernelFreqs)
                    .map { (gaussian, kernelFreq) -> gaussian + kernelFreq  }.normalize() }

            return kernelNames to perturbations
        }

    }
}


/**
 * Class: KotlinMetaKernelAnalzyer
 * Desc: Poor choice of naming, but it's too late now to refactor... Anyway, this is used to deconstruct
 *       topics (collections of abstracts / paragraphs) into paragraphs, sentences, and words.
 */
class KotlinMetaKernelAnalyzer(val paragraphIndex: String) {
    val sheaves = arrayListOf<Sheaf>()
    private val sim = NormalizedLevenshtein()

    /**
     * Func: unigramFreq
     * Desc: Simple measure of unigram frequency given a text.
     */
    fun unigramFreq(text: String): Map<String, Double> =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            .groupingBy(::identity)
            .eachCount()
            .normalize()

    /**
     * Func: bigramFreq
     * Desc: Simple measure of bigra frequency given a text.
     */
    fun bigramFrequency(text: String): Map<String, Double> =
            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                .windowed(2, partialWindows = false)
                .flatMap { (first, second) -> listOf("${first}_$second", "${second}_$first") }
                .groupingBy(::identity)
                .eachCount()
                .normalize()


    /**
     * Func: splitSentence
     * Desc: Convenience function used to partition paragraphs into sentences.
     */
    fun splitSentence(text: String): List<String> = text.split(".")
        .filter { it.length > 2 }


    /**
     * Func: splitWord
     * Desc: Convenience function used to partition sentences into words.
     */
    fun splitWord(text: String): List<String> =
//            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            "[ ]".toRegex().split(text)
                .filter { it.length > 2 }
                .toSet()
                .toList()



    /**
     * Func: letterFreq
     * Desc: Used to generated k-mer frequencies.
     */
    fun letterFreq(windowSize: Int, text: String, partial: Boolean) =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
            .flatMap { token -> token.windowed(windowSize, partialWindows = partial) }
            .groupingBy(::identity)
            .eachCount()
            .normalize()


    // Wrapper around letterFreq to specify a specific kmer.
    fun bindFreq(windowSize: Int, partial: Boolean = false) = { text: String -> letterFreq(windowSize, text, partial)}


    /**
     * Func: evaluateMeasure
     * Desc: Given a measure of similarity to words, derive a measure of similarity to topics (also a distribution)
     */
    fun evaluateMeasure(startingLayer: Int, measureLayer: Int, measure: (String) -> Double, filterList: List<String>,
                        ascentType: AscentType) =
            extractSheaves(startingLayer)
                .filter { (topName, _) -> filterList.isEmpty() || topName in filterList }
                .flatMap { (topName, sheafLayer) ->
                    sheafLayer.map { sheaf ->
                        sheaf.name to sheaf.transferDown(measureLayer - startingLayer, measure, ascentType) } }


    /**
     * Func: trainParagraphs
     * Desc: Takes paragraphs belonging to topic models and performs the hierarchical decomposition described
     *       previously. The results are stored in descent_data/ where the sheaves are serialized.
     */
    private fun trainParagraph(topic: String, directory: File) {
        val paragraphs = directory.listFiles().map { file -> file.readText().toLowerCase().replace(",", " ") }
            .map { text -> splitSentence(text).map {  text ->
                AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED).joinToString(" ") }
                .joinToString(". ")
            }

        val sheaf = Sheaf(topic, paragraphs)

        // Describes how we should deconstruct everything
        // Topic Level: decompose into paragraphs and find distribution over sentences that best explains 2-mers
        // Paragraph Level: decompose into sentences and find distribution over words that best explains 2-mers
        // Sentence Level: decompose into words and find distribution over words that best explains 2-mers.
        val descentData = listOf(
                DescentData(bindFreq(2), this::splitSentence),
                DescentData(bindFreq(2), this::splitWord),
                DescentData(bindFreq(2), ::listOf)
        )
        sheaf.descend(descentData)
        File("descent_data/").let { file -> if (!file.exists()) file.mkdir() }
        val f = FileOutputStream("descent_data/$topic")
        val of = ObjectOutputStream(f)
        of.writeObject(sheaf)
        of.close()
        f.close()


    }


    /**
     * Func: extractSheaves
     * Desc: For a list of topic sheaves, return sub-sheaves of a given level.
     */
    fun extractSheaves(level: Int) =
        sheaves.map { sheaf -> sheaf.name to sheaf.retrieveLayer(level) }

    /**
     * @see trainParagraph
     */
    fun trainParagraphs(filterList: List<String> = emptyList()) {
        File(paragraphIndex)
            .listFiles()
            .filter { file -> file.isDirectory && (filterList.isEmpty() || file.name in filterList)  }
            .forEach { file -> trainParagraph(file.name, file) }
    }


    /**
     * Func: loadSheaves
     * Desc: Loads serialized sheaves (in descent_data/ directory)
     */
    fun loadSheaves(sheafIndex: String, filterWords: List<String> = emptyList()) =
            File(sheafIndex)
                .listFiles()
                .filter { file -> filterWords.isEmpty() || file.name in filterWords }
                .map { file ->
                    val reader = ObjectInputStream(FileInputStream(file))
                    reader.readObject() as Sheaf}
                .onEach { sheaf -> sheaves += sheaf }


    /**
     * Desc: inferMetric
     * Desc: Big wrapper around some of the other functions. The goal is to call evaluate measure
     *       to get similarities between queries/paragraphs to topics and then normalize this to a distribution.
     *       However, my methods explore variations of this which is why there are so many arguments.
     */
    fun inferMetric(text: String, startingLayer: Int, measureLayer: Int,
                    doNormalize: Boolean = true,
                    reductionMethod: ReductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    ascentType: AscentType = AscentType.ASCENT_SUM,
                    filterList: List<String> = emptyList()): TopicMixtureResult {

        val mySentence = bindSims(text, reductionMethod = reductionMethod)
        val res = evaluateMeasure(startingLayer, measureLayer, mySentence, filterList, ascentType = ascentType)
            .toMap()
            .run { if (doNormalize) normalize() else this }

        return TopicMixtureResult(res.toSortedMap(), 0.0)
    }


    /**
     * Func: averageSim
     * Desc: Ugh, sorry, the name no longer makes sense. Need to refactor.
     *       This is just calling NormalizedLevenshtein and applying something like a heavyside step function
     *       to it.
     */
    fun averageSim(w1: String, w2: String): Double =
                (1.0 - sim.distance(w1, w2)).run { if (this < 0.8) 0.0 else this }


    /**
     * Func: productMaxMax
     * Desc: Takes the maximum similarity from words to words.
     */
    fun productMaxMax(w1: List<String>, w2: List<String>): Double =
            w1.map { word1 -> w2.map { word2 -> averageSim(word1, word2) }.max()!! }.max()!!


    /**
     * Func: productSmooththreshold
     * Desc: Wow I named this poorly. Anyway, it just gives more weight to small queries/paragraphs.
     */
    fun productSmoothThreshold(w1: List<String>, w2: String): Double {
        val results = w1.map { word1 -> averageSim(word1, w2) }
        val misses = results.count { it == 0.0 }
        val hits = results.sum()
        val sizeSmooth = 1.0 + 200 / (1.0 + misses.toDouble())

        return hits * sizeSmooth

    }

    /**
     * Func: productAverage
     * Desc: Takes the average similarity value to words.
     */
    fun productAverage(w1: List<String>, w2: List<String>): Double =
            w1.flatMap { word1 -> w2.map { word2 -> averageSim(word1, word2) } }.average()



    fun bindSims(text: String, reductionMethod: ReductionMethod): (String) -> Double {
        val w1 = filterWords(text)
        return { w2 ->
            when (reductionMethod) {
                ReductionMethod.REDUCTION_MAX_MAX            -> productMaxMax(w1, listOf(w2))
                ReductionMethod.REDUCTION_AVERAGE            -> productAverage(w1, listOf(w2))
                ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD -> productSmoothThreshold(w1, w2)
            }
        }
    }

}


fun filterWords(text: String) =
    AnalyzerFunctions.createTokenList(text.toLowerCase(),
            analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)



// Ignore these: they are important for my tests but not for generating runfiles
fun testStuff2(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
//    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
    val text = """
        Instead of table service, there are food-serving counters/stalls, either in a line or allowing arbitrary walking paths. Customers take the food that they desire as they walk along, placing it on a tray. In addition, there are often stations where customers order food and wait while it is prepared, particularly for items such as hamburgers or tacos which must be served hot and can be immediately prepared. Alternatively, the patron is given a number and the item is brought to their table. For some food items and drinks, such as sodas, water, or the like, customers collect an empty container, pay at the check-out, and fill the container after the check-out. Free unlimited second servings are often allowed under this system. For legal purposes (and the consumption patterns of customers), this system is rarely, if at all, used for alcoholic beverages in the US.
            """

    val bb = """
        food
    """
    val red = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD
    val ascentType = AscentType.ASCENT_THRESHOLD_SUM
    val (time, result) = withTime {
        metaAnalyzer.inferMetric(text, 0, 3, doNormalize = true, reductionMethod = red,
                ascentType = ascentType) }
    val result2 = metaAnalyzer.inferMetric(bb, 0, 3, doNormalize = true, reductionMethod = red,
            ascentType = ascentType)

    result.reportResults()
    result2.reportResults()
    println(result.manhattenDistance(result2))
    println("TIME: $time")
}


fun showSheaves(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
    val res = sheaves
        .filter { sheaf -> sheaf.name == "Cuisine" }
        .map { sheaf ->
        sheaf.retrieveLayer(3)
            .map { s ->
                val parentScore = s.cover?.run { measure[s.name]!!.second }.toString()
                s.partitions.joinToString(" ") + ":\n\t$parentScore\n"  }
            .joinToString("\n")
        }

    println(res)

}



fun main(args: Array<String>) {
    val metaAnalyzer = KotlinMetaKernelAnalyzer("paragraphs/")
//    metaAnalyzer.trainParagraphs()
    testStuff2(metaAnalyzer)
}