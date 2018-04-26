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
import edu.unh.cs980.utils.QueryBuilder;
import edu.unh.cs980.variations.BigramAnalyzer;


import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

/********
 * This class is being used for contextual similarity with Query Expansion 
 * Date: 15th April 2018
 *
 */

public class Contextual_QueryExpansion{
	
	//instance variables
    private static int topkentities = 5; 
	private static int max_result = 100; // top 100 documents
	private static QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());
	private static String abstractindexdir;
	private static Integer time_out = 5;
   private static ExecutorService crawlers;
   
   /**
    * Function:  pass true if you want to give multithread optionas true.
    * @param queriesStr for string Query
    * @param index_dir for wherever index directory is located
    * @param abstract_index for abstract index location
    * @return
    * @throws IOException
    * @throws ParseException
    */
      public static ArrayList<String> TrueWithMultiThread(ArrayList<String> queriesStr, String index_dir,
			String abstract_index) throws IOException, ParseException {

		ArrayList<String> runFileString = new ArrayList<String>();
		ArrayList<ContexualQueryObj> queryList = new ArrayList<ContexualQueryObj>();
		abstractindexdir = abstract_index;
		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		try {
			queryList = convertToContextualExpanedQuery(queriesStr);
		

			int dupparaid = 0;
			for (ContexualQueryObj q_obj : queryList) {

				TopDocs tops = searcher.search(q_obj.getQuery(), max_result);
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				for (int i = 0; i < scoreDoc.length; i++) {
					ScoreDoc score = scoreDoc[i];
					Document doc = searcher.doc(score.doc);
					String paragraphId = doc.getField(PID).stringValue();     // change to PID
					float rankScore = score.score;
					int rank = i + 1;

					String runStr = "enwiki:" + q_obj.getQueryStr().replace(" ", "%20") + " Q0 " + paragraphId + " " + rank
							+ " " + rankScore + " Contextual_QueryExpansion";
					if (runFileString.contains(runStr)) {
						dupparaid++;
						
					} else {
						runFileString.add(runStr);
					}
				}

			}

		} catch (Exception e) {
			System.out.println("Error!!!! " + e.getLocalizedMessage());
		}

		return runFileString;
	}
      /*********************
       * Function: top K entities are being checked again query
       * @param queriesStr
       * @return queryList
       * @throws InterruptedException
       * @throws ExecutionException
       */
    private static ArrayList<ContexualQueryObj> convertToContextualExpanedQuery(ArrayList<String> queriesStr)
			throws InterruptedException, ExecutionException {

		crawlers = Executors.newCachedThreadPool();  //  Creates a thread pool that creates new threads as needed, but will reuse previously constructed threads when they are available.
		ArrayList<ContexualQueryObj> queryList = new ArrayList<ContexualQueryObj>();   // creating arraylist
		Collection<Callable<ContexualQueryObj>> tasks = new ArrayList<Callable<ContexualQueryObj>>();
		for (final String queryStr : queriesStr) {
			if (queriesStr != null) {
				tasks.add(new Callable<ContexualQueryObj>() {
					public ContexualQueryObj call() throws Exception {
					
						ArrayList<String> terms = getContextualExpandedQuery(topkentities, queryStr);

						Query expanded_query = generateWeightedQuery(queryStr, terms);
						ContexualQueryObj obj = new ContexualQueryObj();
						obj.setQueryStr(queryStr);
						obj.setQuery(expanded_query);

						return obj;
					}

				});
			}
		}

		List<Future<ContexualQueryObj>> results = crawlers.invokeAll(tasks, time_out, TimeUnit.SECONDS);
		for (Future<ContexualQueryObj> f : results) {
			if (f != null) {
				ContexualQueryObj result = f.get();
				queryList.add(result);
			}
		}
		crawlers.shutdown();      // shutting down the crawlers. 
		return queryList;
	}
	
	/**************
	 * 
	 * @param queriesStr
	 * @param index_dir
	 * @param abstract_index
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	
	public static ArrayList<String> getContextualResults(ArrayList<String> queriesStr, String index_dir, String abstract_index)
			throws IOException, ParseException {

		abstractindexdir = abstract_index;
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());
		
		int duplicate = 0;
		for (String queryStr : queriesStr) {
			ArrayList<String> expanded_entities = getContextualExpandedQuery(topkentities, queryStr);
			Query q_rm = generateWeightedQuery(queryStr, expanded_entities);

			TopDocs tops = searcher.search(q_rm, max_result);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField(PID).stringValue();   //PID
				float rankScore = score.score;
				int rank = i + 1;
                 
				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ rankScore + " Contextual_QueryExpansion";
				if (runFileStr.contains(runStr)) {
					duplicate++;
				
				} else {
					runFileStr.add(runStr);
				}
			}

		}

		return runFileStr;
	}
	/*******************
	 * Function: getContextualExpandedQuery
	 * @param top_k
	 * @param queryStr
	 * @return
	 * @throws IOException
	 */
	private static ArrayList<String> getContextualExpandedQuery(int top_k, String queryStr) throws IOException {
		
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
					abstractindexdir);
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
				String term = termList.get(i);    // assigning some factor value
				if (i == 0) {
					factor = 3;
				}
				if (i == termList.size() - 1) {
					factor = 2;
				}
				String abstracStr = EntityMethods.getEntityAbstract(term.replace(" ", "_").toLowerCase(),
						abstractindexdir);
				if (!abstracStr.isEmpty()) {
					ArrayList<EntityWord> entities = EntityMethods.getAnnotatedEntites(abstracStr);
					if (entities != null) {
						if (entities.size() > 0) {
							for (EntityWord entity : entities) {
								float score = (float) entity.getSimilarityScore() * factor;
								entityScore.put(entity.getSurfaceForm(), score);
							}
						} else {
							System.out.println("Can't find any  contextual entities for term: " + term + ". Skipped.");
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
	/*****************
	 * Function: getExpandedEntitiesFromPageQuery
	 * @param page_query
	 * @param top_k
	 * @param searcher
	 * @return
	 */

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

	
	/**********
	 * Function:getExpandedEntitiesListFromSectionQuery 
	 * @param section_query
	 * @param top_k
	 * @param abstract_index_dir
	 * @return
	 */

	public static HashMap<String, ArrayList<String>> getExpandedEntitiesListFromSectionQuery(String section_query,
			int top_k, String abstract_index_dir) {
		
		HashMap<String, ArrayList<String>> result_map = new HashMap<String, ArrayList<String>>();
		HashMap<String, Float> entityScore;

		List<String> termList = new ArrayList<String>();
		termList = Arrays.asList(section_query.split("/"));

		if (!termList.isEmpty()) {
			for (int i = 0; i < termList.size(); i++) {
				int factor = 1; 
				String term = termList.get(i);
				if (i == 0) {
					factor = 3;     //assigning factor value for upper tree
				}
				if (i == termList.size() - 1) {
					factor = 2;        // assigning factor value for lower tree
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
							System.out.println("Can't find any  contextual entities for term: " + term + ". Skipped.");
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
	/**************
	 * 
	 * @param initialQ
	 * @param rm_list
	 * @return
	 * @throws ParseException
	 */

	private static Query generateWeightedQuery(String initialQ, ArrayList<String> rm_list) throws ParseException {
		if (!rm_list.isEmpty()) {
			String rm_str = String.join(" ", rm_list);
			Query q = parser.parse(QueryParser.escape(initialQ) + "^0.5" + QueryParser.escape(rm_str) + "^0.2");
		
			return q;
		} else {
			Query q = parser.parse(QueryParser.escape(initialQ));
			
			return q;
		}
	}
	/*******
	 * 
	 * @param inputStr
	 * @return
	 * @throws IOException
	 */

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
	
	/************
	 * Function: runContextualSimilarityWithQueryExpansion
	 * @param indexLocation
	 * @param abstractIndexLocation
	 * @param queryFile
	 * @param qt
	 * @param threadChoice
	 * @param outputLocation
	 */
	
public void runContextualSimilarityWithQueryExpansion(String indexLocation, String abstractIndexLocation, String queryFile, String qt,
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

			if (queryType.equalsIgnoreCase("pages")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();

				if (runMultiThread) {
					ArrayList<String> page_run = Contextual_QueryExpansion.TrueWithMultiThread(pages_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				} else {
					ArrayList<String> page_run = Contextual_QueryExpansion.getContextualResults(pages_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				}

			} else {
				System.out.println("Error: ContextualQueryType could not be  recognized");
			}

		} catch (Throwable e) {
			System.out.println(e.getMessage());
		}
	}
	
}

