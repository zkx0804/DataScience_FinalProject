/**
 * Given a query file, will build queries to query indexed Lucene database with.
 * Implements variations of querying and ranking.
 *
 * command: Command used to run program. Used to distinguish between variations of normal methods when querying.
 * gloveReader: Used for word vector variation: compares query and documents via cosine sim
 */

package edu.unh.cs980.WordEmbedding;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Data.Page;
import edu.unh.cs.treccar_v2.Data.PageSkeleton;
import edu.unh.cs.treccar_v2.Data.Section;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;


import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;

/*
 * 
 * This class contains all the heading weights option and Wordembedding call option
 * 
 * */
public class Lucene_Query_Creator {
	
	// Constant declaration
	
	private static final String PAGE = "PAGE";
	private static final String SECTION = "SECTION";
	private static final String BM25QUERYJUSTTHEPAGENAME = "BM25QUERYJUSTTHEPAGENAME";
	private static final String BM25QUERYJUSTTHELOWESTHEADING = "BM25QUERYJUSTTHELOWESTHEADING";
	private static final String BM25QUERYOFINTERIORHEADING = "BM25QUERYOFINTERIORHEADING";
	private static final String WORDEMBEDDING = "WORDEMBEDDING";
	final MyQueryBuilder queryBuilder = new MyQueryBuilder(new StandardAnalyzer());
	private IndexSearcher indexSearcher;
	private Analyzer analyzer;
	private String queryType;
	
	
	/**
     * Function: writeRankings
     * Description: Lucene Query creator  Constructor
     * @Call : Index searcher 
     */
    
 
	public Lucene_Query_Creator(String qType, String queryType2, Analyzer ana, Similarity sim, String indexPath)
			throws IOException {
		analyzer = ana;
		queryType = queryType2;

		setUpIndexSearcher(indexPath);
		indexSearcher.setSimilarity(sim);

	}

	@SuppressWarnings("unused")
	private class TokenGenerator implements Supplier<String> {
		final TokenStream tokenStream;

		@SuppressWarnings("unused")
		TokenGenerator(TokenStream ts) throws IOException {
			tokenStream = ts;
			ts.reset();
		}

		public String get() {
			try {
				if (!tokenStream.incrementToken()) {
					tokenStream.end();
					tokenStream.close();
					return null;
				}
			} catch (IOException e) {
				return null;
			}
			return tokenStream.getAttribute(CharTermAttribute.class).toString();
		}
	}

	
	// Author: Prof Laura.
	static class MyQueryBuilder {
		private final StandardAnalyzer analyzer;
		private List<String> tokens;

		public MyQueryBuilder(StandardAnalyzer standardAnalyzer) {
			analyzer = standardAnalyzer;
			tokens = new ArrayList<>(128);
		}

		public BooleanQuery toQuery(String queryStr) throws IOException {

			TokenStream tokenStream = analyzer.tokenStream("text", new StringReader(queryStr));
			tokenStream.reset();
			tokens.clear();

			while (tokenStream.incrementToken()) {
				final String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
				tokens.add(token);
			}
			tokenStream.end();
			tokenStream.close();
			BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
			for (String token : tokens) {
				booleanQuery.add(new TermQuery(new Term("text", token)), BooleanClause.Occur.SHOULD);
			}
			return booleanQuery.build();
		}

	}
    
	/**
     * Function: writeRankings
     * Description: /* This function is being used for Section Query String 
     * @return : QueryString
     */
	
	
	private static String buildSectionQueryStr(Data.Page page, List<Data.Section> sectionPath) {

		StringBuilder queryStr = new StringBuilder();
		queryStr.append(page.getPageName());
		for (Data.Section section : sectionPath) {
			queryStr.append(" ").append(section.getHeading());
		}
		return queryStr.toString();
	}
	
	
	
	
	/**
     * Function: writeRankings
     * Description: This method calls ranking function of heading weights variants 
     * @Call:Call different heading variants. 
     */

