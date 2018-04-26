package edu.unh.cs980.variations;

import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

import edu.unh.cs980.utils.ProjectUtils;

// 1. predict relevant entities  through entity linking paragraphs of the feedback run
// 2. Expand query with top 5 entities, run against paragraph index using BM25.
public class Doc_RM_QE_variation {
	private static int top_k_entities = 5; // Include top k entities for QE
	private static int top_k_doc = 10; // Initial top k documents for QE
	private static int max_result = 100; // Max number for Lucene docs
	private static QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());

	private static final Logger logger = Logger.getLogger(Doc_RM_QE_variation.class);

	public static ArrayList<String> getResults(ArrayList<String> queriesStr, String index_dir)
			throws IOException, ParseException {
		logger.info("Doc_RM + QueryExpansion ====> Retrieving results for " + queriesStr.size() + " queries...");

		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		int duplicate = 0;
		for (String queryStr : queriesStr) {
			Query q0 = parser.parse(QueryParser.escape(queryStr));

			TopDocs init_tops = searcher.search(q0, top_k_doc);
			ScoreDoc[] init_scoreDoc = init_tops.scoreDocs;

			// Get top k entities with relevance mode
			ArrayList<String> expanded_entities = getExpandedTerms(top_k_entities, searcher, init_scoreDoc);
			logger.debug(queryStr + " ====> " + String.join(", ", expanded_entities));
			// Create new expanded query
			Query q_rm = generateWeightedQuery(queryStr, expanded_entities);
			// logger.debug(queryStr + " ====> " + expanded_entities);
			TopDocs tops = searcher.search(q_rm, max_result);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField(PID).stringValue();
				float rankScore = score.score;
				int rank = i + 1;

				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ rankScore + " Doc_RM_QE";
				if (runFileStr.contains(runStr)) {
					duplicate++;
					// logger.debug("Found duplicate: " + runStr);
				} else {
					runFileStr.add(runStr);
				}
			}
		}

		logger.info("Doc_RM + QueryExpansion ====> Got " + runFileStr.size() + " results. Found " + duplicate
				+ " duplicates.");

		return runFileStr;
	}

	private static ArrayList<String> getExpandedTerms(int top_k, IndexSearcher searcher, ScoreDoc[] scoreDoc)
			throws IOException {
		ArrayList<String> q_rm = new ArrayList<String>();
		HashMap<String, Float> entity_map = new HashMap<String, Float>();

		for (int i = 0; i < scoreDoc.length; i++) {
			ScoreDoc score = scoreDoc[i];
			Document doc = searcher.doc(score.doc);
			String paraId = doc.getField(PID).stringValue();
			List<String> entityList = Arrays.asList(doc.getValues("spotlight"));
			if (entityList.isEmpty()) {
				// logger.debug("Can't get entities from doc: " + paraId);
				// String content = doc.getField(CONTENT).stringValue();
				// logger.debug(content);
			} else {
				int rank = i + 1;
				float initial_p = (float) 1 / (rank + 1);

				for (String entity : getVocabularyList(entityList)) {
					int tf_w = countExactStrFreqInList(entity, entityList);
					int tf_list = entityList.size();
					float entity_score = initial_p * ((float) tf_w / tf_list);
					if (entity_map.keySet().contains(entity.replace("_", " "))) {
						entity_map.put(entity.replace("_", " "),
								entity_map.get(entity.replace("_", " ")) + entity_score);

					} else {
						entity_map.put(entity.replace("_", " "), entity_score);
					}

				}

			}
		}

		Set<String> entitySet = ProjectUtils.getTopValuesInMap(entity_map, top_k_entities).keySet();
		q_rm.addAll(entitySet);
		return q_rm;
	}

	// Boost q0 with 0.6, expanded query with 0.4
	private static Query generateWeightedQuery(String initialQ, ArrayList<String> rm_list) throws ParseException {
		if (!rm_list.isEmpty()) {
			String rm_str = String.join(" ", rm_list);
			Query q = parser.parse(QueryParser.escape(initialQ) + "^0.6" + QueryParser.escape(rm_str) + "^0.4");
			return q;
		} else {
			Query q = parser.parse(QueryParser.escape(initialQ));
			return q;
		}
	}

	// Get exact count.
	private static int countExactStrFreqInList(String entity, List<String> list) {
		int occurrences = Collections.frequency(list, entity);
		return occurrences;
	}

	private static ArrayList<String> getVocabularyList(List<String> entityList) {
		ArrayList<String> list = new ArrayList<String>();
		Set<String> hs = new HashSet<>();
		hs.addAll(entityList);
		list.addAll(hs);
		return list;
	}
}
