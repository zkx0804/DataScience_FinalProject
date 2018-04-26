package edu.unh.cs980.entityLinking;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import edu.unh.cs980.utils.ProjectUtils;

public class EntityMethods {
	private static Gson gson = ProjectUtils.getGsonStringBuilder();

	private static final Logger logger = Logger.getLogger(EntityMethods.class);

	public static String getEntityAbstract(String queryStr, String index_dir) {
		String content = "";
		try {
			IndexSearcher searcher = new IndexSearcher(
					DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
			searcher.setSimilarity(new BM25Similarity());
			return getEntityAbstract(queryStr, searcher, false);

		} catch (Exception e) {
			logger.error("Error when finding abstract. Throw: " + e.getMessage());
		}

		return content;
	}

	public static String getEntityAbstract(String queryStr, IndexSearcher searcher, Boolean shutUp) {
		String content = "";
		try {
			BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
			// String[] terms = queryStr.split(" ");
			// for (int i = 0; i < terms.length; i++) {
			// queryBuilder.add(new FuzzyQuery(new Term("name", terms[i])),
			// BooleanClause.Occur.SHOULD);
			//
			// }

			queryBuilder.add(new FuzzyQuery(new Term("name", queryStr)), BooleanClause.Occur.SHOULD);

			Query q = queryBuilder.build();

			QueryParser parser = new QueryParser("name", new StandardAnalyzer());

			TopDocs tops = searcher.search(q, 1);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			if (scoreDoc.length == 0) {
				return "";
			}
			ScoreDoc score = scoreDoc[0];
			Document doc = searcher.doc(score.doc);
			String name = doc.getField("name").stringValue();
			content = doc.getField("text").stringValue();
		} catch (Exception e) {
			logger.debug("Error when finding abstract. Throw: " + e.getMessage());
		}

		return content;
	}

	public static ArrayList<EntityWord> getAnnotatedEntites(String content) {
		ArrayList<EntityWord> results = new ArrayList<EntityWord>();
		try {
			String jsonStr = SpotLightClient.getAnootatedJson(content);

			JsonParser jsonParser = new JsonParser();
			JsonObject gsonObject = (JsonObject) jsonParser.parse(jsonStr);

			Type listType = new TypeToken<List<EntityWord>>() {
			}.getType();
			results = gson.fromJson(gsonObject.get("Resources"), listType);

		} catch (Exception e) {
			logger.debug("Can't get json response from Spotlight API.");
		}

		return results;
	}

}
