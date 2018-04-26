@file:JvmName("KotAbstractExtractor")
package edu.unh.cs980.language

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import edu.unh.cs980.forEachParallelRestricted
import edu.unh.cs980.getIndexWriter
import edu.unh.cs980.misc.AnalyzerFunctions
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import java.io.BufferedInputStream
import java.io.File
import java.lang.Integer.min
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH

/**
 * Class: KotlinAbstractExtractor
 * Desc: Parses the AllButBenchmark page corpus and creates abstracts for each page (the first few paragraphs).
 *      Stores results in a new "abstract" lucene index.
 */
class KotlinAbstractExtractor(filename: String) {
    val indexWriter = getIndexWriter(filename)

    /**
     * Func: iterWrapper
     * Desc: A really annoying fix to some memory leaks I was seeing. For some reason why I parallelize adding documents,
     *      the page contents were not being freed up properly. This lead to memory errors.
     *      This function wraps around the iterableAnnotations annotator and extracts its contents.
     *      Again, I don't know why this fixes the problem, but it does...
     */
    private fun iterWrapper(f: BufferedInputStream): Iterable<Pair<String, String>> {
        val iter = DeserializeData.iterableAnnotations(f).iterator()
        val iterWrapper = buildIterator<Pair<String, String>>() {
            while (iter.hasNext()) {
                val nextPage = iter.next()

                val name = nextPage.pageName.toLowerCase().replace(" ", "_")
                val content = nextPage.flatSectionPathsParagraphs()
                    .take(4)
                    .map { psection -> psection.paragraph.textOnly }
                    .joinToString(" ")

                yield(Pair(name, content))
            }
        }
        return Iterable { iterWrapper }
    }


    /**
     * Func: getAbstracts
     * Desc: Given corpus location, retrieves page names and abstracts (derived from first few paragraphs).
     *       Results are stored in the abstract index.
     */
    fun getAbstracts(filename: String) {
        val f = File(filename).inputStream().buffered(16 * 1024)
        val counter = AtomicInteger()

        iterWrapper(f)
            .forEachParallelRestricted(10) { (name, content) ->

                // This is just to keep track of how many pages we've parsed
                counter.incrementAndGet().let {
                    if (it % 100000 == 0) {
                        println(it)
                        indexWriter.commit()
                    }
                }

                val doc = Document()
                doc.add(TextField("name", name, Field.Store.YES))
                doc.add(TextField("text", content, Field.Store.YES))


                // Do some word stemming using english analyzer and then calculate -gram models
                val tokens = AnalyzerFunctions.createTokenList(content, ANALYZER_ENGLISH)
                val unigrams = ArrayList<String>()
                val bigrams = ArrayList<String>()
                val bigramWindows = ArrayList<String>()
                (0 until tokens.size).forEach { i ->
                    unigrams.add(tokens[i])
                    if (i < tokens.size - 1) {
                        bigrams.add(tokens[i] + tokens[i + 1])
                    }

                    ( i + 1 until min(i + 9, tokens.size)).forEach { j ->
                        bigramWindows.add(tokens[i] + tokens[j])
                        bigramWindows.add(tokens[j] + tokens[i])
                    }
                }
                doc.add(TextField("unigram", unigrams.joinToString(separator = " "), Field.Store.YES))
                doc.add(TextField("bigrams", bigrams.joinToString(separator = " "), Field.Store.YES))
                doc.add(TextField("bigram_windows", bigrams.joinToString(separator = " "), Field.Store.YES))
                indexWriter.addDocument(doc)
            }
        indexWriter.close()
    }
}


