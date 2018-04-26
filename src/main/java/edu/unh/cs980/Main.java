package edu.unh.cs980;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;

import co.nstant.in.cbor.CborException;
import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import edu.unh.cs980.WordEmbedding.Contextual_QueryExpansion;
import edu.unh.cs980.WordEmbedding.EntitySimilarity;
import edu.unh.cs980.WordEmbedding.Lucene_Query_Creator;
import edu.unh.cs980.WordEmbedding.ParaRankWithDepParser;
import edu.unh.cs980.WordEmbedding.ParagraphSimilarity;
import edu.unh.cs980.WordEmbedding.ParagraphWithWordnet;
import edu.unh.cs980.WordEmbedding.TfIdfSimilarity;
import edu.unh.cs980.WordEmbedding.TopktreeContextualSimilarity;
import edu.unh.cs980.experiment.LaunchSparqlDownloader;
import edu.unh.cs980.experiment.LaunchTopicDecomposer;
import edu.unh.cs980.experiment.MasterExperiment;
import edu.unh.cs980.language.KotlinAbstractExtractor;
import edu.unh.cs980.utils.ProjectUtils;
import edu.unh.cs980.utils.QueryBuilder;
import edu.unh.cs980.variations.Doc_RM_QE_variation;
import edu.unh.cs980.variations.FreqBigram_variation;
import edu.unh.cs980.variations.NLP_query_variation;
import edu.unh.cs980.variations.QueryExpansion_variation;
import edu.unh.cs980.variations.Query_RM_QE_variation;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class Main {

	private static final Logger logger = Logger.getLogger(Main.class);

	// Used as a wrapper around a static method: will call method and pass
	// argument parser's parameters to it
	public static class Exec {
		private Consumer<Namespace> func;

		Exec(Consumer<Namespace> funcArg) {
			func = funcArg;
		}

		void run(Namespace params) {
			func.accept(params);
		}
	}

	public static Exec buildExec(Consumer<Namespace> consumer) {
		return new Exec(consumer);
	}

	/****
	 * @Function:getAllPageFromPath
	 * @param indexLocation
	 * @param queryLocation
	 * @param rankingOutputLocation
	 * @return pagelist
	 */

	private static ArrayList<Data.Page> getAllPageFromPath(String indexLocation, String queryLocation,
			String rankingOutputLocation) {
		ArrayList<Data.Page> pageList = new ArrayList<Data.Page>();

		try {

			FileInputStream fis = new FileInputStream(new File(queryLocation));
			for (Data.Page page : DeserializeData.iterableAnnotations(fis)) {

				pageList.add(page);

			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
		}
		return pageList;
	}

	public static ArgumentParser createArgParser() {
		ArgumentParser parser = ArgumentParsers.newFor("program").build();
		Subparsers subparsers = parser.addSubparsers(); // Subparsers is used to
														// create subcommands

		// Add subcommand for running index program
		Subparser indexParser = subparsers.addParser("index") // index is the
																// name of the
																// subcommand
				.setDefault("func", new Exec(Main::runIndexer)).help("Indexes paragraph corpus using Lucene.");
		indexParser.addArgument("corpus").required(true).help("Location to paragraph corpus file (.cbor)");
		indexParser.addArgument("--spotlight_folder").setDefault("")
				.help("Directory containing spotlight jar file and model."
						+ "If the directory doesn't exist, the required files are downloaded automatically."
						+ "If no folder is specified, entity annotation is skipped.");
		indexParser.addArgument("--out").setDefault("index")
				.help("Directory name to create for Lucene index (default: index)");

		// Argument parser for Paragraph Similarity (Added By Bindu)

		Subparser paragraphSimilarityParser = subparsers.addParser("paragraph_similarity")
				.setDefault("func", new Exec(Main::runParagraphSimilarity)).help("Queries Lucene database.");

		paragraphSimilarityParser.addArgument("index").help("Location of Lucene index directory.");
		paragraphSimilarityParser.addArgument("query_file").help("Location of the query file (.cbor)");
		paragraphSimilarityParser.addArgument("--out") // -- means it's not
														// positional
				.setDefault("paragraph_similarity.run") // If no --out is
														// supplied,
				// defaults to
				// paragraph_similarity.txt
				.help("The name of the trec_eval compatible run file to write. (default: paragraph_similarity.run)");

		// Argument parser for TFIDF Similarity (Added By Bindu)
		Subparser tfidfSimilarityParser = subparsers.addParser("tfidf_similarity")
				.setDefault("func", new Exec(Main::runTfidfSimilarity)).help("Queries Lucene database.");

		tfidfSimilarityParser.addArgument("index").help("Location of Lucene index directory.");
		tfidfSimilarityParser.addArgument("query_file").help("Location of the query file (.cbor)");
		tfidfSimilarityParser.addArgument("--out") // -- means it's not
													// positional
				.setDefault("tfidf_similarity.run") // If no --out is supplied,
													// defaults to
													// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: tfidf_similarity.run)");

		// Argument parser for Entity Similarity
		Subparser entitySimilarityParser = subparsers.addParser("entitySimilarity")
				.setDefault("func", new Exec(Main::runEntitySimilarity)).help("Use EntitySimilarity");
		entitySimilarityParser.addArgument("query_choice").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		entitySimilarityParser.addArgument("multi_thread").choices("true", "false").setDefault("false")
				.help("\ttrue: Run Multi Thread function. (Not Stable)\n" + "\tfalse: Use normal function\n");

		entitySimilarityParser.addArgument("index").help("Location of Lucene index directory.");
		entitySimilarityParser.addArgument("abstract").help("Location of Lucene entity abstract index directory.");
		entitySimilarityParser.addArgument("query_file").help("Location of the query file (.cbor)");
		entitySimilarityParser.addArgument("--out").setDefault("Entity-Similarity.run")
				.help("The name of the trec_eval compatible run file to write. (default: Entity-Similarity.run)");

		Subparser paragraphwithwordnet;
		paragraphwithwordnet = subparsers.addParser("paragraph_wordnet")
				.setDefault("func", new Exec(Main::runParagraphWordnet)).help("Paragraph Wordnet Similarity");
		paragraphwithwordnet.addArgument("index").help("Location of Lucene index directory.");
		paragraphwithwordnet.addArgument("query_file").help("Location of the query file (.cbor)");
		paragraphwithwordnet.addArgument("outputLocation") // -- means it's not
															// positional
				.help("The name of the trec_eval compatible run file to write. (default:paragraph_wordnet.run)");

		// Argument parser for Query Expansion
		Subparser queryExpansionParser = subparsers.addParser("query_expansion")
				.setDefault("func", new Exec(Main::runQueryExpansion)).help("Use Query Expansion");
		queryExpansionParser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		queryExpansionParser.addArgument("index").help("Location of Lucene index directory.");
		queryExpansionParser.addArgument("query_file").help("Location of the query file (.cbor)");
		queryExpansionParser.addArgument("--out") // -- means it's not
													// positional
				.setDefault("query_results.run") // If no --out is supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_results.run)");

		// Argument parser for Query RM Query Expansion
		Subparser query_RM_QE_Parser = subparsers.addParser("query_rm_qe")
				.setDefault("func", new Exec(Main::runQuery_RM_QE))
				.help("Use Query Entity Relevance Model + Query Expansion");
		query_RM_QE_Parser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		query_RM_QE_Parser.addArgument("multi_thread").choices("true", "false").setDefault("false")
				.help("\ttrue: Run Multi Thread function. (Not Stable)\n" + "\tfalse: Use normal function\n");

		query_RM_QE_Parser.addArgument("index").help("Location of Lucene index directory.");
		query_RM_QE_Parser.addArgument("abstract").help("Location of Lucene entity abstract index directory.");
		query_RM_QE_Parser.addArgument("query_file").help("Location of the query file (.cbor)");
		query_RM_QE_Parser.addArgument("--out") // -- means it's not
												// positional
				.setDefault("query_rm_qe_results.run") // If no --out is
														// supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_rm_qe_results.run)");

		// Argument parser for Doc RM Query Expansion
		Subparser doc_RM_QE_Parser = subparsers.addParser("doc_rm_qe").setDefault("func", new Exec(Main::runDoc_RM_QE))
				.help("Use Document Entity Relevance Model +Query Expansion");
		doc_RM_QE_Parser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		doc_RM_QE_Parser.addArgument("index").help("Location of Lucene index directory.");
		doc_RM_QE_Parser.addArgument("query_file").help("Location of the query file (.cbor)");
		doc_RM_QE_Parser.addArgument("--out") // -- means it's not
												// positional
				.setDefault("doc_rm_qe_results.run") // If no --out is supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: doc_rm_qe_results.run)");

		// Argument parser for NLP query variation
		Subparser nlp_query_Parser = subparsers.addParser("nlp_query_variation")
				.setDefault("func", new Exec(Main::runNLP_query_variation))
				.help("Use NLP + entities relations relevance method.");
		nlp_query_Parser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		nlp_query_Parser.addArgument("multi_thread").choices("true", "false").setDefault("false")
				.help("\ttrue: Run Multi Thread function. \n" + "\tfalse: Use normal function\n");

		nlp_query_Parser.addArgument("index").help("Location of Lucene index directory.");
		nlp_query_Parser.addArgument("query_file").help("Location of the query file (.cbor)");
		nlp_query_Parser.addArgument("--out") // -- means it's not
												// positional
				.setDefault("nlp_query_results.run") // If no --out is
														// supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: nlp_query_results.run)");

		// // Gram
		// Subparser gramParser =
		// subparsers.addParser("gram_indexer").setDefault("func", new
		// Exec(Main::runGram))
		// .help("Indexes -gram models for paragraphCorpus. See Readme for
		// further details.");
		//
		// gramParser.addArgument("corpus").help("Location of paragraph corpus
		// to index.");
		//
		// gramParser.addArgument("--database").setDefault("gram")
		// .help("Name of the indexed Lucene database to creature (default is
		// gram)");
		//
		// // Abstract Indexer
		// Subparser abstractParser = subparsers.addParser("abstract_indexer")
		// .setDefault("func", new Exec(Main::runAbstract))
		// .help("Creates a Lucene index of entities, where abstract are derived
		// from first three paragraphs."
		// + "See Readme for further details.");
		// abstractParser.addArgument("corpus").help("Location of paragraph
		// corpus to index.");

		// -----------------------------------Added on 22nd April By Bindu
		// -----------------------------------------

		// Argument parser for Contextual Similarity with Query Expansion
		Subparser ContextSimilarityQueryExpanParser = subparsers.addParser("context_queryeexpansion")
				.setDefault("func", new Exec(Main::runConQuerExpansion))
				.help("Use Contextual Similarity with Query expansion");
		ContextSimilarityQueryExpanParser.addArgument("query_choice").choices("page")
				.help("\tpage: Page of paragraph corpus\n");
		ContextSimilarityQueryExpanParser.addArgument("multi_thread").choices("true", "false").setDefault("false")
				.help("\ttrue: Run Multi Thread function. (Not Stable)\n" + "\tfalse: Use normal function\n");

		ContextSimilarityQueryExpanParser.addArgument("index").help("Location of Lucene index directory.");
		ContextSimilarityQueryExpanParser.addArgument("abstract")
				.help("Location of Lucene entity abstract index directory.");
		ContextSimilarityQueryExpanParser.addArgument("query_file").help("Location of the query file (.cbor)");
		ContextSimilarityQueryExpanParser.addArgument("--out").setDefault("ContextQuerySimilarity.run")
				.help("The name of the trec_eval compatible run file to write. (default: ContextQuerySimilarity.run)");

		// Argument parser for top_k_treecontextualsimilarity (Added By Bindu)

		Subparser TopktreeConSimParser = subparsers.addParser("top_k_treecontextualsimilarity")
				.setDefault("func", new Exec(Main::runtopktreeContSim))
				.help("Run top k tree contextualSimilarity Passer");

		TopktreeConSimParser.addArgument("index").help("Location of Lucene index directory.");
		TopktreeConSimParser.addArgument("query_file").help("Location of the query file (.cbor)");
		TopktreeConSimParser.addArgument("--out").setDefault("topk_treeConSimParser.run")
				.help("The name of the trec_eval compatible run file to write. (default: topk_treeConSimParser.run)");

		// Argument parser for ParaRank With DependencyParser

		Subparser ParaRankWithDepParser = subparsers.addParser("pararank_with_depparser")
				.setDefault("func", new Exec(Main::runParaGraphRankWithDepParser))
				.help(" Paragraph Ranking with dependency Parser");

		ParaRankWithDepParser.addArgument("index").help("Location of Lucene index directory.");
		ParaRankWithDepParser.addArgument("query_file").help("Location of the query file (.cbor)");
		ParaRankWithDepParser.addArgument("--out").setDefault("pararankdepparser.run")
				.help("The name of the trec_eval compatible run file to write. (default: pararankdepparser.run)");

		// Abstract Indexer
		Subparser abstractParser = subparsers.addParser("abstract_indexer")
				.setDefault("func", new Exec(Main::runAbstract))
				.help("Creates a Lucene index of entities, where abstract are derived from first three paragraphs."
						+ "See Readme for further details.");
		abstractParser.addArgument("corpus").help("Location of paragraph corpus to index.");

		MasterExperiment.Companion.addExperiments(subparsers);
		LaunchSparqlDownloader.Companion.addExperiments(subparsers);
		LaunchTopicDecomposer.Companion.addExperiments(subparsers);

		// ****************** Bindu Parser completed
		// ******************************************
		return parser;
	}

	private static void runAbstract(Namespace params) {
		String corpusFile = params.getString("corpus");
		KotlinAbstractExtractor extractor = new KotlinAbstractExtractor("abstract");
		extractor.getAbstracts(corpusFile);
	}

	private static void runIndexer(Namespace params) {
		String indexLocation = params.getString("out");
		String corpusFile = params.getString("corpus");
		String spotlight_location = params.getString("spotlight_folder");

		try {
			IndexData.indexAllData(indexLocation, corpusFile, spotlight_location);
		} catch (CborException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	// Runs Bindu's Query Heading Weights Variation
	private static void runQueryHeadingWeights(Namespace params) {
		String index = params.getString("index");
		String queryFile = params.getString("query_file");
		String queryType = params.getString("query_type");
		String out = params.getString("out");
		StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
		BM25Similarity sim = new BM25Similarity();

		try {
			Lucene_Query_Creator qCreator = new Lucene_Query_Creator("", queryType, standardAnalyzer, sim, index);

			qCreator.writeRankings(queryFile, out);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	// Runs Bindu Paragraph Similarity Variation
	public static void runParagraphSimilarity(Namespace params) {
		try {
			String indexLocation = params.getString("index");// Paragraph Corpus
																// indexing
			String queryLocation = params.getString("query_file"); // outlines-cbor
																	// file
			String rankingOutputLocation = params.getString("out");

			ArrayList<Data.Page> pagelist = getAllPageFromPath(indexLocation, queryLocation, rankingOutputLocation);

			ParagraphSimilarity ps = new ParagraphSimilarity(pagelist, 100, indexLocation);
			ps.writeParagraphScore(rankingOutputLocation);

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

	// Run for Entity Similarity

	private static void runEntitySimilarity(Namespace params) {
		try {
			String indexLocation = params.getString("index");
			String abstract_indexLocation = params.getString("abstract");
			String queryFile = params.getString("query_file");
			String queryChoice = params.getString("query_choice");
			String multi = params.getString("multi_thread");
			Boolean runMultiThread = Boolean.valueOf(multi.toLowerCase());
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryChoice.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();

				if (runMultiThread) {
					ArrayList<String> page_run = EntitySimilarity.getResultsWithMultiThread(pages_queries,
							indexLocation, abstract_indexLocation);
					ProjectUtils.writeToFile(out, page_run);
				} else {
					ArrayList<String> page_run = EntitySimilarity.getEntityResults(pages_queries, indexLocation,
							abstract_indexLocation);
					ProjectUtils.writeToFile(out, page_run);
				}

			} else if (queryChoice.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();

				if (runMultiThread) {
					ArrayList<String> section_run = EntitySimilarity.getResultsWithMultiThread(section_queries,
							indexLocation, abstract_indexLocation);
					ProjectUtils.writeToFile(out, section_run);
				} else {
					ArrayList<String> section_run = EntitySimilarity.getEntityResults(section_queries, indexLocation,
							abstract_indexLocation);
					ProjectUtils.writeToFile(out, section_run);
				}

			} else {
				System.out.println("Error: QueryChoice was not recognized");
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}

	// Runs Bindu Tfidf(lnc.ltc) SImilarity Variation

	public static void runTfidfSimilarity(Namespace params) {
		try {
			String indexLocation = params.getString("index"); // Paragraph
																// Corpus
																// indexing
			String queryLocation = params.getString("query_file");
			String rankingOutputLocation = params.getString("out"); // where
																	// Tf-IDF
																	// output
																	// should
																	// be
			ArrayList<Data.Page> pagelist = getAllPageFromPath(indexLocation, queryLocation, rankingOutputLocation);

			TfIdfSimilarity tfidf = new TfIdfSimilarity(pagelist, 100, indexLocation);
			tfidf.writeTFIDFScoresTo(rankingOutputLocation);
		} catch (Exception e) {
			logger.error(e.getMessage());

		}
	}

	// This function is being used for running query
	public static void runParagraphWordnet(Namespace params) {
		try {
			String index = params.getString("index");
			String queryLocation = params.getString("query_file");
			String rankingOutputLocation = params.getString("outputLocation");

			ParagraphWithWordnet qbuilder = getQueryBuilder(index, queryLocation, rankingOutputLocation);
			qbuilder.writeRankings(queryLocation, rankingOutputLocation);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// This function is being used for creating
	public static ParagraphWithWordnet getQueryBuilder(String indexLocation, String queryLocation,
			String rankingOutputLocation) throws IOException {

		return new ParagraphWithWordnet(new StandardAnalyzer(), new BM25Similarity(), indexLocation);
	}

	// Runs Kevin's Query Expansion Variation
	private static void runQueryExpansion(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = QueryExpansion_variation.getSearchResult(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				ArrayList<String> section_run = QueryExpansion_variation.getSearchResult(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				System.out.println("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}

	}

	// Runs Kevin's Frequent Bigram Variation
	private static void runFreqBigram(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = FreqBigram_variation.getSearchResult(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				ArrayList<String> section_run = FreqBigram_variation.getSearchResult(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				logger.error("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}

	// Runs Kevin's Query Entity relevance model + query expansion method
	private static void runQuery_RM_QE(Namespace params) {
		try {
			String index = params.getString("index");
			String abstract_index = params.getString("abstract");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String multi = params.getString("multi_thread");
			Boolean runMultiThread = Boolean.valueOf(multi.toLowerCase());
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				if (runMultiThread) {
					ArrayList<String> page_run = Query_RM_QE_variation.getResultsWithMultiThread(pages_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				} else {
					ArrayList<String> page_run = Query_RM_QE_variation.getResults(pages_queries, index, abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				}

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				if (runMultiThread) {
					ArrayList<String> section_run = Query_RM_QE_variation.getResultsWithMultiThread(section_queries,
							index, abstract_index);
					ProjectUtils.writeToFile(out, section_run);
				} else {
					ArrayList<String> section_run = Query_RM_QE_variation.getResults(section_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, section_run);
				}

			} else {
				logger.error("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}

	// Runs Kevin's Document Entity Relevance Model + Query Expansion method
	private static void runDoc_RM_QE(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = Doc_RM_QE_variation.getResults(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				ArrayList<String> section_run = Doc_RM_QE_variation.getResults(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				logger.error("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}

	// Run Kevin's NLP entities relations variation methods
	private static void runNLP_query_variation(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String multi = params.getString("multi_thread");
			Boolean runMultiThread = Boolean.valueOf(multi.toLowerCase());
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);

			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				if (runMultiThread) {
					ArrayList<String> page_run = NLP_query_variation.getResultsWithMultiThread(pages_queries, index);
					ProjectUtils.writeToFile(out, page_run);
				} else {
					ArrayList<String> page_run = NLP_query_variation.getResults(pages_queries, index);
					ProjectUtils.writeToFile(out, page_run);
				}
			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				if (runMultiThread) {
					ArrayList<String> section_run = NLP_query_variation.getResultsWithMultiThread(section_queries,
							index);
					ProjectUtils.writeToFile(out, section_run);
				} else {
					ArrayList<String> section_run = NLP_query_variation.getResults(section_queries, index);
					ProjectUtils.writeToFile(out, section_run);
				}
			} else {
				logger.error("Error: QueryType not recognized. Got: " + queryType);
			}
		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}

	// ******************************* Adding calling method which is mentioned
	// in Parser call ( By Bindu ) ***********************

	// Run Contextual Query Expansion

	private static void runConQuerExpansion(Namespace params) {
		try {
			String indexLocation = params.getString("index");
			String abstract_indexLocation = params.getString("abstract");
			String queryFile = params.getString("query_file");
			String queryChoice = params.getString("query_choice");
			String multi = params.getString("multi_thread");
			Boolean runMultiThread = Boolean.valueOf(multi.toLowerCase());
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryChoice.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();

				if (runMultiThread) {
					ArrayList<String> page_run = Contextual_QueryExpansion.TrueWithMultiThread(pages_queries,
							indexLocation, abstract_indexLocation);
					ProjectUtils.writeToFile(out, page_run);
				} else {
					ArrayList<String> page_run = Contextual_QueryExpansion.getContextualResults(pages_queries,
							indexLocation, abstract_indexLocation);
					ProjectUtils.writeToFile(out, page_run);
				}

			} else {
				System.out.println("Error: QueryType was not recognized");
			}

		} catch (Throwable e) {
			System.out.println(e.getMessage());
		}
	}

	// Added on 22nd April By Bindu
	public static void runtopktreeContSim(Namespace params) {
		try {
			String indexLocation = params.getString("index");
			String queryLocation = params.getString("query_file");
			String rankingOutputLocation = params.getString("out");

			ArrayList<Data.Page> pagelist = getAllPageFromPath(indexLocation, queryLocation, rankingOutputLocation);

			TopktreeContextualSimilarity ts = new TopktreeContextualSimilarity(pagelist, 100, indexLocation);
			ts.writeContextualParagraphScore(rankingOutputLocation);

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

	// for Paragraph Ranking with Dependency parser with full tree with BM25
	// Similarity
	public static void runParaGraphRankWithDepParser(Namespace params) {
		try {
			String indexLocation = params.getString("index");
			String queryLocation = params.getString("query_file");
			String rankingOutputLocation = params.getString("out");

			ArrayList<Data.Page> pagelist = getAllPageFromPath(indexLocation, queryLocation, rankingOutputLocation);

			ParaRankWithDepParser ps = new ParaRankWithDepParser(pagelist, 100, indexLocation);
			ps.writeParaRankWithDepParser(rankingOutputLocation);

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

	// ******************************** Completed on 22nd April ( By Bindu
	// )****************************************

	// This is an example of a method that is compatible with
	// KotlinRanklibFormatter's addFeature
	// queryString: Name of the sectionpath query
	// tops: Top 100 documents returned after querying using BM25
	// indexSearcher: IndexSearcher that has access to the Lucene database (use
	// this if you need to)
	public static List<Double> testFunction(String queryString, TopDocs tops, IndexSearcher indexSearcher) {
		ArrayList<Double> scores = new ArrayList<>();
		for (ScoreDoc sc : tops.scoreDocs) {
			scores.add((double) sc.score);
		}
		return scores;
	}

	// Main class for project
	public static void main(String[] args) {
		System.setProperty("file.encoding", "UTF-8");
		Logger.getRootLogger().setLevel(Level.ERROR);
		ArgumentParser parser = createArgParser();

		try {
			// This parses the arguments (based on createArgParser) and returns
			// the results
			Namespace params = parser.parseArgs(args);

			// We store the function that handles using these parameters in the
			// "func" field
			// In this example, we retrieve the parameter and cast it as Exec,
			// which is used to run the method reference
			// That was passed to it when the Exec was created.
			((Exec) params.get("func")).run(params);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}

}
