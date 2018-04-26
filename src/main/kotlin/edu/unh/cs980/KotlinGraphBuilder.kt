@file:JvmName("KotGraphBuilder")
package edu.unh.cs980

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.MultiFields
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.experimental.buildSequence


/**
 * Class: KotlinGraphBuilder
 * Description: Uses supplied Lucene index to generate a bipartite graph with edges between entities and paragraphs.
 *              The results are stored in a hashmap file (graph_database.db) for later use.
 */
class KotlinGraphBuilder(indexLocation: String) {

    // Start up an index searcher using supplied path to Lucene index directory
    val indexSearcher = kotlin.run {
        val indexPath = Paths.get (indexLocation)
        val indexDir = FSDirectory.open(indexPath)
        val indexReader = DirectoryReader.open(indexDir)
        IndexSearcher(indexReader)
    }

    // Open up database where we will be storing graphs
    val db = KotlinDatabase("graph_database.db")
    val graphAnalyzer = KotlinGraphAnalyzer(indexSearcher, db)


    /**
     * Function: buildParagraphGraph
     * Description: Iterates over paragraphs in index and adds entities as edges to paragraph.
     *              The results are stored in parMap ("par_map" in the database)
     */
    fun buildParagraphGraph() {
        println("Adding edges from paragraphs to entities")
        val maxDoc = indexSearcher.indexReader.numDocs()

        val bar = ProgressBar("Paragraphs Added", maxDoc.toLong(),
                ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()

        // Iterate over each paragraph, storing its entities in a map
        (0 until maxDoc).forEachParallel { docId ->
            val doc = indexSearcher.doc(docId)
            val paragraphid = doc.get(PID)
            val entities = doc.getValues("spotlight")
            db.parMap[paragraphid] = entities.joinToString(separator = " ")
            lock.withLock { bar.step() }
        }
        bar.stop()
    }


    /**
     * Function: addEntitiesToGraph
     * Description: Iterates over list of entities in index and queries Lucene for paragraphs that contain this entity.
     *              Edges are then added from this entity to the paragraphs that contain it.
     *              The results are stored in entityMap ("entity_map" in the database)
     */
    fun addEntitiesToGraph(entities: List<String>) {
        entities.forEachParallel { entity ->
            val termQuery = TermQuery(Term("spotlight", entity))
            val topDocs = indexSearcher.search(termQuery, 10000)

            val parEdges = topDocs.scoreDocs.joinToString(separator = " ") { scoreDoc ->
                indexSearcher.doc(scoreDoc.doc).get(PID) }
            db.entityMap[entity] = parEdges
        }
    }

    /**
     * Function: buildEntityGraph
     * Description: Builds edges from entities to paragraphs. Uses Lucene index to iterate over spotlight terms and then
     *              processes them in chunks of 10000.
     * @see addEntitiesToGraph
     */
    fun buildEntityGraph() {
        println("Adding edges from entities to paragraphs")
        val fields = MultiFields.getFields(indexSearcher.indexReader)
        val spotLightTerms = fields.terms("spotlight")
        val numTerms = 2100000 // Hard coding number of entities for progress bar (no easy way to count this)
        val termIterator = spotLightTerms.iterator()

        // Build a sequence that lets us iterate over terms in chunks and run them in parallel
        val termSeq = buildSequence<String> {
            while (true) {
                val bytesRef = termIterator.next() ?: break
                yield(bytesRef.utf8ToString())
            }
        }

        // Create a progress bar that keeps track of entities that are added
        val bar = ProgressBar("Entities Added", numTerms.toLong(), ProgressBarStyle.ASCII)
        bar.start()
        val lock = ReentrantLock()

        // Chunks entities in groups of 10000 and adds them to graph
        termSeq.chunked(10000)
                .forEach { chunk ->
                    addEntitiesToGraph(chunk)
                    lock.withLock { bar.stepBy(10000) }  // Have to make sure update is thread-safe
                }

        bar.stop()
    }

    /**
     * Function: run
     * Description: Builds edges from paragraphs to entities and then from entities to paragraphs.
     * @see buildEntityGraph
     * @see buildParagraphGraph
     */
    fun run() {
        buildParagraphGraph()
        buildEntityGraph()
        println("Graphs complete!")
    }
}

