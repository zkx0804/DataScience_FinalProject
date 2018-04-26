package edu.unh.cs980.WordEmbedding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.FSDirectory;
import com.google.common.io.Files;
import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Data.Page;
import edu.stanford.nlp.coref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

import edu.unh.cs980.KotUtils;

class DependencyTree {

public DependencyTree() throws IOException {
		// creates a StanfordCoreNLP object, with POS tagging, lemmatization,
		// NER, parsing, and coreference resolution
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

		File inputFile = new File("paragraph-content.txt");
		String text = Files.toString(inputFile, Charset.forName("UTF-8"));

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);

		// run all Annotators on this text
		pipeline.annotate(document);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);

		for (CoreMap sentence : sentences) {
			// traversing the words in the current sentence
			// a CoreLabel is a CoreMap with additional token-specific methods
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				// this is the text of the token
				String word = token.get(TextAnnotation.class);
				// this is the POS tag of the token
				String pos = token.get(PartOfSpeechAnnotation.class);
				// this is the NER label of the token
				String ne = token.get(NamedEntityTagAnnotation.class);

				// System.out.println("word: " + word + " pos: " + pos + " ne:"
				// + ne);
			}

			// this is the parse tree of the current sentence
			Tree tree = sentence.get(TreeAnnotation.class);
			// System.out.println("parse tree:\n" + tree);

			// this is the Stanford dependency graph of the current sentence
			SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
			// System.out.println("dependency graph:\n" + dependencies);
		}

		// This is the coreference link graph
		// Each chain stores a set of mentions that link to each other,
		// along with a method for getting the most representative mention
		// Both sentence and token offsets start at 1!
		Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
	}

}

/***
 * 
 * @author Bindu Kumari
 *
 */
public class ParaRankWithDepParser {

	private IndexSearcher searcher;
	private QueryParser parser;
	private int numDocs; // Number of documents to return
	private ArrayList<Data.Page> pageList; // List of pages to query
	private HashMap<Query, ArrayList<ResultQuery>> queryResults;

