@file:JvmName("KotEntityLinker")
package edu.unh.cs980

import org.jsoup.Jsoup
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.ThreadLocalRandom


/**
 * Class: retrieveEntities
 * Description: Queries spotlight server with string and retrieve list of linked entities.
 * @return List of linked entities (strings). Empty if no entities were linked or if errors were encountered.
 */
class KotlinEntityLinker(serverLocation: String) {
    val url = "http://localhost:9310/jsr-spotlight/annotate"        // Hardcoded url to local server


    // Start up server (can take a while if we need to download files
    val server = KotlinSpotlightRunner(serverLocation)


    /**
     * Function: retrieveEntities
     * Description: Queries spotlight server with string and retrieve list of linked entities.
     * @return List of linked entities (strings). Empty if no entities were linked or if errors were encountered.
     */
    fun retrieveEntities(content: String): List<String> {

        // Retrieve html file from the Spotlight server
        val jsoupDoc = Jsoup.connect(url)
                .data("text", content)
                .post()

        // Parse urls, returning only the last word of the url (after the last /)
        val links = jsoupDoc.select("a[href]")
        return links.map {  element ->
                            val title = element.attr("title")
                        title.substring(title.lastIndexOf("/") + 1)}
                    .toList()
    }


    /**
     * Function: queryServer
     * Description: Wrapper around retrieveEntities to handle timeouts (which seem to be possible for this server).
     *              Because of Socket Timeout Exceptions, will try three times before giving up.
     * @return List of entities (if any) linked by retrieveEntities function.
     */
    fun queryServer(content: String): List<String> {
        var entities = ArrayList<String>() as List<String>

        // Try three times to query server before giving up
        for (i in (0..3)) {
              try { entities = retrieveEntities(content); break
            } catch (e: SocketTimeoutException) { Thread.sleep(ThreadLocalRandom.current().nextLong(500))
            } catch (e: ConnectException) { Thread.sleep(ThreadLocalRandom.current().nextLong(500)) } catch (e: ConnectException) { Thread.sleep(ThreadLocalRandom.current().nextLong(500))
            } catch (e: IOException) { Thread.sleep(ThreadLocalRandom.current().nextLong(500))}
        }
        return entities
    }
}
