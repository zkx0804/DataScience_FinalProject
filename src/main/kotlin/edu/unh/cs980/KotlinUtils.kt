@file:JvmName("KotUtils")
package edu.unh.cs980

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import org.apache.commons.math3.random.JDKRandomGenerator
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.similarities.BM25Similarity
import org.apache.lucene.store.FSDirectory
import java.lang.Math.abs
import java.lang.Math.pow
import java.nio.file.Paths
import java.util.*
import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

// Conditional versions of run/let/apply/also
fun <T,R> T.runIf(condition: Boolean, block: T.() -> R): R? = if (condition)  run(block)  else null
fun <T,R> T.runIf(condition: T.() -> Boolean, block: T.() -> R): R? = if (condition())  run(block)  else null

fun <T,R> T.letIf(condition: Boolean, block: (T) -> R): R? = if (condition)  let(block)  else null
fun <T,R> T.letIf(condition: T.() -> Boolean, block: (T) -> R): R? = if (condition())  let(block)  else null

fun <T> T.applyIf(condition: Boolean, block: T.() -> Unit): T = if (condition)  apply(block)  else this
fun <T> T.applyIf(condition: T.() -> Boolean, block: T.() -> Unit): T = if (condition())  apply(block)  else this

fun <T> T.alsoIf(condition: Boolean, block: (T) -> Unit): T = if (condition) also(block) else this
fun <T> T.alsoIf(condition: T.() -> Boolean, block: (T) -> Unit): T = if (condition()) also(block) else this


// Parallel versions of map/forEach methods.
// See: https://stackoverflow.com/questions/45575516/kotlin-process-collection-in-parallel
fun <A, B>Iterable<A>.pmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(CommonPool) { f(it) } }.map { it.await() }
}

fun <A, B>Iterable<A>.pmapRestricted(nThreads: Int = 10, f: suspend (A) -> B): List<B> = runBlocking {
    val pool = newFixedThreadPoolContext(nThreads, "parallel")
    map { async(pool) { f(it) } }.map { it.await() }
}


fun <A>Iterable<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
    map { async(CommonPool) { f(it) } }.forEach { it.await() }
}

fun <A>Iterable<A>.forEachParallelRestricted(nThreads: Int = 10, f: suspend (A) -> Unit): Unit = runBlocking {
    val pool = newFixedThreadPoolContext(nThreads, "parallel")
    map { async(pool) { f(it) } }.forEach { it.await() }
}

fun <A, B, C>Iterable<A>.accumMap(keyFun: (A) -> C, f: (B?, A) -> B): List<Pair<C, B>> {
    var init: B? = null
    return map { element ->
        val key = keyFun(element)
        val result = f(init, element)
        init = result
        key to result
    }
}


fun <A, B: Number>Map<A, B>.normalize(): Map<A, Double> {
    val total = values.sumByDouble { it.toDouble() }
    return mapValues { (_, value) -> (value.toDouble() / total).defaultWhenNotFinite(0.0) }
}


fun Iterable<Double>.smooth2()  =
    windowed(2, 1, false)
        .map { window -> window.average() }
//        .map { window -> abs(window[0] * window[1])  }
        .run {
            val total = sum()
            map { averagedValue -> averagedValue / total }
        }

fun Iterable<Double>.smooth5()  =
        windowed(3, 1, false)
            .map { window -> abs(window[0] * window[1] * window[2]) / (window[0] + window[1] + window[2])    }
            .run {
                val total = sum()
                map { averagedValue -> averagedValue / total }
            }

fun Iterable<Double>.smooth6()  =
        windowed(2, 1, false)
            .map { window -> abs(window[0] * window[1]) / (window[0] + window[1])    }
            .run {
                val total = sum()
                map { averagedValue -> averagedValue / total }
            }

fun Iterable<Double>.smooth3(): List<Double> {
    val items = toList()
    val product = items.flatMap { first ->
        items.map { second ->
            (first * second) / (first + second)
        } }
        .chunked(items.size)
        .map { chunk -> chunk.average() }
//        .shuffled(sharedRand)
////        .mapIndexed { index, chunk -> 0.5 * chunk + 0.5 * items[index] }
//        .mapIndexed { index, chunk -> chunk     }
        .mapIndexed { index, chunk -> 0.5 * chunk * items[index] + items[index] * 0.5    }

    return product
//    val total = product.sum()
//    return product.map { it / total }
}

fun Iterable<Double>.smooth(): List<Double>  {
    val items = toList()
    val mean = items.average()
    val variantMap = items.map {abs(it - mean)}
    val total = variantMap.sum()
//    return variantMap.map {value -> value / total}
    return items.smooth2()

}

fun Iterable<Double>.normalize(): List<Double> {
    val items = toList()
    val total = items.sum()
    if (total == 0.0) return items.map { value -> 0.0 }
    return items.map { value -> value / total }
}



//fun <A>Sequence<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
//    forEach { async(CommonPool) { f(it) }.await() }
//}

fun<A> withTime(f: () -> A): Pair<Long, A> {
    var result: A? = null
    val time = measureTimeMillis { result = f() }
    return time to result!!
}


// Map Extensions
fun <K,V>MutableMap<K,V>.removeAll(f: (key:K,value:V) -> Boolean) {
    this.entries
        .filter{(key,value) -> f(key,value)}
        .forEach { (key,_) ->
            remove(key)
        }
}

// Retrieves an index searcher (I use this everywhere so might as well put it here)
fun getIndexSearcher(indexLocation: String): IndexSearcher {
    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val indexReader = DirectoryReader.open(indexDir)
    return IndexSearcher(indexReader)
}

fun getIndexWriter(indexLocation: String): IndexWriter {
    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val conf = IndexWriterConfig(StandardAnalyzer())
        .apply { openMode = IndexWriterConfig.OpenMode.CREATE }
    return IndexWriter(indexDir, conf)
}


// Constants referring to Lucene fields
public const val PID: String = "paragraphid"
public const val CONTENT = "text"


// I don't know why the hell they don't have an identity function..
fun <A> identity(it: A): A = it

fun Double.defaultWhenNotFinite(default: Double = 0.0): Double = if (!isFinite()) default else this

//val sharedRand = JDKRandomGenerator(12941)
//val sharedRand = JDKRandomGenerator(9914)
//val sharedRand = JDKRandomGenerator(1999138)
val sharedRand = JDKRandomGenerator()
