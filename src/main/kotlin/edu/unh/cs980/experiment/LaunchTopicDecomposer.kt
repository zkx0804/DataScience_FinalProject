@file: JvmName("LaunchSparqlDownloader")
package edu.unh.cs980.experiment

import edu.unh.cs980.Main
import edu.unh.cs980.identity
import edu.unh.cs980.language.KotlinMetaKernelAnalyzer
import edu.unh.cs980.misc.MethodContainer
import edu.unh.cs980.misc.buildResourceDispatcher
import edu.unh.cs980.paragraph.KotlinSparql
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers

/**
 * Class: LaunchTopicDecomposer
 * Desc: This is just an app that is used to deconstruct topics.
 * @see KotlinMetaKernelAnalyzer
 */
class LaunchTopicDecomposer(val resources: HashMap<String, Any>) {
    val paragraphs: String by resources
    val analyzer = KotlinMetaKernelAnalyzer(paragraphs)



    fun run() {
        analyzer.trainParagraphs()
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("topic_decomposer")
                .help("Decomposes topics into hierarchies of paragraphs, sentences, and words.")
            register("", mainParser)


        }

        fun register(methodType: String, parser: Subparser)  {
            val exec = Main.buildExec { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = LaunchTopicDecomposer(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<LaunchTopicDecomposer>
                method.getMethod("", methodName)?.invoke(instance)

            }

            parser.help("Decomposes topics into hierarchies of paragraphs, sentences, and words.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<LaunchTopicDecomposer> {
                        method("", "run") { run() }
                        help = "Decomposes topics and stores them in the descent_data/ directory"
                    }

                    resource("paragraphs") {
                        help = "Directory containing subdirectories for each topic's paragraphs " +
                                "(default: /trec_data/team_1/paragraphs/)"
                        default = "/trec_data/team_1/paragraphs/"
                    }
                }
    }

}