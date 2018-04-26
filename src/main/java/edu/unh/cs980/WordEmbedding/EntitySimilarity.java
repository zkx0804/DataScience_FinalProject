package edu.unh.cs980.WordEmbedding;

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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.FSDirectory;

import edu.unh.cs980.entityLinking.EntityMethods;
import edu.unh.cs980.entityLinking.EntityWord;
import edu.unh.cs980.utils.ProjectUtils;
import edu.unh.cs980.utils.QueryBuilder;

/****
 * 
 * Description: This class is being used for Word-net Entity Similarity
 *
 */

public class EntitySimilarity {

	private static QueryParser parser = new QueryParser("text", new StandardAnalyzer());
//	private static QueryParser parser = new QueryParser("parabody", new StandardAnalyzer());
	private static String abstract_index_dir;
	private static Integer time_out = 5;
	private static ExecutorService executors;
	private static int topnentities = 5;
	private static int numDocs = 100;

	/**
	 * Function:getEntityResults
	 * 
	 * @param queriesStr
	 * @param index_dir
	 * @param abstract_index
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 *             Description:This function returns runfile string for the
	 *             given entities
	 */
	public static ArrayList<String> getEntityResults(ArrayList<String> queriesStr, String index_dir,
			String abstract_index) throws IOException, ParseException {

		abstract_index_dir = abstract_index;
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new ClassicSimilarity());

