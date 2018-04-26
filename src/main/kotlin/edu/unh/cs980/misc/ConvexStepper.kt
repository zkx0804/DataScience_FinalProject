package edu.unh.cs980.misc

import edu.unh.cs980.features.featSDMWithEntityQueryExpansion
import edu.unh.cs980.normalize
import edu.unh.cs980.sharedRand
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.geometry.Vector
import smile.math.matrix.Matrix
import smile.math.*
import smile.math.Math.*
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import smile.math.matrix.*
import smile.regression.LASSO
import smile.wavelet.*
import smile.regression.OLS
import smile.regression.RidgeRegression
import kotlin.math.sign
import kotlin.system.exitProcess




// Ignore this (I will revisit it later, but it's a partitioned version of my GradientDescender)
data class Partition(val origin: DoubleArray, val topics: List<DenseMatrix>) {
    fun kld(weightMatrices: List<DenseMatrix>): Double =
        weightMatrices.zip(topics)
            .map { (w,t) -> w.transpose().mul(t) }
            .reduce { acc, denseMatrix -> acc.add(denseMatrix)  }
            .run { div(sum()) }
            .transpose().array()
            .run { KullbackLeiblerDivergence(origin, this.first())}
}

// Ignore this (I will revisit it later, but it's a partitioned version of my GradientDescender)
class PartitionContainer(val partitions: List<Partition>)  {
    var curStep = 0

    fun step() = (curStep + 1)  % partitions.size
//    fun step() = sharedRand.nextInt(partitions.size)

    fun kld(weightMatrices: List<DenseMatrix>): Double = partitions[curStep].kld(weightMatrices)


    companion object {
        fun createPartitionContainer(origin: List<Double>,
                                     topics: List<List<Double>>, partitionSize: Int = 100): PartitionContainer {
            val nPartitions = origin.size / partitionSize
            val partitions = (0 until nPartitions).map {  part ->
                val originPartition = origin.subList(part, part + partitionSize).normalize().toDoubleArray()
                val topicPartitions = topics.map { topic ->
                    Matrix.newInstance(topic.subList(part, part + partitionSize).normalize().toDoubleArray()) }

                Partition(originPartition, topicPartitions)
            }

            return PartitionContainer(partitions)
        }
    }
}


// Ignore this (I will revisit it later, but it's a partitioned version of my GradientDescender)
class PartitionDescenter(origin: List<Double>, topics: List<List<Double>>, partitionSize: Int = 500) {
    val partitionContainer = PartitionContainer.createPartitionContainer(origin, topics, partitionSize)
    val weightMatrices =
            (0 until topics.size).map { Matrix.newInstance(1, partitionSize, 0.1) }


    val weightHistory = (0 until topics.size).map { ArrayList<Double>() }

    fun changeWeight(index: Int, weight: Double) =
            weightMatrices[index].mul(0.0).add(weight)

    fun doKld() =
            partitionContainer.kld(weightMatrices)




    fun getDerivative(baseline: Double, index: Int, curWeight: Double): Pair<Double, Double> {
        if (curWeight <= 0.0) return 0.0 to 0.0

        changeWeight(index, curWeight - 0.001)
        val lowerDiff = (doKld() - baseline)

        changeWeight(index, curWeight + 0.001)
        val upperDiff = (doKld() - baseline)
        changeWeight(index, curWeight)

        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
    }

    fun updateWeightAgainstGradient(weights: List<Double>): List<Double> {
        weights.mapIndexed { index, weight -> changeWeight(index, weight)}
        val baseline = doKld()
        val gradient = (0 until weights.size).map { index -> getDerivative(baseline, index, weights[index])}
        val total = gradient.sumByDouble { (_, delta) -> delta }
        if (total <= 0.0) return weights

        return weights.mapIndexed { index, weight ->
            val delta = gradient[index]
            if (weight <= 0.0) 0.0 else
                max(0.0, weight + delta.first * (delta.second / total) * 0.01)

        }
    }


    fun startDescent(nTimes: Int): Pair<List<Double>, Double> {
        val nTopics = weightMatrices.size
        var weights = (0 until nTopics).map { 0.1  }.toList()

        (0 until nTimes).forEach { iter ->

            weights.mapIndexed { index, weight -> changeWeight(index, weight)}

            weights = updateWeightAgainstGradient(weights)
            partitionContainer.step()

        }
        val weightSum = weights.sum()
        val finalWeights = weights.map { value -> value / weightSum }
        return finalWeights to doKld()
    }

}


