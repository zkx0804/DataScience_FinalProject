package edu.unh.cs980.variations;

import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import edu.unh.cs980.entityLinking.EntityMethods;
import edu.unh.cs980.entityLinking.EntityWord;
import edu.unh.cs980.utils.ProjectUtils;

// 1. Predict relevant entities by annotated abstract of the entities from query
// 2. Build expanded query = query + words from entity's page (like RM3/Relevance Model); run this query against paragraph index
class QueryObj {
	String queryStr;
	Query query;

	public void setQueryStr(String str) {
		this.queryStr = str;
	}

	public String getQueryStr() {
		return this.queryStr;
	}

	public void setQuery(Query q) {
		this.query = q;
	}

	public Query getQuery() {
		return this.query;
	}
}

public class Query_RM_QE_variation {

	private static final Logger logger = Logger.getLogger(Query_RM_QE_variation.class);
	private static int top_k_entities = 5; // Include top k entities for QE
	private static int max_result = 100; // Max number for Lucene docs
	private static QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());
	private static String abstract_index_dir;
	private static Integer time_out = 5;

	private static ExecutorService workers;

	public static ArrayList<String> getResults(ArrayList<String> queriesStr, String index_dir, String abstract_index)
			throws IOException, ParseException {

		abstract_index_dir = abstract_index;
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		logger.info("Query_RM + QueryExpansion ====> Retrieving results for " + queriesStr.size() + " queries...");

		int duplicate = 0;
		for (String queryStr : queriesStr) {
			ArrayList<String> expanded_entities = getExpandedQuery(top_k_entities, queryStr);
			Query q_rm = generateWeightedQuery(queryStr, expanded_entities);

			TopDocs tops = searcher.search(q_rm, max_result);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField(PID).stringValue();
				float rankScore = score.score;
				int rank = i + 1;

				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ rankScore + " Query_RM_QE";
				if (runFileStr.contains(runStr)) {
					duplicate++;
					// System.out.println("Found duplicate: " + runStr);
				} else {
					runFileStr.add(runStr);
				}
			}

		}

		return runFileStr;
	}

	public static ArrayList<String> getResultsWithMultiThread(ArrayList<String> queriesStr, String index_dir,
			String abstract_index) throws IOException, ParseException {

		abstract_index_dir = abstract_index;
		ArrayList<String> runFileStr = new ArrayList<String>();
		ArrayList<QueryObj> queryList = new ArrayList<QueryObj>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		logger.info("Query_RM + QueryExpansion (Multi-threads)====> Retrieving results for " + queriesStr.size()
				+ " queries...");

		try {
			queryList = convertToExpanedQuery(queriesStr);
			logger.debug(queryList.size());

			int duplicate = 0;
			for (QueryObj q_obj : queryList) {

				TopDocs tops = searcher.search(q_obj.getQuery(), max_result);
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				for (int i = 0; i < scoreDoc.length; i++) {
					ScoreDoc score = scoreDoc[i];
					Document doc = searcher.doc(score.doc);
					String paraId = doc.getField(PID).stringValue();
					float rankScore = score.score;
					int rank = i + 1;

					String runStr = "enwiki:" + q_obj.getQueryStr().replace(" ", "%20") + " Q0 " + paraId + " " + rank
							+ " " + rankScore + " Query_RM_QE";
					if (runFileStr.contains(runStr)) {
						duplicate++;
						// System.out.println("Found duplicate: " + runStr);
					} else {
						runFileStr.add(runStr);
					}
				}

			}

		} catch (Exception e) {
			logger.debug("Error!!!! " + e.getLocalizedMessage());
		}

		return runFileStr;
	}

	/*
	 * Multi-thread methods has error.
	 * 
	 * MMapIndexInput(path=
	 * "C:\Users\Kaixin\workspace\DataScience_FinalProject\abstract_index\_p8.cfs")
	 * [this may be caused by lack of enough unfragmented virtual address space
	 * or too restrictive virtual memory limits enforced by the operating
	 * system, preventing us to map a chunk of 16716594 bytes. Windows is
	 * unfortunately very limited on virtual address space. If your index size
	 * is several hundred Gigabytes, consider changing to Linux. More
	 * information:
	 * http://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html]
	 */
	private static ArrayList<QueryObj> convertToExpanedQuery(ArrayList<String> queriesStr)
			throws InterruptedException, ExecutionException {
		// Use cache pool to create multi thread.
		workers = Executors.newCachedThreadPool();
		ArrayList<QueryObj> queryList = new ArrayList<QueryObj>();
		Collection<Callable<QueryObj>> tasks = new ArrayList<Callable<QueryObj>>();
		for (final String queryStr : queriesStr) {
			if (queriesStr != null) {
				tasks.add(new Callable<QueryObj>() {
					public QueryObj call() throws Exception {
						logger.debug("Get query: " + queryStr);
						ArrayList<String> terms = getExpandedQuery(top_k_entities, queryStr);

						Query expanded_query = generateWeightedQuery(queryStr, terms);
						QueryObj obj = new QueryObj();
						obj.setQueryStr(queryStr);
						obj.setQuery(expanded_query);

						return obj;
					}

				});
			}
		}

		List<Future<QueryObj>> results = workers.invokeAll(tasks, time_out, TimeUnit.SECONDS);
		for (Future<QueryObj> f : results) {
			if (f != null) {
				QueryObj result = f.get();
				queryList.add(result);
			}
		}
		workers.shutdown();
		return queryList;
	}

	private static ArrayList<String> getExpandedQuery(int top_k, String queryStr) throws IOException {
		// Page query
		// Section query
		Boolean isSection = false;
		List<String> termList = new ArrayList<String>();
		HashMap<String, Float> entityScore = new HashMap<>();
		ArrayList<String> expandedQueryTerms = new ArrayList<String>();

		if (queryStr.contains("/")) {
			// Section query
			isSection = true;
			termList = Arrays.asList(queryStr.split("/"));
		}

		if (!isSection) {
			String abstracStr = EntityMethods.getEntityAbstract(queryStr.replace(" ", "_").toLowerCase(),
					abstract_index_dir);
			if (!abstracStr.isEmpty()) {
				ArrayList<EntityWord> entities = EntityMethods.getAnnotatedEntites(abstracStr);
				if (entities == null) {
					return expandedQueryTerms;
				}
				if (entities.size() > 5) {
					for (EntityWord entity : entities) {
						entityScore.put(entity.getSurfaceForm(), entity.getSimilarityScore());
					}
				} else if (entities.size() > 0 && entities.size() <= 5) {
					for (EntityWord entity : entities) {
						expandedQueryTerms.add(entity.getSurfaceForm());
					}
					return expandedQueryTerms;
				} else {
					// logger.debu("Can't find any entities for query: " +
					// queryStr);
					return expandedQueryTerms;
				}
			}
		} else {
			for (int i = 0; i < termList.size(); i++) {
				int factor = 1;
				String term = termList.get(i);
				if (i == 0) {
					factor = 3;
				}
				if (i == termList.size() - 1) {
					factor = 2;
				}
				String abstracStr = EntityMethods.getEntityAbstract(term.replace(" ", "_").toLowerCase(),
						abstract_index_dir);
				if (!abstracStr.isEmpty()) {
					ArrayList<EntityWord> entities = EntityMethods.getAnnotatedEntites(abstracStr);
					if (entities != null) {
						if (entities.size() > 0) {
							for (EntityWord entity : entities) {
								float score = (float) entity.getSimilarityScore() * factor;
								entityScore.put(entity.getSurfaceForm(), score);
							}
						} else {
							logger.debug("Can't find any entities for term: " + term + ". Skipped.");
						}
					}

				}
			}
			if (entityScore.isEmpty()) {
				return expandedQueryTerms;
			}
		}

		Set<String> termSet = ProjectUtils.getTopValuesInMap(entityScore, top_k).keySet();
		expandedQueryTerms.addAll(termSet);
		return expandedQueryTerms;
	}

	public static ArrayList<String> getExpandedEntitiesFromPageQuery(String page_query, int top_k,
																	 IndexSearcher searcher) {

		ArrayList<String> expandedQueryTerms = new ArrayList<String>();
		HashMap<String, Float> entityScore = new HashMap<>();

		String abstracStr = EntityMethods.getEntityAbstract(page_query.replace(" ", "_").toLowerCase(),
				searcher, true);

		if (!abstracStr.isEmpty()) {
			ArrayList<EntityWord> entities = EntityMethods.getAnnotatedEntites(abstracStr);
			if (entities == null) {
				return expandedQueryTerms;
			}
			if (entities.size() > 5) {
				for (EntityWord entity : entities) {
					// If entities is more than 5, save entity to hashmap, for
					// scoring.
					entityScore.put(entity.getSurfaceForm(), entity.getSimilarityScore());
				}
				// Get the top 5 entities.
				Set<String> termSet = ProjectUtils.getTopValuesInMap(entityScore, top_k).keySet();
				expandedQueryTerms.addAll(termSet);
				return expandedQueryTerms;
			} else if (entities.size() > 0 && entities.size() <= 5) {
				for (EntityWord entity : entities) {
					expandedQueryTerms.add(entity.getSurfaceForm());
				}
				return expandedQueryTerms;
			} else {
				// logger.debug("Can't find any entities for query: " +
				// queryStr);
				return expandedQueryTerms;
			}
		}
		return expandedQueryTerms;
	}


	public static HashMap<String, ArrayList<String>> getExpandedEntitiesListFromSectionQuery(String section_query,
			int top_k, String abstract_index_dir) {
		// <query_term, List of expanded Terms
		HashMap<String, ArrayList<String>> result_map = new HashMap<String, ArrayList<String>>();
		HashMap<String, Float> entityScore;

		List<String> termList = new ArrayList<String>();
		termList = Arrays.asList(section_query.split("/"));

		if (!termList.isEmpty()) {
			for (int i = 0; i < termList.size(); i++) {
				int factor = 1; // Entities from in-between will have normal
								// score.
				String term = termList.get(i);
				if (i == 0) {
					factor = 3; // Entities from top level section will rank
								// higher
				}
				if (i == termList.size() - 1) {
					factor = 2; // Entities from the bottom level section will
								// rank higher
				}

				ArrayList<String> expanded_terms = new ArrayList<String>();

				String abstracStr = EntityMethods.getEntityAbstract(term.replace(" ", "_").toLowerCase(),
						abstract_index_dir);
				entityScore = new HashMap<>();
				if (!abstracStr.isEmpty()) {
					ArrayList<EntityWord> entities = EntityMethods.getAnnotatedEntites(abstracStr);
					if (entities != null) {
						if (entities.size() > 0) {
							for (EntityWord entity : entities) {
								float score = (float) entity.getSimilarityScore() * factor;
								entityScore.put(entity.getSurfaceForm(), score);
							}
						} else {
							logger.debug("Can't find any entities for term: " + term + ". Skipped.");
						}
					}

				}
				if (!entityScore.isEmpty()) {
					Set<String> termSet = ProjectUtils.getTopValuesInMap(entityScore, top_k).keySet();
					expanded_terms.addAll(termSet);
				}
				result_map.put(term, expanded_terms);
			}

		}

		return result_map;
	}

	private static Query generateWeightedQuery(String initialQ, ArrayList<String> rm_list) throws ParseException {
		if (!rm_list.isEmpty()) {
			String rm_str = String.join(" ", rm_list);
			Query q = parser.parse(QueryParser.escape(initialQ) + "^0.6" + QueryParser.escape(rm_str) + "^0.4");
			logger.debug(initialQ + " =====> " + initialQ + " " + rm_str);
			return q;
		} else {
			Query q = parser.parse(QueryParser.escape(initialQ));
			logger.debug(initialQ + " =====> " + initialQ);
			return q;
		}
	}

	private static ArrayList<String> analyzeByBigram(String inputStr) throws IOException {
		ArrayList<String> strList = new ArrayList<String>();
		Analyzer analyzer = new BigramAnalyzer();
		TokenStream tokenizer = analyzer.tokenStream(CONTENT, inputStr);
		CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
		tokenizer.reset();
		while (tokenizer.incrementToken()) {
			String token = charTermAttribute.toString();
			if (token.contains(" ")) {
				strList.add(token);
			}
		}
		tokenizer.end();
		tokenizer.close();
		return strList;
	}
}
