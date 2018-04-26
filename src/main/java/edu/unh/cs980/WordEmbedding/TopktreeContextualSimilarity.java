package edu.unh.cs980.WordEmbedding;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Data.Page;
import info.debatty.java.stringsimilarity.NGram;

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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import java.io.*;
import java.util.*;


import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

/**
 * @class:ResultComparator Description: To compare the result query strings
 * @return return result comparision based on Paragraph
 */

 class DocumentResultComparator implements Comparator<ResultQuery> {
	public int compare(ResultQuery d2, ResultQuery d1) {
		if (d1.getScore() < d2.getScore())
			return -1;
		if (d1.getScore() == d2.getScore())
			return 0;
		return 1;
	}
}
/*********
 * 
 * @author :Bindu Kuamri 
 *
 */

public class TopktreeContextualSimilarity {

	private IndexSearcher searcher;
	private QueryParser parser;
	private int numDocs;
	private ArrayList<Data.Page> pageList;
	private HashMap<Query, ArrayList<ResultQuery>> ContextualParagraphqueryResults;

public TopktreeContextualSimilarity(ArrayList<Data.Page> pl, int n, String index) throws ParseException, IOException {
		String INDEX_DIRECTORY = index;
		numDocs = n;
		pageList = pl;

		parser = new QueryParser(CONTENT, new StandardAnalyzer());

		searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIRECTORY).toPath()))));
		SimilarityBase tfidf = new SimilarityBase() {
			protected float score(BasicStats stats, float freq, float docLen) {
				return (float) (1 + Math.log10(freq));
			}

			@Override
			public String toString() {
				return null;
			}
		};
		searcher.setSimilarity(tfidf);
	}

	/*****
	 * Function:ChooseTopKWords
	 * 
	 * @param page
	 * @param runfile
	 * @throws IOException
	 * @throws ParseException
	 */

	public void ChooseTopKWords(Page page, String runfile) throws IOException, ParseException {

		String qid = page.getPageId();
		String qname = page.getPageName();
		HashMap<TermQuery, Float> queryweights = new HashMap<>();
		ArrayList<TermQuery> terms = new ArrayList<>();

		Query q = parser.parse(page.getPageName());

		for (String term : page.getPageName().split(" "))

		{
			TermQuery tq = new TermQuery(new Term(CONTENT, term));
			terms.add(tq);
			queryweights.put(tq, queryweights.getOrDefault(tq, 0.0f) + 1.0f);
		}

		calculateParagraphScore(qid, q, qname, terms, runfile, queryweights);

	}

	/*****
	 * Function:calculate ParagraphScore
	 * 
	 * @param qid
	 * @param q
	 * @param qname
	 * @param terms
	 * @param runfile
	 * @throws IOException
	 * @throws ParseException
	 */

	public void calculateParagraphScore(String qid, Query q, String qname, ArrayList<TermQuery> terms, String runfile,
			HashMap<TermQuery, Float> queryweights2) throws IOException, ParseException {

		for (TermQuery queryterm : terms) { // For every Term

			IndexReader reader = searcher.getIndexReader();

			float DF = (reader.docFreq(queryterm.getTerm()) == 0) ? 1 : reader.docFreq(queryterm.getTerm());

			float qTF = (float) ((queryweights2.get(queryterm)));

			float qIDF = (float) ((reader.numDocs() / DF));
			float qWeight = qTF * qIDF;

			queryweights2.put(queryterm, qWeight);

			TopDocs tpd = searcher.search(queryterm, numDocs);

			List<Double> ls = doParagraphScoring(qid, qname, tpd, queryweights2, queryterm, runfile);

		}

	}

	/**
	 * Function: This function will return Paragraph Scoring. This also returns
	 * the list of score .
	 * 
	 * @param :qid
	 * @param :qname
	 * @param tpd
	 * @param :TopDocs
	 * @param queryweights2
	 * @param queryterm
	 * @param runfile
	 * @return :List for combined function
	 * @throws ParseException
	 * @throws IOException
	 */

	public List<Double> doParagraphScoring(String qid, String qname, TopDocs tpd,
			HashMap<TermQuery, Float> queryweights2, TermQuery queryterm, String runfile)
			throws ParseException, IOException {
		HashMap<Document, ResultQuery> docMap = new HashMap<>();
		HashMap<Document, Float> scores = new HashMap<>(); // Mapping of each
		ArrayList<ResultQuery> docResults = new ArrayList<>();
		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ParagraphResultComparator());

		List<Double> ls1 = new ArrayList<Double>();

		Query q = parser.parse(qname);

		for (int i = 0; i < tpd.scoreDocs.length; i++) { // For every

			Document doc = searcher.doc(tpd.scoreDocs[i].doc); // Get

			double score = tpd.scoreDocs[i].score * queryweights2.get(queryterm); // Calculate

			ResultQuery dResults = docMap.get(doc);
			if (dResults == null) {
				dResults = new ResultQuery(doc);
			}

			String docBody = doc.get(CONTENT);
			NGram ngram = new NGram(8);
			double score1 = ngram.distance(qname, docBody);

			float prevScore = dResults.getScore();
			dResults.score((float) (prevScore + score + score1));
			dResults.queryId(qid);
			dResults.paragraphId(doc.getField(PID).stringValue());
																				
			dResults.teamName("Team1 ");
			dResults.methodName("TopktreeContextualSimilarity");

			docMap.put(doc, dResults);

			scores.put(doc, (float) (prevScore + score));

		}
		float cosineLength = 0.0f;
		for (Map.Entry<Document, Float> entry : scores.entrySet()) {
			Float score = entry.getValue();

			cosineLength = (float) (cosineLength + Math.pow(score, 2));
		}
		cosineLength = (float) (Math.sqrt(cosineLength));

		for (Map.Entry<Document, Float> entry : scores.entrySet()) {

			Document doc = entry.getKey();
			Float score = entry.getValue();

			scores.put(doc, score / scores.size());

			ls1.add((double) (score / scores.size()));

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

		ContextualParagraphqueryResults.put(q, docResults);
		writeResults(ContextualParagraphqueryResults, runfile);
		return ls1;

	}

	/*****
	 * Function:WriteResults
	 * 
	 * @param ParagraphqueryResults2
	 * @param runfile
	 * @throws IOException
	 */

	public void writeResults(HashMap<Query, ArrayList<ResultQuery>> ParagraphqueryResults2, String runfile)
			throws IOException {
		// TODO Auto-generated method stub

		FileWriter runfileWriter = new FileWriter(new File(runfile));
		for (Map.Entry<Query, ArrayList<ResultQuery>> results : ContextualParagraphqueryResults.entrySet()) {
			ArrayList<ResultQuery> list = results.getValue();

			for (int i = 0; i < list.size(); i++) {

				ResultQuery dr = list.get(i);

				runfileWriter.write(dr.getRunfileString());
			}
		}
		runfileWriter.close();

	}

	/**
	 * Function:writeParagraphScore
	 * 
	 * @param runfile
	 * @Description:Main function of the Similarity which calculates similarity
	 *                   score of paragraph
	 * @throws IOException,ParseException
	 */
	public void writeContextualParagraphScore(String runfile) throws IOException, ParseException {

		ContextualParagraphqueryResults = new HashMap<>(); // Maps query to map of
													// Documents with

		for (Data.Page page : pageList) {

			ChooseTopKWords(page, runfile);
		}
		System.out.println("TopKtree Contextual Simialrity is being written to: \t\t" + runfile);

	}
}
