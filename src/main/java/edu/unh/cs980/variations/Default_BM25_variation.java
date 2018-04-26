package edu.unh.cs980.variations;

import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

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

public class Default_BM25_variation {

	private static final Logger logger = Logger.getLogger(Default_BM25_variation.class);

	public static ArrayList<String> getSearchResult(ArrayList<String> queriesStr, int max_result, String index_dir)
			throws IOException, ParseException {
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());
		int duplicate = 0;
		for (String queryStr : queriesStr) {
			Query q = parser.parse(QueryParser.escape(queryStr));

			TopDocs tops = searcher.search(q, max_result);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField(PID).stringValue();
				float rankScore = score.score;
				int rank = i + 1;

				if (i == 0) {
					String[] spotlight = doc.getValues("spotlight");
					String content = doc.getField("text").stringValue();
					for (int j = 0; j < spotlight.length; j++) {
						logger.debug(spotlight[j]);
					}
					logger.debug(content);
				}

				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ rankScore + " BM25";
				if (runFileStr.contains(runStr)) {
					duplicate++;
				} else {
					runFileStr.add(runStr);
				}
				logger.debug("Found " + duplicate + " duplicates");
			}
		}

		return runFileStr;
	}
}