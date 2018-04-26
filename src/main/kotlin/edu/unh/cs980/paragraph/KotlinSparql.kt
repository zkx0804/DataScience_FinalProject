package edu.unh.cs980.paragraph

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.jsoup.Jsoup
import khttp.post
import java.io.File
import kotlin.system.exitProcess


/**
 * Singleton: KotlinSparql
 * Desc: Contains convenience functions for downloading abstracts/pages using SPARQL
 */
object KotlinSparql {

    // List of abstract topics to download using sparql
    private val topics = listOf(
            "Biology",
            "Computers",
            "Cooking",
            "Cuisine",
            "Engineering",
            "Environments",
            "Events",
            "Fashion",
            "Games",
            "Mathematics",
            "Medicine",
            "Organizations",
            "People",
            "Politics",
            "Science",
            "Society",
            "Statistics",
            "Technology",
            "Tools",
            "Travel",
            "Warfare"
    )

    private val pageTopics = listOf(
            "Biology",
            "Medicine",
            "Science"
    )


    /**
     * Func: abstractTemplate
     * Desc: Used to query a SPARQL endpoint and retrieve a list of abstracts.
     *       The vrank resource assigns a pagerank-esque score to each of the pages.
     *       Using this, the top 50 page abstracts (according to vrank) are retrieved.
     *       Pages are retrieved based on their relation to a category (entity string here).
     */
    fun abstractTemplate(entity: String): String {
        val sparTemp = """
            PREFIX vrank:<http://purl.org/voc/vrank#>
            SELECT distinct ?abstract
            FROM <http://people.aifb.kit.edu/ath/#DBpedia_PageRank>
            FROM <http://dbpedia.org>
            WHERE {
                 ?related skos:broader dbc:$entity .
                 {?entity  dct:subject/dbc:subClassOf* ?related } UNION {?entity dct:subject dbc:$entity } .
                 ?entity dbo:abstract ?abstract .
                 filter langMatches(lang(?abstract),"en") .
                 filter (strlen(str(?abstract)) > 500) .
                 ?entity vrank:hasRank/vrank:rankValue ?v .
                  }
            ORDER BY DESC(?v) LIMIT 50
            """
        return sparTemp
    }

    /**
     * Func: linkTemplates
     * Desc: As above, but the links to the pages are retrieved instead.
     */
    fun linkTemplate(entity: String): String {
        val sparTemp = """
            PREFIX vrank:<http://purl.org/voc/vrank#>
            SELECT distinct ?link
            FROM <http://people.aifb.kit.edu/ath/#DBpedia_PageRank>
            FROM <http://dbpedia.org>
            WHERE {
                 ?related skos:broader dbc:$entity .
                 {?entity  dct:subject/dbc:subClassOf* ?related } UNION {?entity dct:subject dbc:$entity } .
                 ?entity dbo:abstract ?abstract .
                 ?entity prov:wasDerivedFrom ?link .
                 filter langMatches(lang(?abstract),"en") .
                 filter (strlen(str(?abstract)) > 500) .
                 ?entity vrank:hasRank/vrank:rankValue ?v .
                  }
            ORDER BY DESC(?v) LIMIT 10
            """
        return sparTemp
    }


    /**
     * Func: doSearch
     * Desc: Query SPARQL endpoint with template and retrieve abstracts.
     */
    fun doSearch(entity: String): List<String> {
        val doc = Jsoup.connect("http://dbpedia.org/sparql")
            .data("query", abstractTemplate(entity))
            .post()

        val elements = doc.getElementsByTag("pre")
        return elements.map { element -> element.text() }
    }

    /**
     * Func: doSearchPage
     * Desc: Query SPARQL endpoint with template and retrieve links to pages
     */
    fun doSearchPage(entity: String): List<String> {
        val doc = Jsoup.connect("http://dbpedia.org/sparql")
            .data("query", linkTemplate(entity))
            .post()

        val elements = doc.getElementsByTag("td")
        return elements.map { element -> element.text() }
    }


    /**
     * Func: writeResults
     * Desc: Writes SPARQL query results to a directory. Each subdirectory is the topic that these results are
     *       relates to (such as Medicine or Computers)
     */
    fun writeResults(category: String, results: List<String>, folderName: String) {
        val outDir = "$folderName/$category/"
        File(outDir).apply { if (!exists()) mkdirs() }

        results.forEachIndexed { index, doc ->
            File("${outDir}doc_$index.txt").writeText(doc.take(doc.length - 3).replace("\"", ""))
        }
    }


    /**
     * Func: getWikiText
     * Desc: Retrieves and cleans text from Wikipedia using the provided URL.
     */
    fun getWikiText(url: String): String {
        val doc = Jsoup.connect(url).get()
        val paragraphs = doc.select(".mw-content-ltr p")
        return paragraphs.map { p -> p.text() }.joinToString(" ")
    }


    /**
     * Func: extractCategoryAbstract
     * Desc: Using predefined topics, download abstracts from SPARQL and store them in subdirectories
     *       corresponding to the topics they were related to.
     */
    fun extractCategoryAbstracts() {
        topics.forEach { topic ->
            val results = doSearch(topic)
            writeResults(topic, results, "paragraphs")
        }
    }


    /**
     * Func: extractCategoryAbstract
     * Desc: Using predefined topics, download links from SPARQL, retrieves Wikipedia pages from the links,
     * and store them in subdirectories corresponding to the topics they were related to.
     */
    fun extractWikiPages() {
        pageTopics.forEach { topic ->
            val results = doSearchPage(topic).map { link -> getWikiText(link) }
            writeResults(topic, results, "pages")
        }
    }

}
