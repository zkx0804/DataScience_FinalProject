package edu.unh.cs980.variations;

import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.apache.lucene.store.FSDirectory;

import com.google.common.collect.Lists;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.unh.cs980.entityLinking.EntityMethods;
import edu.unh.cs980.entityLinking.EntityWord;
import edu.unh.cs980.nlp.NL_Document.Paragraph;
import edu.unh.cs980.nlp.NL_Processor;

public class NLP_query_variation {
	// Run with BM25, extracted the relations from content.
	// Rank the document that entities has relations with entities in query
	// higher.
	// Re rank the file.

	private static int max_result = 20; // Max number for Lucene docs
	private static QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());

	private static final Logger logger = Logger.getLogger(NLP_query_variation.class);
	private static NL_Processor NLPpipeline = NL_Processor.getInstance();
	private static ExecutorService workers;
	private static Integer thread_timeout = 30;
	private static Integer pool_size = 20;
	private static float obj_factor = (float) 0.6;
	private static float subj_factor = (float) 0.4;

	public static ArrayList<String> getResults(ArrayList<String> queriesStr, String index_dir)
			throws IOException, ParseException {

		logger.info("NLP query variation ====> Retrieving results for " + queriesStr.size() + " queries...");
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		int duplicate = 0;
		int queryCount = 1;
		for (String queryStr : queriesStr) {
			logger.debug("Processing query " + queryCount + " / " + queriesStr.size() + " ...");
			queryCount++;
			Query q = parser.parse(QueryParser.escape(queryStr));
			HashMap<Document, Float> scoredDocs = new HashMap<>();
			TopDocs init_tops = searcher.search(q, max_result);
			ScoreDoc[] scoreDoc = init_tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				// String paraId = doc.getField(PID).stringValue();
				String paraContent = doc.getField(CONTENT).stringValue();
				float init_score = score.score;
				// List<String> entities =
				// Arrays.asList(doc.getValues("spotlight"));

				Paragraph paraObj = NLPpipeline.convertToNL_DocumentWithOpenIE(paraContent);
				ArrayList<EntityWord> spotLight_entities = EntityMethods.getAnnotatedEntites(queryStr);
				List<String> entities = new ArrayList<String>();
				// Reduce the boots factor, when spotlight can't find any
				// entities.
				// Else, keep the origin boots.
				float reduce_factor = (float) 1;
				if (spotLight_entities == null || spotLight_entities.isEmpty()) {
					reduce_factor = (float) 0.5;
					// // Check if it's section path query
					if (queryStr.contains("/")) {

						entities = Arrays.asList(queryStr.split("/"));
					} else {
						entities.add(queryStr);
					}
				} else {
					for (EntityWord e : spotLight_entities) {
						entities.add(e.getSurfaceForm().toLowerCase());
					}
				}

				// if (queryStr.contains("/")) {
				// entities = Arrays.asList(queryStr.split("/"));
				// } else {
				// entities.add(queryStr);
				// }

				// Caculate score
				float final_score = getRerankedScore(init_score, reduce_factor, entities, paraObj);
				scoredDocs.put(doc, final_score);
			}

			// Re-rank the documents with new scores.
			int rank = 1;
			HashMap<Document, Float> rankedDocs = getSortedDocs(scoredDocs);
			for (Entry<Document, Float> entry : rankedDocs.entrySet()) {
				Document doc = entry.getKey();
				String paraId = doc.getField(PID).stringValue();

				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ entry.getValue() + " NLP_query_variation";
				if (runFileStr.contains(runStr)) {
					duplicate++;
					// logger.debug("Found duplicate: " + runStr);
				} else {
					runFileStr.add(runStr);
				}

				rank++;
			}

		}

		logger.info(
				"NLP query variation ====> Got " + runFileStr.size() + " results. Found " + duplicate + " duplicates.");

		return runFileStr;

	}

	private static float getRerankedScore(Float init_score, Float reduce_factor, List<String> entities,
			Paragraph paraObj) {

		// Get a list of relations in this paragraph. <Entity, Relation, Entity>
		ArrayList<RelationTriple> relations = paraObj.getAllRelationsTriple();
		float obj_score = (float) 0.0;
		float subj_score = (float) 0.0;
		for (String entity : entities) {
			List<Integer> counts = getParticipatedNumber(entity, relations);

			int obj_c = counts.get(0);
			int subj_c = counts.get(1);

			obj_score = +(float) obj_c * obj_factor * reduce_factor;
			subj_score = +(float) subj_c * subj_factor * reduce_factor;
		}

		float final_score = init_score + obj_score + subj_score;

		return final_score;
	}

	// Count the number of how many times that this entity participate in
	// relations as an object and as a subject
	// Output as List[NumOfObject, NumOfSubject]
	// Object counts = List[0]
	// Subject counts = List[1]
	private static List<Integer> getParticipatedNumber(String entity, ArrayList<RelationTriple> relations) {
		int objCount = 0;
		int subCount = 0;

		if (!relations.isEmpty()) {
			entity = entity.toLowerCase();
			for (RelationTriple rel : relations) {
				String object = rel.objectGloss().toLowerCase();
				String subject = rel.subjectGloss().toLowerCase();

				if (object.contains(entity) || entity.contains(object)) {
					objCount++;
				}
				if (subject.contains(entity) || entity.contains(subject)) {
					subCount++;
				}
			}
		}

		List<Integer> counts = new ArrayList<Integer>(2);
		counts.add(objCount);
		counts.add(subCount);

		return counts;
	}

	// Sort in DESC order
	private static HashMap<Document, Float> getSortedDocs(HashMap<Document, Float> unsortedMap) {

		List<Map.Entry<Document, Float>> list = new LinkedList<Map.Entry<Document, Float>>(unsortedMap.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<Document, Float>>() {

			public int compare(Map.Entry<Document, Float> o1, Map.Entry<Document, Float> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});

		HashMap<Document, Float> sortedMap = new LinkedHashMap<Document, Float>();
		int i = 0;
		for (Map.Entry<Document, Float> entry : list)

		{
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}

	// Create thread for a sublist of queries.
	public static ArrayList<String> getResultsWithMultiThread(ArrayList<String> queriesStr, String index_dir)
			throws InterruptedException, ExecutionException {
		ArrayList<String> runfileStr = new ArrayList<>();
		workers = Executors.newFixedThreadPool(pool_size);
		logger.info("Create excuter for threads. Size: " + pool_size);
		// workers = Executors.newCachedThreadPool();
		Collection<Callable<ArrayList<String>>> tasks = new ArrayList<Callable<ArrayList<String>>>();
		for (List<String> partialQuery : Lists.partition(queriesStr, 50)) {
			if (!partialQuery.isEmpty()) {
				logger.debug("Create thread for partial queryString.");
				tasks.add(new Callable<ArrayList<String>>() {

					public ArrayList<String> call() throws Exception {
						// TODO Auto-generated method stub
						ArrayList<String> partial_runfile = new ArrayList<>();
						partial_runfile = getResults(Lists.newArrayList(partialQuery), index_dir);
						return partial_runfile;
					}

				});
			}
		}

		List<Future<ArrayList<String>>> results = workers.invokeAll(tasks, thread_timeout, TimeUnit.MINUTES);
		for (Future<ArrayList<String>> f : results) {
			if (f != null) {
				ArrayList<String> partial_result = f.get();
				runfileStr.addAll(partial_result);
			}
		}
		logger.info("Found total " + runfileStr.size() + " results by multi-thread methods.");

		workers.shutdown();
		return runfileStr;
	}

	// Create thread for each single query string.
	public static ArrayList<String> getResultsWithMultiThread2(ArrayList<String> queriesStr, String index_dir)
			throws InterruptedException, ExecutionException {
		ArrayList<String> runfileStr = new ArrayList<>();
		workers = Executors.newFixedThreadPool(pool_size);
		// workers = Executors.newCachedThreadPool();
		Collection<Callable<ArrayList<String>>> tasks = new ArrayList<Callable<ArrayList<String>>>();
		for (String query : queriesStr) {
			logger.debug("Create thread for query: " + query);
			tasks.add(new Callable<ArrayList<String>>() {

				public ArrayList<String> call() throws Exception {
					// TODO Auto-generated method stub
					ArrayList<String> partial_runfile = new ArrayList<>();
					partial_runfile = getResultsForSingleQuery(query, index_dir);

					logger.info("Processing query: " + query + " /nFound " + partial_runfile.size() + " results.");
					return partial_runfile;
				}

			});
		}

		List<Future<ArrayList<String>>> results = workers.invokeAll(tasks, thread_timeout, TimeUnit.MINUTES);
		for (Future<ArrayList<String>> f : results) {
			if (f != null) {
				ArrayList<String> partial_result = f.get();
				runfileStr.addAll(partial_result);
			}
		}
		workers.shutdown();
		return runfileStr;
	}

	private static ArrayList<String> getResultsForSingleQuery(String queryStr, String index_dir)
			throws IOException, ParseException {

		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		int duplicate = 0;
		Query q = parser.parse(QueryParser.escape(queryStr));
		HashMap<Document, Float> scoredDocs = new HashMap<>();
		TopDocs init_tops = searcher.search(q, max_result);
		ScoreDoc[] scoreDoc = init_tops.scoreDocs;
		for (int i = 0; i < scoreDoc.length; i++) {
			ScoreDoc score = scoreDoc[i];
			Document doc = searcher.doc(score.doc);
			// String paraId = doc.getField(PID).stringValue();
			String paraContent = doc.getField(CONTENT).stringValue();
			float init_score = score.score;
			// List<String> entities =
			// Arrays.asList(doc.getValues("spotlight"));

			Paragraph paraObj = NLPpipeline.convertToNL_DocumentWithOpenIE(paraContent);
			// ArrayList<EntityWord> spotLight_entities =
			// EntityMethods.getAnnotatedEntites(queryStr);
			List<String> entities = new ArrayList<String>();
			// Reduce the boots factor, when spotlight can't find any
			// entities.
			// Else, keep the origin boots.
			float reduce_factor = (float) 1;
			// if (spotLight_entities == null ||
			// spotLight_entities.isEmpty()) {
			// reduce_factor = (float) 0.5;
			// // Check if it's section path query
			// if (queryStr.contains("/")) {
			//
			// entities = Arrays.asList(queryStr.split("/"));
			// } else {
			// entities.add(queryStr);
			// }
			// } else {
			// for (EntityWord e : spotLight_entities) {
			// entities.add(e.getSurfaceForm().toLowerCase());
			// }
			// }

			if (queryStr.contains("/")) {
				entities = Arrays.asList(queryStr.split("/"));
			} else {
				entities.add(queryStr);
			}

			// Caculate score
			float final_score = getRerankedScore(init_score, reduce_factor, entities, paraObj);
			scoredDocs.put(doc, final_score);
		}

		// Re-rank the documents with new scores.
		int rank = 1;
		HashMap<Document, Float> rankedDocs = getSortedDocs(scoredDocs);
		for (Entry<Document, Float> entry : rankedDocs.entrySet()) {
			Document doc = entry.getKey();
			String paraId = doc.getField(PID).stringValue();

			String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
					+ entry.getValue() + " NLP_query_variation";
			if (runFileStr.contains(runStr)) {
				duplicate++;
				// logger.debug("Found duplicate: " + runStr);
			} else {
				runFileStr.add(runStr);
			}

			rank++;
		}

		logger.info(
				"NLP query variation ====> Got " + runFileStr.size() + " results. Found " + duplicate + " duplicates.");

		return runFileStr;
	}

}
