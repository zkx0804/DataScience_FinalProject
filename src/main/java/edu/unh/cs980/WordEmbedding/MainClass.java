package edu.unh.cs980.WordEmbedding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.similarities.BM25Similarity;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;


class MainClass
{
  
	static final String INDEX_DIRECTORY = "I:\\CS980AssignmentMaterial\\ParagraphCorpus\\ParagraphIndex\\";
	
	private static void IndexerUsage(){

		System.out.println("Indexer Usage: INDEX INDEXTYPE PARAGRAPH_CORPUS_LOCATION INDEX_DIRECTORY_LOCATION\n");
	}

	private static void printQueryUsage() {
		System.out.println("Query Usage: QUERY QUERYTYPE PARAGRAPHCORPUS_INDEX_LOCATION OUTLINESCBOR_LOCATION OUTPUT_RANK_LOCATION\n");
	}
  
	
	  private static void printHeadingWeightsVariation() {
	        System.out.println("WordEmbedding Usage: command query_vector queryType PARAGRAPHCORPUS_INDEX_LOCATION LOCATION_OF_WORD_VECTOR_FILE OUTLINESCBOR_LOCATION OUTPUT_RANK_LOCATION\n" );
	      }
	  
	  
	  private static void PrintTF_IDFUsage()
	  {
		  System.out.println("choice indexLocation queryLocation  rankingOutputLocation"); 
                    
	  }
	
	//This function is being used for Paragraph Indexer
	private static void runIndexer(String sType, String corpusFile,
			String indexOutLocation) throws IOException {

		String yourChoice = null;
		try {
			yourChoice = sType;
			Lucene_Index_Creator indexBuilder = new Lucene_Index_Creator(yourChoice, corpusFile, indexOutLocation);

			indexBuilder.initializeIndexWriter();
			indexBuilder.run();
		} catch (IllegalArgumentException e) {
			System.out.println("Invalid index type!!!!");
			IndexerUsage();
			System.exit(1);
		}
}

	//This function is being used for running query
    private static void runQuery(String command, String qType,
			String indexLocation, String queryLocation,
			String rankingOutputLocation) throws IOException {

		Lucene_Query_Creator qbuilder = getQueryBuilder(command, qType,
				indexLocation, queryLocation, rankingOutputLocation);
		qbuilder.writeRankings(queryLocation, rankingOutputLocation);
	}

	
    //This function is being used for creating 
	private static Lucene_Query_Creator getQueryBuilder(String command,
			String qType, String indexLocation, String queryLocation,
			String rankingOutputLocation) throws IOException {
		String yourQueryType = null;
		try {
			yourQueryType = qType;

		} catch (IllegalArgumentException e) {
			System.out.println("Invalid query type!!!!!!!!!!!!");
			printQueryUsage();
			System.exit(1);
		}

		return new Lucene_Query_Creator(command, yourQueryType,
				new StandardAnalyzer(), new BM25Similarity(), indexLocation);
	}
	
	 // Variant of runQuery that also supplied location to word vectors file (used for word vector reranking)
    private static void WordEmbeddingRunQuery(String command, String qType, String indexLocation, String queryLocation,
                                 String rankingOutputLocation) throws IOException {

        Lucene_Query_Creator qbuilder = getQueryBuilder(command, qType, indexLocation,
                queryLocation, rankingOutputLocation);
       
        qbuilder.writeRankings(queryLocation, rankingOutputLocation);
    }
    
    
    public static void EntityLinking(String command, String queryType, String indexLocation, String queryLocation,
			String rankingOutputLocation) throws IOException {
		// TODO Auto-generated method stub
    	
    	Lucene_Query_Creator qbuilder = getQueryBuilder(command, queryType, indexLocation,
                queryLocation, rankingOutputLocation);
       
        qbuilder.writeRankings(queryLocation, rankingOutputLocation);
		
	}
    
    
    
 // To access the file from given Path
	
