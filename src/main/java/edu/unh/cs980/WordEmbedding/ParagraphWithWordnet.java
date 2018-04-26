package edu.unh.cs980.WordEmbedding;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import co.nstant.in.cbor.CborException;
import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Data.Page;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import edu.unh.cs980.WordEmbedding.Lucene_Query_Creator.MyQueryBuilder;

/**
 * @author Bindu Kumari
 *
 */
public class ParagraphWithWordnet {

	final MyQueryBuilder queryBuilder = new MyQueryBuilder(new StandardAnalyzer());
	private IndexSearcher indexSearcher;
	private Analyzer analyzer;

	/**
	 * @param args
	 */
	private IndexSearcher is = null;
	private QueryParser qp = null;
	private boolean customScore = true;

	Query q;
	TopDocs topdocs;
	ScoreDoc[] returnedDocs;

	static final String Output_Directory = "output";
	// static final String Index_Directory = "index/dir";
	static final String Cbor_File = "test200.cbor/train.test200.cbor.paragraphs";

	public ParagraphWithWordnet(StandardAnalyzer standardAnalyzer, BM25Similarity bm25Similarity, String indexLocation)
			throws IOException {

		analyzer = standardAnalyzer;

		setUpIndexSearcher(indexLocation);
		indexSearcher.setSimilarity(bm25Similarity);

	}

	private void setUpIndexSearcher(String iPath) throws IOException {
		Path indexPath = Paths.get(iPath);
		Directory indexDir = FSDirectory.open(indexPath);
		IndexReader indexReader = DirectoryReader.open(indexDir);
		indexSearcher = new IndexSearcher(indexReader);
	}

	/*****************************************
	 * For search
	 *****************************************************************/

	public void writeRankings(String queryLocation, String rankingsOutput) throws IOException {
		final BufferedWriter out = new BufferedWriter(new FileWriter(rankingsOutput));
		final FileInputStream inputStream = new FileInputStream(new File(queryLocation));

		writeParagraphRankings(inputStream, out);
		out.flush();
		out.close();

	}

	private void writeParagraphRankings(FileInputStream inputStream, BufferedWriter out) throws IOException {
		// TODO Auto-generated method stub

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			final String queryId = page.getPageId();

			String queryStr = buildSectionQueryStr(page, Collections.<Data.Section> emptyList());

			TopDocs tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			writeRankingsToFile(scoreDoc, queryId, out);
		}

	}
	
	
	  //This function is being used for creating 
  	public static ParagraphWithWordnet getQueryBuilder( String indexLocation, String queryLocation,
  			String rankingOutputLocation) throws IOException {
  	

  		return new ParagraphWithWordnet( new StandardAnalyzer(), new BM25Similarity(), indexLocation);
  	}
    

	private static String buildSectionQueryStr(Data.Page page, List<Data.Section> sectionPath) {

		StringBuilder queryStr = new StringBuilder();
		queryStr.append(page.getPageName());
		for (Data.Section section : sectionPath) {
			queryStr.append(" ").append(section.getHeading());
		}
		return queryStr.toString();
	}

	void writeRankingsToFile(ScoreDoc[] scoreDoc, String queryId, BufferedWriter out) throws IOException {

		Set<String> lines = new HashSet<String>();
		for (int i = 0; i < scoreDoc.length; i++) {
			ScoreDoc score = scoreDoc[i];
			final Document doc = indexSearcher.doc(score.doc);
			final String paragraphid = doc.getField("paragraphid").stringValue();

			final float searchScore = score.score;
			final int searchRank = i + 1;
			String writeIntoFile = queryId + " Q0 " + paragraphid + " " + searchRank + " " + searchScore
					+ " Paragraph-WordnetSimilarity" + "\n";

			synchronized (this) {
				if (!lines.contains(writeIntoFile) || (!writeIntoFile.contains(
						"enwiki:Trafficking%20of%20children/Proposed%20solutions/Relevant%20organizations Q0 f268fa74e75a547861f75"))) {

					out.write(writeIntoFile);
				}

			}
		}

	}

}
