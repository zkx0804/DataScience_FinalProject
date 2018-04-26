package edu.unh.cs980.variations;

import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

import edu.unh.cs980.utils.ProjectUtils;

public class FreqBigram_variation {
	private static int max_results = 100;
	private static final Logger logger = Logger.getLogger(FreqBigram_variation.class);

	public static ArrayList<String> getSearchResult(ArrayList<String> queriesStr, String index_dir)
			throws IOException, ParseException {
		logger.info("Frequent Bigram ====> Retrieving results for " + queriesStr.size() + " queries...");
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());
		QueryParser parser2 = new QueryParser("bigram", new StandardAnalyzer());
		int duplicate = 0;
		for (String queryStr : queriesStr) {
			// Get bigram list for query
			ArrayList<String> bigram_list = analyzeByBigram(queryStr);

			// Create a HashMap to compute score for each document.
			// <documentId,score>
			HashMap<String, Float> score_map = new HashMap<String, Float>();

			if (bigram_list.isEmpty()) {
				logger.debug(queryStr + " ===>Single Word query found.");
				bigram_list.add(queryStr);
			}

			// Query against bigram field with BM25
			for (String term : bigram_list) {
				Query q = parser2.parse(QueryParser.escape(term));
				TopDocs tops = searcher.search(q, max_results);
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				for (int i = 0; i < scoreDoc.length; i++) {
					ScoreDoc score = scoreDoc[i];
					Document doc = searcher.doc(score.doc);
					String paraId = doc.getField(PID).stringValue();
					float rankScore = score.score;

					if (score_map.keySet().contains(paraId)) {
						score_map.put(paraId, score_map.get(paraId) + rankScore);

					} else {
						score_map.put(paraId, rankScore);
					}

				}
			}
			int test = 0;

			// Query against content field with BM25 and combine results with
			// bigram query.
			Query q = parser.parse(QueryParser.escape(queryStr));
			TopDocs tops = searcher.search(q, max_results);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField(PID).stringValue();
				// if (test == 0) {
				// logger.debug(doc.getField("content").stringValue());
				// logger.debug(doc.getField("bigram").stringValue());
				// }
				float rankScore = score.score;

				if (score_map.keySet().contains(paraId)) {
					score_map.put(paraId, score_map.get(paraId) + rankScore);

				} else {
					score_map.put(paraId, rankScore);
				}

			}

			int rank = 1;
			for (Map.Entry<String, Float> entry : ProjectUtils.getTopValuesInMap(score_map, max_results).entrySet()) {
				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + entry.getKey() + " " + rank + " "
						+ entry.getValue() + " FreqBigram";
				rank++;
				if (runFileStr.contains(runStr)) {
					duplicate++;
					// logger.debug("Found duplicate: " + runStr);
				} else {
					runFileStr.add(runStr);
				}
			}

		}

		logger.info("Frequent Bigram ====> Got " + runFileStr.size() + " results.");
		return runFileStr;
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