 	 private static  ArrayList<Data.Page> getAllPageFromPath(String indexLocation,String queryLocation,String rankingOutputLocation)  {
 		ArrayList<Data.Page> pageList = new ArrayList<Data.Page>(); 
 		
 		try { 
 			
 			FileInputStream fis = new FileInputStream(new File(queryLocation));
 			for (Data.Page page : DeserializeData.iterableAnnotations(fis)) {
 			
 				pageList.add(page);
 			

 			}
 		} catch (FileNotFoundException e) {
 			// TODO Auto-generated catch block
 			e.printStackTrace();
 		} 
 		return pageList;
 	}
    
	

	private static void printUsages() {
		IndexerUsage();
		System.out.println();
		printQueryUsage();

	}

	public static void main(String[] args) throws IOException, ParseException {
		
		String choice = "";
		try {
			choice = args[0];
			
			System.out.println(choice);
		} catch (ArrayIndexOutOfBoundsException e) {
			printUsages();
			System.exit(1);
		}

		switch (choice) {
		
		case "index":
			try {
				final String indexType = args[1].toUpperCase();
				System.out.println(indexType);
				final String corpusFile = args[2];
				System.out.println(corpusFile);
				final String indexOutputDirectory = args[3];
				System.out.println(indexOutputDirectory);
				runIndexer(indexType, corpusFile, indexOutputDirectory);
			} catch (IndexOutOfBoundsException e) {
				IndexerUsage();
			}
			break;

		
		case "query":
		   try {
				String command = args[0];
				String queryType = args[1];
				System.out.println(queryType);
				String indexLocation = args[2];
				String queryLocation = args[3];
				String rankingOutputLocation = args[4];
				runQuery(command, queryType, indexLocation, queryLocation,
						rankingOutputLocation);
				
				
			} catch (ArrayIndexOutOfBoundsException e) {
				printQueryUsage();
			}
			break;
         // it is being used for Word-embedding 			
		case "query_vector":
            try {
                String command = args[0];
                String queryType = args[1];
                String indexLocation = args[2];
                String queryLocation = args[3]; // Should be GloVe's 50D word vectors
                String rankingOutputLocation = args[4];
                WordEmbeddingRunQuery(command, queryType, indexLocation, queryLocation,rankingOutputLocation);
            } catch (ArrayIndexOutOfBoundsException e) {
            	printHeadingWeightsVariation();
            }
            break;
           
         case "TFDF_Similarity":
       			try
       			{    
       			    String indexLocation = args[1];  // Paragraph Corpus indexing
                    String queryLocation = args[2];   //outlines-cbor file 
                    String rankingOutputLocation = args[3];  //where Tf-IDF output should be 
                    ArrayList<Data.Page> pagelist = 
                    		getAllPageFromPath(indexLocation,queryLocation,rankingOutputLocation);
                    
                    TfIdfSimilarity tfidf = new TfIdfSimilarity(pagelist, 100,args[1]);
                   tfidf.writeTFIDFScoresTo(args[3] + "\\Similarity_TFIDF.run");
       			}
       			catch(ArrayIndexOutOfBoundsException e)
       			{
       				PrintTF_IDFUsage();
       			}
       			break;
       			
       			
         case "ParagraphSimilarity":
    			try
    			{    
    			    String indexLocation = args[1];  // Paragraph Corpus indexing
                 String queryLocation = args[2];   //outlines-cbor file 
                 String rankingOutputLocation = args[3];  //where Tf-IDF output should be 
                 ArrayList<Data.Page> pagelist = 
                 		getAllPageFromPath(indexLocation,queryLocation,rankingOutputLocation);
                 
                                
                 ParagraphSimilarity ps = new ParagraphSimilarity(pagelist, 100,args[1]);
                 ps.writeParagraphScore(args[3] +"\\ParagraphSimilarity.run");
     			 
     			 
    			}
    			catch(ArrayIndexOutOfBoundsException e)
    			{
    				PrintTF_IDFUsage();
    			}
    			break;
       		
		default:
			printUsages();
			break;
		}

	}

}