	public void writeRankings(String queryLocation, String rankingsOutput) throws IOException {
		final BufferedWriter out = new BufferedWriter(new FileWriter(rankingsOutput));
		final FileInputStream inputStream = new FileInputStream(new File(queryLocation));

		if (queryType.equalsIgnoreCase(PAGE)) {
			writePageRankings(inputStream, out);
		} else if (queryType.equalsIgnoreCase(SECTION)) {
			writeSectionRankings(inputStream, out,rankingsOutput);
		} else if (queryType.equalsIgnoreCase(BM25QUERYJUSTTHEPAGENAME)) {
			writeBM25QueryJustThePageName(inputStream, out,rankingsOutput);
		} else if (queryType.equalsIgnoreCase(BM25QUERYJUSTTHELOWESTHEADING)) {
			writeBm25QueryJustTheLowestHeading(inputStream, out, rankingsOutput);
		} else if (queryType.equalsIgnoreCase(BM25QUERYOFINTERIORHEADING)) {
			writeBm25QueryOfInteriorHeading(inputStream, out,rankingsOutput);
		} else if (queryType.equalsIgnoreCase(WORDEMBEDDING)) {
			writeWordEmbedding(inputStream,rankingsOutput);
		} 
		 else {
			System.out.println("Wrong Input");

		}

		out.flush();
		out.close();
	}

	


	/**
     * Function: writeRunfile
     * Description: This function is being used for writing results for wordembedding.
     * @Call  : Wordembedding Class and other method to write the wordembeddding results. 
     */
	
	
	public void writeRunfile(String filename, ArrayList<String> runfileStrings) {
		String fullpath = filename;
		try (FileWriter runfile = new FileWriter(new File(fullpath))) {
			for (String line : runfileStrings) {
				runfile.write(line + "\n");
			}

			runfile.close();
		} catch (IOException e) {
			System.out.println("Could not open " + fullpath);
		}
	}
	
	/**
     * Function: writeWordEmbedding
     * Description: Given an inputStream,outputstream,call Wordembedding and proceed with pagelist 
     * @Call  : Wordembedding Class and other method to write the wordembeddding results. 
     */
	


	private void writeWordEmbedding(FileInputStream inputStream, String RankingsOutput) throws IOException {
		// TODO Auto-generated method stub

		ArrayList<Data.Page> pageIDList = new ArrayList<Data.Page>();

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {

			pageIDList.add(page);

		}
		WordEmbedding UL_ranking = new WordEmbedding(pageIDList, 100);
		writeRunfile(RankingsOutput, UL_ranking.getResults());
		//RemoveDuplicatesFromFile(RankingsOutput);
	
	}
  
	
	/**
     * Function: writeBm25QueryOfInteriorHeading
     * Description: Given an inputStream, outputstream write the ranking string of QueryOfInteriorHeading into file
     * @Call  : Another write function to write the rankingstring  
     */
	private void writeBm25QueryOfInteriorHeading(FileInputStream inputStream, BufferedWriter out,String RankingoutputFile) throws IOException {
		// TODO Auto-generated method stub
		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			for (List<Data.Section> sectionPath : page.flatSectionPaths()) {

				System.out.println();
				System.out.println("Section Path Id =" + Data.sectionPathId(page.getPageId(), sectionPath) + "   \t "
						+ "Section Path Headings =" + Data.sectionPathHeadings(sectionPath));
				final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);

				String queryStr = buildBm25QueryOfInteriorHeading(page, sectionPath);

				TopDocs tops = null;
				try {
					tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				System.out.println("Found " + scoreDoc.length + " results.");
				writeRankingsToFile(scoreDoc, queryId, out);

			}
		}RemoveDuplicatesFromFile(RankingoutputFile);

	}
	
	/**
     * Function: buildBm25QueryOfInteriorHeading
     * Description: called by writeBm25QueryOfInteriorHeading function to create a string of QueryOfInteriorHeading into file
     * @return : Querystring for interior heading  
     */

	private String buildBm25QueryOfInteriorHeading(Data.Page page, List<Section> sectionPath) {
		// TODO Auto-generated method stub

		StringBuilder queryStr = new StringBuilder();
		queryStr.append(page.getPageName());
		System.out.println("queryStr = " + queryStr);
		for (List<Data.Section> sectionPath1 : page.flatSectionPaths()) {
			for (Data.Section section : sectionPath1) {
				queryStr.append(" ").append(section.getHeading());

			}
		}
		return queryStr.toString();

	}

	/**
     * Function: writeBm25QueryJustTheLowestHeading
     * Description: given an input/output stream and output ranking file read and write the paragraph page and find out the lowest heading of sections
     * and call another function to write the queryString
     * @return : Querystring for interior heading  
     */
	
	