/**
 * Func: uniformSmoothing
 * Desc: The values of the target function (our paragraph to be embedded) are averaged out.
 *       It is almost always very close to the uniform distribution.
 */
fun uniformSmoothing(values: List<Double>): DoubleArray {
    val total = values.sum()
    val normal = total / values.size.toDouble()
    return (0 until values.size).map { normal }.toDoubleArray()
}


/**
 * Class: GradientDescenter
 * Desc: Performs gradients descent on KLD. Find the linear combination of distributions (topics) that
 *       minimizes KLD to the uniform distribution.
 */
class GradientDescenter(val origin: List<Double>, val topics: List<List<Double>>) {

    // Warning, lots of hacky shit below. I need to switch to using a real convex optimizer next time instead
    // of my poor approximation of gradient descent.

    val weightMatrices =
        (0 until topics.size).map { Matrix.newInstance(1, origin.size, 1.0) }

    val topicMatrices = topics.map { Matrix.newInstance(it.toDoubleArray())}
    val originArray = uniformSmoothing(origin)

    fun changeWeight(index: Int, weight: Double) =
        weightMatrices[index].mul(0.0).add(weight)

    fun euclid(array1: DoubleArray, array2: DoubleArray): Double =
        array1.zip(array2).sumByDouble { (a1, a2) -> pow(a1 - a2, 2.0) }
            .run { pow(this, 1/2.0) }

    fun doKld() =
        weightMatrices.zip(topicMatrices)
            .map { (w,t) -> w.transpose().mul(t) }
            .reduce { acc, denseMatrix -> acc.add(denseMatrix)  }
            .run { div(sum()) }
            .transpose().array()
            .run { KullbackLeiblerDivergence(originArray, this.first())}


    fun kld(weights: List<Double>): Double =
            origin.zip(mix(weights))
                .sumByDouble { (originDist, mixtureDist) ->
                    (originDist) * log2(originDist / mixtureDist) }


    private val mempty = (0 until topics.first().size).map { 0.0 }


    fun getDerivative(baseline: Double, index: Int, weights: List<Double>): Pair<Double, Double> {
        if (weights[index] <= 0.0) { return 0.0 to 0.0 }
        val curWeight = weights[index]

        val lower = weights.toMutableList().apply { set(index, curWeight - 0.001) }
        val upper = weights.toMutableList().apply { set(index, curWeight + 0.001) }
        val lowerDiff = (kld(lower) - baseline)
        val upperDiff = (kld(upper) - baseline)

        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
    }

    fun getDerivative2(baseline: Double, index: Int, curWeight: Double): Pair<Double, Double> {
        if (curWeight <= 0.0) return 0.0 to 0.0

        changeWeight(index, curWeight - 0.001)
        val lowerDiff = (doKld() - baseline)

        changeWeight(index, curWeight + 0.001)
        val upperDiff = (doKld() - baseline)
        changeWeight(index, curWeight)

        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
    }


    fun startDescent(nTimes: Int): Pair<List<Double>, Double> {
        var weights = (0 until topics.size).map { 0.1 }.toList()

        (0 until nTimes).forEach {
            weights.mapIndexed { index, weight -> changeWeight(index, weight) }
            val baseline = doKld()
            val gradient = (0 until topics.size).map { index -> getDerivative2(baseline, index, weights[index])}
            val total = gradient.sumByDouble { (_, delta) -> delta }

            if (total > 0.00000000000001) {
                weights = weights.mapIndexed { index, weight ->
                    val delta = gradient[index]
                    if (weight <= 0.0) 0.0 else
                        max(0.0, weight + delta.first * (delta.second / total) * 0.01)
                }
            }

        }
        val weightSum = weights.sum()
        val finalWeights = weights.map { value -> value / weightSum }
        return finalWeights to doKld()
    }


    private fun mix(weights: List<Double>): List<Double> {
        val weightsTotal = weights.sum()
        return topics.foldIndexed(mempty) { index, acc, list ->
            acc.zip(list).map { (v1, v2) -> v1 + v2 * weights[index] } }
            .map { mixValue -> mixValue / weightsTotal }
    }

}