	/********
	 * Function: ParaRankWithDepParser
	 * 
	 * @param pl
	 * @param n
	 * @param index
	 * @throws ParseException
	 * @throws IOException
	 */

public ParaRankWithDepParser(ArrayList<Data.Page> pl, int n, String index) throws ParseException, IOException {
		String INDEX_DIRECTORY = index;
		numDocs = n;
		pageList = pl;
        parser = new QueryParser(KotUtils.CONTENT, new StandardAnalyzer());
        searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIRECTORY).toPath()))));
		// Setting our own similarity class which computes
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

	/**
	 * Function:getQueryScore Description:It will fetch the score of each
	 * paragraph bes on Query name
	 * 
	 * @param query
	 * @param indexSearcher
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */

	public List<List<Double>> getQueryScore(String query, IndexSearcher indexSearcher)
			throws ParseException, IOException {

		HashMap<TermQuery, Float> queryweights = new HashMap<>();
		ArrayList<TermQuery> terms = new ArrayList<>();

		Query q = parser.parse(query);

		for (String term : query.split(" "))

		{
			TermQuery tq = new TermQuery(new Term(KotUtils.CONTENT, term));

			terms.add(tq);

			queryweights.put(tq, queryweights.getOrDefault(tq, 0.0f) + 1.0f);
		}

		return calculateQueryScore(q, query, terms, queryweights);

	}

	/**
	 * Function:calculateQueryScore Description: Calculate score for each Query
	 * terms
	 * 
	 * @param q
	 * @param qname
	 * @param terms
	 * @param queryweights2
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */

	private List<List<Double>> calculateQueryScore(Query q, String qname, ArrayList<TermQuery> terms,
			HashMap<TermQuery, Float> queryweights2) throws IOException, ParseException {

		List<List<Double>> ls = new ArrayList<List<Double>>();

		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());
		for (TermQuery queryterm : terms) {

			IndexReader reader = searcher.getIndexReader();
			float DF = (reader.docFreq(queryterm.getTerm()) == 0) ? 1 : reader.docFreq(queryterm.getTerm());

			float qTF = (float) (1 + Math.log10(queryweights2.get(queryterm)));

			float qIDF = (float) (Math.log10(reader.numDocs() / DF));
			float qWeight = qTF * qIDF;
			queryweights2.put(queryterm, qWeight);
			TopDocs tpd = searcher.search(queryterm, numDocs);
			ls.add(getTermScores(qname, tpd, queryweights2, queryterm, q));

		}
		return ls;
	}

	/*********
	 * Function:SplitpageName Description: This
	 * 
	 * @param page
	 * @param runfile
	 * @throws IOException
	 * @throws ParseException
	 */

	public void TrimDepParser(Page page, String runfile) throws IOException, ParseException {
		// TODO Auto-generated method stub
		String qid = page.getPageId();
		String qname = page.getPageName();
		HashMap<TermQuery, Float> queryweights = new HashMap<>();
		ArrayList<TermQuery> terms = new ArrayList<>();

		Query q = parser.parse(page.getPageName());

		for (String term : page.getPageName().split(" ")) {
			TermQuery tq = new TermQuery(new Term(KotUtils.CONTENT, term));
			terms.add(tq);
			queryweights.put(tq, queryweights.getOrDefault(tq, 0.0f) + 1.0f);
		}

		calculateScore(qid, q, qname, terms, runfile, queryweights);

	}

	
	/***
	 * Function: CalculateScores
	 * @param qid
	 * @param q
	 * @param qname
	 * @param terms
	 * @param runfile
	 * @param queryweights2
	 * @throws IOException
	 * @throws ParseException
	 */
	public void calculateScore(String qid, Query q, String qname, ArrayList<TermQuery> terms, String runfile,
			HashMap<TermQuery, Float> queryweights2) throws IOException, ParseException {

		List<Double> ls = new ArrayList<Double>();

		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());
		for (TermQuery queryterm : terms) { // For every Term
			IndexReader reader = searcher.getIndexReader();
			float DF = (reader.docFreq(queryterm.getTerm()) == 0) ? 1 : reader.docFreq(queryterm.getTerm());

			float qTF = (float) (1 + Math.log10(queryweights2.get(queryterm))); // Logarithmic

			float qIDF = (float) (Math.log10(reader.numDocs() / DF));
			float qWeight = qTF * qIDF;

			queryweights2.put(queryterm, qWeight);
			TopDocs tpd = searcher.search(queryterm, numDocs);
			ls = doScoring(qid, qname, tpd, queryweights2, queryterm, runfile);

		}

	}
	/***
	 * Function: GetTermScores
	 * @param qname
	 * @param tpd
	 * @param queryweights2
	 * @param queryterm
	 * @param q
	 * @return
	 * @throws IOException
	 */

	private List<Double> getTermScores(String qname, TopDocs tpd, HashMap<TermQuery, Float> queryweights2,
			TermQuery queryterm, Query q) throws IOException {
		List<Double> ls1 = new ArrayList<Double>();
		HashMap<Document, ResultQuery> docMap = new HashMap<>();
		HashMap<Document, Float> scores = new HashMap<>(); // Mapping of each
		PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());

		for (int i = 0; i < tpd.scoreDocs.length; i++) {

			Document doc = searcher.doc(tpd.scoreDocs[i].doc);

			double score = tpd.scoreDocs[i].score * queryweights2.get(queryterm);

			ResultQuery dResults = docMap.get(doc);
			if (dResults == null) {
				dResults = new ResultQuery(doc);
			}

			float prevScore = dResults.getScore();
			dResults.score((float) (prevScore + score));
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

			// Normalize the score
			scores.put(doc, score / scores.size());

			ls1.add((double) (score / scores.size())); // Creating list for
														// combined Method
			// System.out.println(score/scores.size());
			ResultQuery dResults = docMap.get(doc);
			dResults.score(dResults.getScore() / cosineLength);

			docQueue.add(dResults);
		}
		return ls1;

	}
	
	/************
	 * Function DoScoring
	 * @param qid
	 * @param qname
	 * @param tpd
	 * @param queryweights2
	 * @param queryterm
	 * @param runfile
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */

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
			dResults.paragraphId(doc.getField(KotUtils.PID).stringValue());
			dResults.teamName("Team1");
			dResults.methodName("ParaRankWithDepParser");

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
		queryResults.put(q, docResults);
		writeResults(queryResults, runfile);
		return ls1;

	}
	/***
	 * Function : WriteResults
	 * @param queryResults2
	 * @param runfile
	 * @throws IOException
	 */

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
 * Function: writeParaRankWithDepParser
 * @param runfile
 * @throws IOException
 * @throws ParseException
 */
	public void writeParaRankWithDepParser(String runfile) throws IOException, ParseException {

		queryResults = new HashMap<>(); // Maps query to map of Documents with
		// TF-IDF score

		for (Data.Page page : pageList) {
			TrimDepParser(page, runfile);
		}
		System.out.println("Paragraph Ranking with Dependency Parser is being  written to: \t\t" + runfile);
	}
}