private void writeBm25QueryJustTheLowestHeading(FileInputStream inputStream, BufferedWriter out,
			String RankingOutput) throws IOException {
		// TODO Auto-generated method stub
		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			// final String queryId = page.getPageId();

			for (List<Data.Section> sectionPath : page.flatSectionPaths()) {

				/*System.out.println();
				System.out.println("Section Path Id =" + Data.sectionPathId(page.getPageId(), sectionPath) + "   \t "
						+ "Section Path Headings =" + Data.sectionPathHeadings(sectionPath));*/
				final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);

				String queryStr = buildBm25QueryJustTheLowestHeading(sectionPath);

				TopDocs tops = null;
				try {
					tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				
				writeRankingsToFile(scoreDoc, queryId, out);
				
			}
			
			
		}RemoveDuplicatesFromFile(RankingOutput);    
		
	}

/**
 * Function: buildBm25QueryJustTheLowestHeading
 * Description: given sectionpath  write the query string for just the lowest heading
 * @return : Querystring for JustTheLowestHeading 
 */

	private String buildBm25QueryJustTheLowestHeading(List<Section> sectionPath) {
		// TODO Auto-generated method stub

		String queryStr = " ";
		List<PageSkeleton> childrenElement;
		for (Data.Section sectionPath1 : sectionPath) {

			childrenElement = sectionPath1.getChildren();
			if (!(childrenElement.isEmpty())) {
				Section s = (Section) childrenElement.get(childrenElement.size() - 1);
				queryStr = s.getHeading();

			} else {
				queryStr = sectionPath1.getHeading();
			}

		}
		return queryStr;
	}

	/**
	 * Function: RemoveDuplicatesFromFile
	 * Description: given output file remove the duplicate paragraphid to make compatible run file
	 * (if it will contain duplicate then it wont run on trec_eval)
	 * @create : run file after removing of paragraph id
	 */
	public  void RemoveDuplicatesFromFile(String RankingFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(RankingFile));
		Set<String> lines = new HashSet<String>();
        Set<String>CopyLines=new HashSet<String>();

		String line;
		
		synchronized(this)
		{
		while ((line = reader.readLine()) != null) {
			lines.add(line);
			
		}
		}
		reader.close();
		System.out.println("Removing Duplicates");
		BufferedWriter writer = new BufferedWriter(new FileWriter(RankingFile));
		
		for (String unique : lines) {
			
			synchronized(this)
			{
			if(!CopyLines.contains(unique))
			{
               writer.write(unique);
			   
			}
			}
			
			CopyLines.add(unique);
			writer.newLine();
		
		}
		writer.close();
	}

	
	/**
     * Function: writeBM25QueryJustThePageName
     * Description: given an input/output stream and output ranking file name  read and fetch the page name .
     * and call another function to write the queryString
     * @call : another function to write output to file  
     */

	private void writeBM25QueryJustThePageName(FileInputStream inputStream, BufferedWriter out,String rankingoutput) throws IOException {
		// TODO Auto-generated method stub

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			// String queryId = page.getPageName();

			String queryId = page.getPageId();

			String queryStr = buildBM25QueryJustThePageName(page, Collections.<Data.Section> emptyList());

			TopDocs tops = null;
			try {
				tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			System.out.println("Found " + scoreDoc.length + " results.");
			writeRankingsToFile(scoreDoc, queryId, out);
		}
	}
	
	/**
	 * Function: buildBM25QueryJustThePageName
	 * Description: given page and sectionpath write the query string for just the pagename
	 * @return : Querystring for JustThepagename 
	 */

	private String buildBM25QueryJustThePageName(Data.Page page, List<Data.Section> sectionPath) {
		// TODO Auto-generated method stub
		StringBuilder queryStr = new StringBuilder();
		queryStr.append(page.getPageName());
		// queryStr.append(page.getPageId());
		for (Data.Section section : sectionPath) {
			queryStr.append(" ").append(section.getHeading());
		}

		return queryStr.toString();

	}
	
	
	/**
	 * Function: writeRankingtofile
	 * Description: After receiving the document,create queryString for run file. 
	 * @Write: write rankings to run file for just the page name
	 */

   void writeRankingsToFile(ScoreDoc[] scoreDoc, String queryId, BufferedWriter out) throws IOException {


		Set<String> lines = new HashSet<String>();
		for (int i = 0; i < scoreDoc.length; i++) {
			ScoreDoc score = scoreDoc[i];
			final Document doc = indexSearcher.doc(score.doc);
			final String paragraphid = doc.getField("paragraphid").stringValue();

			final float searchScore = score.score;
			final int searchRank = i + 1;
			String writeIntoFile = queryId + " Q0 " + paragraphid + " " + searchRank + " " + searchScore
					+ " Lucene-BM25" + "\n";
    
			synchronized(this)
			{
			if (!lines.contains(writeIntoFile)||(!writeIntoFile.contains("enwiki:Trafficking%20of%20children/Proposed%20solutions/Relevant%20organizations Q0 f268fa74e75a547861f75"))) {

				out.write(writeIntoFile);
			}
			
			}
		}

	}
   
   
   /**
	 * Function: writePageRankings
	 * Description: Show the results for page  and write ranking to the page.run file
	 * @Write: write rankings to run file for just the page name
	 */
   

	void writePageRankings(FileInputStream inputStream, BufferedWriter out) throws IOException {

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			final String queryId = page.getPageId();

			String queryStr = buildSectionQueryStr(page, Collections.<Data.Section> emptyList());

			TopDocs tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			//System.out.println("Found " + scoreDoc.length + " results.");
			writeRankingsToFile(scoreDoc, queryId, out);
		}
	}
	
	/**
	 * Function: writeSectionRankings
	 * Description: Show the results for section and write ranking to the Sections.run file
	 * @Write: write rankings to run file for just the sections name
	 */
	
	

	void writeSectionRankings(FileInputStream inputStream, BufferedWriter out,String RankingOutput) throws IOException {

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			for (List<Data.Section> sectionPath : page.flatSectionPaths()) {

				System.out.println();
				System.out.println("Section Path Id =" + Data.sectionPathId(page.getPageId(), sectionPath) + "   \t "
						+ "Section Path Headings =" + Data.sectionPathHeadings(sectionPath));
				final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);
				String queryStr = buildSectionQueryStr(page, sectionPath);

				TopDocs tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				writeRankingsToFile(scoreDoc, queryId, out);
			}
		}RemoveDuplicatesFromFile(RankingOutput);

	}

	
	/**
	 * Function: setUpIndexSearcher
	 * Description: Find the path for given string and search
	 * @call: Index searcher 
	 */
	
	
	
	
	private void setUpIndexSearcher(String iPath) throws IOException {
		Path indexPath = Paths.get(iPath);
		Directory indexDir = FSDirectory.open(indexPath);
		IndexReader indexReader = DirectoryReader.open(indexDir);
		indexSearcher = new IndexSearcher(indexReader);
	}
	
	
	
	
}