		int dupcount = 0;
		for (String queryStr : queriesStr) {
			ArrayList<String> expanded_entities = getExpandedEntityQuerySimilairty(topnentities, queryStr);
			Query q_rm = generateWeightedEntityQuery(queryStr, expanded_entities);

			TopDocs tops = searcher.search(q_rm, numDocs);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField("paragraphid").stringValue();
				float rankScore = score.score;
				int rank = i + 1;

				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ rankScore + " Entity-Similarity";
				if (runFileStr.contains(runStr)) {
					dupcount++;

				} else {
					runFileStr.add(runStr);
				}
			}

		}

		return runFileStr;
	}

	/*****
	 * 
	 * @Function:RunEntitySimilarity
	 * @param indexLocation
	 * @param abstractIndexLocation
	 * @param queryFile
	 * @param qt
	 * @param threadChoice
	 * @param outputLocation
	 *            Function: This Function gets result from page queries or
	 *            section queries and write into file
	 */

	public void runEntitySimilarity(String indexLocation, String abstractIndexLocation, String queryFile, String qt,
			String threadChoice, String outputLocation) {
		try {
			String index = indexLocation;
			String abstract_index = abstractIndexLocation;
			String queryFileLocation = queryFile;
			String queryType = qt;
			String multi = threadChoice;
			Boolean runMultiThread = Boolean.valueOf(multi.toLowerCase());
			String out = outputLocation;
			QueryBuilder queryBuilder = new QueryBuilder(queryFileLocation);

			if (queryType.equalsIgnoreCase("section")) {

				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();

				if (runMultiThread) {
					ArrayList<String> section_run = EntitySimilarity.getResultsWithMultiThread(section_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, section_run);
				} else {
					ArrayList<String> section_run = EntitySimilarity.getEntityResults(section_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, section_run);
				}
			} else if (queryType.equalsIgnoreCase("pages")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();

				if (runMultiThread) {
					ArrayList<String> page_run = EntitySimilarity.getResultsWithMultiThread(pages_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				} else {
					ArrayList<String> page_run = EntitySimilarity.getEntityResults(pages_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				}

			} else {
				System.out.println("Error: QueryType could not be  recognized");
			}

		} catch (Throwable e) {
			System.out.println(e.getMessage());
		}
	}

	/***************
	 * Function:getResultsWithMultiThread
	 * 
	 * @param queriesStr
	 * @param index_dir
	 * @param abstract_index
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 *             Description : If multithread is true then this function is
	 *             being called
	 */

	public static ArrayList<String> getResultsWithMultiThread(ArrayList<String> queriesStr, String index_dir,
			String abstract_index) throws IOException, ParseException {

		abstract_index_dir = abstract_index;
		ArrayList<String> runFileStr = new ArrayList<String>();
		ArrayList<EntityQueryObj> queryList = new ArrayList<EntityQueryObj>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		try {
			queryList = EntityQuerySimilarity(queriesStr);
			System.out.println(queryList.size());

			int dupCount = 0;
			for (EntityQueryObj q_obj : queryList) {

				TopDocs tops = searcher.search(q_obj.getQuery(), numDocs);
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				for (int i = 0; i < scoreDoc.length; i++) {
					ScoreDoc score = scoreDoc[i];
					Document doc = searcher.doc(score.doc);
					String paraId = doc.getField("paragraphid").stringValue();
					float rankScore = score.score;
					int rank = i + 1;

					String runStr = "enwiki:" + q_obj.getQueryStr().replace(" ", "%20") + " Q0 " + paraId + " " + rank
							+ " " + rankScore + " Entity-Similarity";
					if (runFileStr.contains(runStr)) {
						dupCount++;
					} else {
						runFileStr.add(runStr);
					}
				}

			}

		} catch (Exception e) {
			System.out.println("Error!!!! " + e.getLocalizedMessage());
		}

		return runFileStr;
	}

	/****
	 * Function:convertToExpanedEntityQuery Description:
	 * 
	 * @param queriesStr
	 * @return
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private static ArrayList<EntityQueryObj> EntityQuerySimilarity(ArrayList<String> queriesStr)
			throws InterruptedException, ExecutionException {
		// Use cache pool to create multi thread.
		executors = Executors.newCachedThreadPool();
		ArrayList<EntityQueryObj> queryList = new ArrayList<EntityQueryObj>();
		Collection<Callable<EntityQueryObj>> tasks = new ArrayList<Callable<EntityQueryObj>>();
		for (final String queryStr : queriesStr) {
			if (queriesStr != null) {
				tasks.add(new Callable<EntityQueryObj>() {
					public EntityQueryObj call() throws Exception {
						ArrayList<String> terms = getExpandedEntityQuerySimilairty(topnentities, queryStr);

						Query expanded_query = generateWeightedEntityQuery(queryStr, terms);
						EntityQueryObj obj = new EntityQueryObj();
						obj.setQueryStr(queryStr);
						obj.setQuery(expanded_query);

						return obj;
					}

				});
			}
		}

		List<Future<EntityQueryObj>> results = executors.invokeAll(tasks, time_out, TimeUnit.SECONDS);
		for (Future<EntityQueryObj> f : results) {
			if (f != null) {
				EntityQueryObj result = f.get();
				queryList.add(result);
			}
		}
		executors.shutdown();
		return queryList;
	}

	/***
	 * Function: generateWeightedEntityQuery Description:This function generated
	 * the weighted Query
	 * 
	 * @param EQ
	 * @param rm_list
	 * @return Query
	 * @throws ParseException
	 */

	private static Query generateWeightedEntityQuery(String EQ, ArrayList<String> rm_list) throws ParseException {
		if (!rm_list.isEmpty()) {
			String rm_str = String.join(" ", rm_list);
			Query q = parser.parse(QueryParser.escape(EQ) + "^0.5" + QueryParser.escape(rm_str) + "^0.3");
			return q;
		} else {
			Query q = parser.parse(QueryParser.escape(EQ));

			return q;
		}
	}

	/*********************
	 * Function:getExpandedEntityQuerySimilairty Description :This function is
	 * being used for expanded entity QuerySimilarity
	 * 
	 * @param top_k
	 * @param queryStr
	 * @return
	 * @throws IOException
	 */
	private static ArrayList<String> getExpandedEntityQuerySimilairty(int top_k, String queryStr) throws IOException {
		// Page query
		// Section query
		Boolean isSection = false;
		List<String> termList = new ArrayList<String>();
		HashMap<String, Float> entityScore = new HashMap<>();
		ArrayList<String> expandedQueryTerms = new ArrayList<String>();

		if (queryStr.contains("/")) {
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
							Logger.getRootLogger().debug("Can't find any entities for term: ");

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

	/********
	 * Function:getExpandedEntitiesFromPageQuery Description: This function
	 * returns an Arraylist of Expanded Query
	 * 
	 * @param page_query
	 * @param top_k
	 * @param abstract_index_dir
	 * @return
	 */

	public static ArrayList<String> getExpandedEntitiesFromPageQuery(String page_query, int top_k,
			String abstract_index_dir) {

		ArrayList<String> expandedQueryTerms = new ArrayList<String>();
		HashMap<String, Float> entityScore = new HashMap<>();

		String abstracStr = EntityMethods.getEntityAbstract(page_query.replace(" ", "_").toLowerCase(),
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

				Set<String> termSet = ProjectUtils.getTopValuesInMap(entityScore, top_k).keySet();
				expandedQueryTerms.addAll(termSet);
				return expandedQueryTerms;
			} else if (entities.size() > 0 && entities.size() <= 5) {
				for (EntityWord entity : entities) {
					expandedQueryTerms.add(entity.getSurfaceForm());
				}
				return expandedQueryTerms;
			} else {

				return expandedQueryTerms;
			}
		}
		return expandedQueryTerms;
	}

	/**************
	 * Function:getExpandedEntitiesListFromSectionQuery Description :It check
	 * the entities
	 * 
	 * @param section_query
	 * @param top_k
	 * @param abstract_index_dir
	 * @return :HashMap
	 */

	public static HashMap<String, ArrayList<String>> getExpandedEntitiesListFromSectionQuery(String section_query,
			int top_k, String abstract_index_dir) {
		// <query_term, List of expanded Terms
		HashMap<String, ArrayList<String>> result_map = new HashMap<String, ArrayList<String>>();
		HashMap<String, Float> entityScore;

		List<String> termList = new ArrayList<String>();
		termList = Arrays.asList(section_query.split("/"));

		if (!termList.isEmpty()) {
			for (int i = 0; i < termList.size(); i++) {
				int factor = 1;
				String term = termList.get(i);
				if (i == 0) {
					factor = 3;
				}
				if (i == termList.size() - 1) {
					factor = 2;
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
							System.out.println("Can't find any entities for term: " + term);
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

}
