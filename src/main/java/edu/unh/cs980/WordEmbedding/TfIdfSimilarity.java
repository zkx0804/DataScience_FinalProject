/*****
 * @author:Bindu Kumari
 * @Date:03/27/2018
 */
package edu.unh.cs980.WordEmbedding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import edu.unh.cs980.KotUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.FSDirectory;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Data.Page;

public class TfIdfSimilarity {

	private IndexSearcher searcher;
	private QueryParser parser;
	private int numDocs; // Number of documents to return
	private ArrayList<Data.Page> pageList; // List of pages to query
	private HashMap<Query, ArrayList<ResultQuery>> queryResults; 
	/***
	 * TFIDFSimilarity Constructor
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */

	public TfIdfSimilarity(ArrayList<Data.Page> pl, int n, String index) throws ParseException, IOException {
		String INDEX_DIRECTORY = index;
		numDocs = n;
		pageList = pl;

		// Need to use "text" field (CONTENT constant) instead of "parabody" field when working with server's index.
		parser = new QueryParser(KotUtils.CONTENT, new StandardAnalyzer());
		// parser = new QueryParser("parabody", new StandardAnalyzer());

		searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIRECTORY).toPath()))));

		// Setting our own similarity class which computes
		searcher.setSimilarity(createSimilarity());
	}

	// Variant constructor that accepts an existing index searcher and does not require pages
	public TfIdfSimilarity(int n, IndexSearcher indexSearcher)  {
		numDocs = n;
		parser = new QueryParser(KotUtils.CONTENT, new StandardAnalyzer());
        searcher = indexSearcher;
		searcher.setSimilarity(createSimilarity());
	}

	private SimilarityBase createSimilarity() {
		return new SimilarityBase() {
			protected float score(BasicStats stats, float freq, float docLen) {
				return (float) (1 + Math.log10(freq));
			}

			@Override
			public String toString() {
				return null;
			}
		};
	}


	/*
	 * @Function: SplitThePageName
	 *
	 * @Call: Another function calculateScore
	 *
	 */

	public List<List<Double>> getQueryScore(List<TermQuery> termQueries, TopDocs tops)
			throws ParseException, IOException {

		HashMap<TermQuery, Float> queryweights = new HashMap<>();
		for (TermQuery tq: termQueries) {
			queryweights.put(tq, queryweights.getOrDefault(tq, 0.0f) + 1.0f);
		}

		return calculateQueryScore(termQueries, queryweights, tops);
	}

	private List<List<Double>> calculateQueryScore(List<TermQuery> terms,
			HashMap<TermQuery, Float> queryweights2, TopDocs tops) throws IOException, ParseException {

		List<List<Double>> ls = new ArrayList<List<Double>>();

		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());
		for (TermQuery queryterm : terms) { // For every Term

			// Get our Index Reader for helpful statistics
			IndexReader reader = searcher.getIndexReader();

			// If document frequency is zero, set DF to 1; else, set DF to
			// document frequency
			float DF = (reader.docFreq(queryterm.getTerm()) == 0) ? 1 : reader.docFreq(queryterm.getTerm());

			// Calculate TF-IDF for the query vector
			float qTF = (float) (1 + Math.log10(queryweights2.get(queryterm))); // Logarithmic
			// term
			// frequency
			float qIDF = (float) (Math.log10(reader.numDocs() / DF)); // Logarithmic
																		// inverse
																		// document
																		// frequency
			float qWeight = qTF * qIDF;

			// Store query weight for later calculations
			queryweights2.put(queryterm, qWeight);

			// Get the top 100 documents that match our query
//			TopDocs tpd = searcher.search(queryterm, numDocs);

			ls.add(getTermScores(tops, queryweights2, queryterm));

		}
		return ls;
	}
	
	/*********
	 * Function:SplitpageName
	 * Description: This
	 * @param page
	 * @param runfile
	 * @throws IOException
	 * @throws ParseException
	 */
	
	public void SplitPageName(Page page, String runfile) throws IOException, ParseException {
		// TODO Auto-generated method stub
		String qid = page.getPageId();
		String qname = page.getPageName();
		HashMap<TermQuery, Float> queryweights = new HashMap<>();
		ArrayList<TermQuery> terms = new ArrayList<>();

		Query q = parser.parse(page.getPageName());

		for (String term : page.getPageName().split(" "))

		{
			TermQuery tq = new TermQuery(new Term("text", term));

			terms.add(tq);

			queryweights.put(tq, queryweights.getOrDefault(tq, 0.0f) + 1.0f);
		}

		calculateScore(qid, q, qname, terms, runfile, queryweights);

	}
	
	
	

	public void calculateScore(String qid, Query q, String qname, ArrayList<TermQuery> terms, String runfile,
			HashMap<TermQuery, Float> queryweights2) throws IOException, ParseException {

		List<Double> ls = new ArrayList<Double>();

		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());
		for (TermQuery queryterm : terms) { // For every Term

			// Get our Index Reader for helpful statistics
			IndexReader reader = searcher.getIndexReader();

			// If document frequency is zero, set DF to 1; else, set DF to
			// document frequency
			float DF = (reader.docFreq(queryterm.getTerm()) == 0) ? 1 : reader.docFreq(queryterm.getTerm());

			// Calculate TF-IDF for the query vector
			float qTF = (float) (1 + Math.log10(queryweights2.get(queryterm))); // Logarithmic
			// term
			// frequency
			float qIDF = (float) (Math.log10(reader.numDocs() / DF)); // Logarithmic
																		// inverse
																		// document
																		// frequency
			float qWeight = qTF * qIDF;

			// Store query weight for later calculations
			queryweights2.put(queryterm, qWeight);

			// Get the top 100 documents that match our query
			TopDocs tpd = searcher.search(queryterm, numDocs);

			ls = doScoring(qid, qname, tpd, queryweights2, queryterm, runfile);
			

		}

	}

	private List<Double> getTermScores(TopDocs tpd, HashMap<TermQuery, Float> queryweights2,
									   TermQuery queryterm) throws IOException {
		List<Double> ls1 = new ArrayList<Double>();
		HashMap<Document, ResultQuery> docMap = new HashMap<>();
		HashMap<Document, Float> scores = new HashMap<>(); // Mapping of each
		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());

		for (int i = 0; i < tpd.scoreDocs.length; i++) { // For every
			// returned
			// document...
			Document doc = searcher.doc(tpd.scoreDocs[i].doc); // Get
			// the
			// document
//			double score = tpd.scoreDocs[i].score * queryweights2.get(queryterm); // Calculate

			BooleanQuery q = new BooleanQuery.Builder()
					.add(queryterm, BooleanClause.Occur.SHOULD)
					.build();

			double score = searcher.explain(q, tpd.scoreDocs[i].doc).getValue() * queryweights2.get(queryterm); // Calculate
			// TF-IDF
			ResultQuery dResults = docMap.get(doc);
			if (dResults == null) {
				dResults = new ResultQuery(doc);
			}

			float prevScore = dResults.getScore();
			dResults.score((float) (prevScore + score));
			docMap.put(doc, dResults);

			// Store score for later use
			scores.put(doc, (float) (prevScore + score));

		}
		float cosineLength = 0.0f;
		for (Map.Entry<Document, Float> entry : scores.entrySet()) {
			Float score = entry.getValue();

			cosineLength = (float) (cosineLength + Math.pow(score, 2));
		}
		cosineLength = (float) (Math.sqrt(cosineLength));

		// Normalization of scores
		for (Map.Entry<Document, Float> entry : scores.entrySet()) {

			Document doc = entry.getKey();
			Float score = entry.getValue();

			// Normalize the score
			scores.put(doc, score / scores.size());

			ls1.add((double) (score / scores.size()));    //Creating list for combined Method
			// System.out.println(score/scores.size());
			ResultQuery dResults = docMap.get(doc);
			dResults.score(dResults.getScore() / cosineLength);

			docQueue.add(dResults);
		}
		return ls1;
		
	}

	private List<Double> doScoring(String qid, String qname, TopDocs tpd, HashMap<TermQuery, Float> queryweights2,
			TermQuery queryterm, String runfile) throws ParseException, IOException {
		// TODO Auto-generated method stub
		HashMap<Document, ResultQuery> docMap = new HashMap<>();
		HashMap<Document, Float> scores = new HashMap<>(); // Mapping of each
		ArrayList<ResultQuery> docResults = new ArrayList<>();
		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());
		
		List<Double> ls1 = new ArrayList<Double>();

		Query q = parser.parse(qname);

		for (int i = 0; i < tpd.scoreDocs.length; i++) { // For every
			// returned
			// document...
			Document doc = searcher.doc(tpd.scoreDocs[i].doc); // Get
			// the
			// document
			double score = tpd.scoreDocs[i].score * queryweights2.get(queryterm); // Calculate
			// TF-IDF
			ResultQuery dResults = docMap.get(doc);
			if (dResults == null) {
				dResults = new ResultQuery(doc);
			}

			float prevScore = dResults.getScore();
			dResults.score((float) (prevScore + score));
			dResults.queryId(qid);
			dResults.paragraphId(doc.getField("paragraphid").stringValue());
			dResults.teamName("Team1 ");
			dResults.methodName("TF-IDFSimilarity");

			docMap.put(doc, dResults);

			// Store score for later use
			scores.put(doc, (float) (prevScore + score));

		}
		float cosineLength = 0.0f;
		for (Map.Entry<Document, Float> entry : scores.entrySet()) {
			Float score = entry.getValue();

			cosineLength = (float) (cosineLength + Math.pow(score, 2));
		}
		cosineLength = (float) (Math.sqrt(cosineLength));

		// Normalization of scores
		for (Map.Entry<Document, Float> entry : scores.entrySet()) {

			Document doc = entry.getKey();
			Float score = entry.getValue();

			// Normalize the score
			scores.put(doc, score / scores.size());

			ls1.add((double) (score / scores.size()));    //Creating list for combined Method
			// System.out.println(score/scores.size());
			ResultQuery dResults = docMap.get(doc);
			dResults.score(dResults.getScore() / cosineLength);

			docQueue.add(dResults);
		}

		int rankCount = 0;
		ResultQuery current;
		while ((current = docQueue.poll()) != null) {
			current.rank(rankCount);
			docResults.add(current);
			rankCount++;
		}

		// Map our Documents and scores to the corresponding query

		queryResults.put(q, docResults);
         writeResults(queryResults, runfile);
		return ls1;

	}

	private void writeResults(HashMap<Query, ArrayList<ResultQuery>> queryResults2, String runfile) throws IOException {
		// TODO Auto-generated method stub

		FileWriter runfileWriter = new FileWriter(new File(runfile));
		for (Map.Entry<Query, ArrayList<ResultQuery>> results : queryResults.entrySet()) {
			ArrayList<ResultQuery> list = results.getValue();

			for (int i = 0; i < list.size(); i++) {

				ResultQuery dr = list.get(i);

				runfileWriter.write(dr.getRunfileString());
			}
		}
		runfileWriter.close();

	}

	/**
	 * @function:writeTFIDFScoresTo
	 * @param runfile
	 *            The name of the run file output to
	 * @Description: Main function of the Similarity which calculates similarity
	 *               score of paragraph
	 * @throws IOException,ParseException
	 * 
	 */
	public void writeTFIDFScoresTo(String runfile) throws IOException, ParseException {

		queryResults = new HashMap<>(); // Maps query to map of Documents with
		// TF-IDF score

		for (Data.Page page : pageList) {

			SplitPageName(page, runfile);

		}
        System.out.println("TF_IDFSimilarity writing results to: \t\t" + runfile);

	}
}

/**
 * @class:ResultComparator Description: To compare the result query strings
 * @return return result comparision based on methods
 */

class ResultComparator implements Comparator<ResultQuery> {
	public int compare(ResultQuery d2, ResultQuery d1) {
		if (d1.getScore() < d2.getScore())
			return -1;
		if (d1.getScore() == d2.getScore())
			return 0;
		return 1;
	}
}
