@file:JvmName("KotSpotlightRunner")
package edu.unh.cs980

import org.apache.commons.io.FileUtils
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver
import org.codehaus.plexus.logging.console.ConsoleLoggerManager
import java.io.File
import java.net.URL


/**
 * Class: KotlinSpotlightRunner
 * Description: Starts a local instance of a Spotlight server and runs it. If the required files are unavailable, it
 * will download them remotely first.
 */
class KotlinSpotlightRunner(private val serverLocation: String) {
//    val process: Process
    val processBuilder: ProcessBuilder
    val process: Process

    init {
        beginDownloads()

        // run server
        processBuilder = ProcessBuilder("java", "-jar", "$serverLocation/spotlight.jar",
                "$serverLocation/en_2+2/", "http://localhost:9310/jsr-spotlight")

        // Gotta eat the process's output, otherwise it seems to stall when the buffer's full
        processBuilder.redirectOutput(File("/dev/null"))
            .redirectErrorStream(true)
        process = processBuilder.start()

        // Ensure process is destroyed when we terminate the JVM
        Runtime.getRuntime().addShutdownHook(Thread {
            process.destroy()
        })

    }


    /**
     * Function: downloadFromUrl
     * Description: Takes a url and output filename and downloads file from url (using copyURLToFile)
     */
    fun downloadFromUrl(url: String, out: String) {
        println("Downloading from $url to $out")
        val site = URL(url)
        val destination = File(out)
        FileUtils.copyURLToFile(site, destination)
    }

    /**
     * Function: beginDownloads
     * Description: Will download spotlight server and model from dbpedia when they are not already availlable.
     */
    fun beginDownloads() {
        val serverLoc = File(serverLocation)
                .applyIf({!exists()}) { mkdir() } // create directory if it doesn't already exist

        // Get Spotlight Jar file (download it from server if it doesn't exist in spotlight directory)
        val spotLoc = "$serverLocation/spotlight.jar"
        val spotlightJar = File(spotLoc).applyIf({!exists()}) {
            downloadFromUrl("http://downloads.dbpedia-spotlight.org/spotlight/dbpedia-spotlight-0.7.1.jar", spotLoc)
        }

        // Make sure the model data is also downloaded
        val model_loc = "$serverLocation/en_2+2"
        val compressed_loc = "$serverLocation/en.tar.gz"
        val modelFile = File(model_loc)

        if (!modelFile.exists() || modelFile.list().isEmpty()) {
            modelFile.mkdir()
            val archive = File(compressed_loc).applyIf({!exists()}) {
                downloadFromUrl("http://downloads.dbpedia-spotlight.org/2016-04/en/model/en.tar.gz", compressed_loc)
            }

            val manager = ConsoleLoggerManager()
            manager.initialize()
            val unarchiver = TarGZipUnArchiver().apply {
                sourceFile = archive
                destDirectory = File(serverLocation)
                enableLogging(manager.getLoggerForComponent("compress"))
            }
            unarchiver.extract()

            // Clean up compressed archive
            archive.delete()
        }
    }
}
