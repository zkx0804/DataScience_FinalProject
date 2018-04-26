package edu.unh.cs980.WordEmbedding;

/***This class is being used for Word embedding of paragraph corpus*/

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.FSDirectory;
import edu.unh.cs.treccar_v2.Data;

class WordEmbedding {
	QueryParser qp;
	IndexSearcher is;
	IndexReader ir;
	int maxResults;
	HashMap<String, HashMap<String, Float>> results; // final results
														// //QueryId-paraid-score
	ArrayList<String> runFileParameter;

	/**
	 * Constructor for the ranking system.
	 * 
	 * @param pagelist
	 *            the list of pages to rank
	 */
	WordEmbedding(ArrayList<Data.Page> pagelist, int numResults) throws IOException {
		runFileParameter = new ArrayList<>();
		results = new HashMap<>();
		maxResults = numResults;
		Cosine c=new Cosine(1); // Bindu, fix this
		qp = new QueryParser("text", new StandardAnalyzer());
		is = new IndexSearcher(DirectoryReader.open((FSDirectory.open(new File(MainClass.INDEX_DIRECTORY).toPath()))));
		ir = is.getIndexReader();
		float sumTotalTermFreq = ir.getSumTotalTermFreq("text");

		SimilarityBase custom = new SimilarityBase() {
			protected float score(BasicStats stats, float freq, float docLen) {

				return (float) ((freq + 1 / docLen));
			}

			@Override
			public String toString() {
				return null;
			}
		};
		is.setSimilarity(custom);

		// For each page in the list
		for (Data.Page page : pagelist) {
			// For every term in the query
			String queryId = page.getPageId();

			if (!results.containsKey(queryId)) {
				results.put(queryId, new HashMap<String, Float>());
			}
			for (String term : page.getPageName().split(" ")) {
				Term t = new Term("text", term);
				TermQuery tQuery = new TermQuery(t);

				TopDocs topDocs = is.search(tQuery, maxResults);
				float totalTermFreq = ir.totalTermFreq(t);
				ScoreDoc[] scores = topDocs.scoreDocs;
				for (int i = 0; i < topDocs.scoreDocs.length; i++) {

					Document doc = is.doc(scores[i].doc);
					String paraId = doc.get("paragraphid");
					String docBody = doc.get("text");
					ArrayList<String> wordembedding_list = analyzeByWordEmbedding(docBody);
					int size_of_voc = getSizeOfVocabulary(wordembedding_list);
					int size_of_doc = wordembedding_list.size();

					if (!results.get(queryId).containsKey(paraId)) {
						results.get(queryId).put(paraId, 0.0f);
					}
					
					String resultQueryId=queryId;
					String paragraphId=paraId;
					float score = results.get(queryId).get(paraId);
					
					double score1=c.similarity(resultQueryId,paragraphId);
				    score += (float) ((scores[i].score+score1 / (size_of_doc + size_of_voc)));
					results.get(queryId).put(paraId, score);
				}
			}
		}

		for (Map.Entry<String, HashMap<String, Float>> queryResult : results.entrySet()) {
			String queryId = queryResult.getKey();
			HashMap<String, Float> paraResults = queryResult.getValue();

			for (Map.Entry<String, Float> paraResult : paraResults.entrySet()) {
				String paraId = paraResult.getKey();
				float score = paraResult.getValue();
				DocumentResults docResult = new DocumentResults(paraId, score);
				docQueue.add(docResult);
			}
			DocumentResults docResult;
			int count = 0;
			while ((docResult = docQueue.poll()) != null) {
				runFileParameter.add(queryId + "  Q0 " + docResult.paraId + " " + count + " " + docResult.score + " Word-Embedding");
				count++;
				if (count >= 100)
					break;
			}
			docQueue.clear();
		}
	}

	/***** To compare the document **/

	PriorityQueue<DocumentResults> docQueue = new PriorityQueue<DocumentResults>(new Comparator<DocumentResults>() {
		@Override
		public int compare(DocumentResults d1, DocumentResults d2) {
			if (d1.score < d2.score)
				return 1;
			if (d1.score > d2.score)
				return -1;
			return 0;
		}
	});

	/*** Get the size of the vocabulary */
	private static int getSizeOfVocabulary(ArrayList<String> unigramList) {
		ArrayList<String> list = new ArrayList<String>();
		Set<String> hs = new HashSet<>();

		hs.addAll(unigramList);
		list.addAll(hs);
		return list.size();
	}

	/*** WordEmbedding Analyzer */

	private static ArrayList<String> analyzeByWordEmbedding(String inputStr) throws IOException {
		Reader reader = new StringReader(inputStr);

		ArrayList<String> strList = new ArrayList<String>();
		Analyzer analyzer = new WordEmbeddingAnalyzer();
		TokenStream tokenizer = analyzer.tokenStream("content", inputStr);

		CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
		tokenizer.reset();
		while (tokenizer.incrementToken()) {
			String token = charTermAttribute.toString();
			strList.add(token);
		}
		tokenizer.end();
		tokenizer.close();
		return strList;
	}
  
	
    /** 
     * @Class: DocumentResults
     * @Description :creating a class with member paraId and score.
     *
     */
	class DocumentResults {
		String paraId;
		float score;

		DocumentResults(String pid, float s) {
			paraId = pid;
			score = s;
		}
	}

	ArrayList<String> getResults() {
		return runFileParameter;
	}
}