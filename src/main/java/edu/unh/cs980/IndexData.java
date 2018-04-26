package edu.unh.cs980;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import co.nstant.in.cbor.CborException;
import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import edu.unh.cs980.variations.FreqBigram_index;

import static edu.unh.cs980.KotUtils.CONTENT;
import static edu.unh.cs980.KotUtils.PID;

public class IndexData {
	// For testing
	static final private String INDEX_DIRECTORY = "index";
	static final private String OUTPUT_DIR = "output";
	private static KotlinEntityLinker linker;

	public static void main(String[] args) {
		System.setProperty("file.encoding", "UTF-8");

		// 1. index dataset
		// 2. Create Lucene search engine
		// 3. Get all queries and retrieve search result
		// 4. Create run file.

		// String queryPath = args[0];
		// String dataPath = args[1];

		// Local testing
		String queryPath = "DataSet/";
		String dataPath = "DataSet/paragraphCorpus/dedup.articles-paragraphs.cbor";
		try {
			indexAllData(INDEX_DIRECTORY, dataPath, "");
		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}

	}

	public static void indexAllData(String indexDirectory, String corpusLocation, String serverLocation)
			throws CborException, IOException {
		Directory indexdir = FSDirectory.open((new File(indexDirectory)).toPath());
		IndexWriterConfig conf = new IndexWriterConfig(new StandardAnalyzer());
		conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
		IndexWriter iw = new IndexWriter(indexdir, conf);

		// Do iteration
		if (!serverLocation.equals("")) {
			linker = new KotlinEntityLinker(serverLocation);
		}

		System.out.println("Start indexing...");
		final FileInputStream fStream = new FileInputStream(new File(corpusLocation));
		Iterable<Data.Paragraph> ip = DeserializeData.iterableParagraphs(fStream);
		// Parallelized stream for adding documents to indexed database
		AtomicInteger counter = new AtomicInteger(0);
		StreamSupport.stream(ip.spliterator(), true).parallel().map(IndexData::convertToLuceneDoc).forEach(doc -> {
			try {
				iw.addDocument(doc);
				Integer cur = counter.getAndIncrement();
				if (cur % 100000 == 0) {
					System.out.println(cur);
					iw.commit();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		System.out.println("#########################");
		System.out.println("Done indexing.");
		System.out.println("#########################");

		iw.close();
	}

	private static Document convertToLuceneDoc(Data.Paragraph para) {
		Document doc = new Document();

		doc.add(new StringField(PID, para.getParaId(), Field.Store.YES));
		doc.add(new TextField(CONTENT, para.getTextOnly(), Field.Store.YES));
		// Create bigram index field
		HashMap<String, Float> bigram_score = FreqBigram_index.createBigramIndexFiled(para.getTextOnly());
		doc.add(new TextField("bigram", bigram_score.toString(), Field.Store.YES));
		if (linker != null) {

			// Add entities linked by using spotlight
			for (String entity : linker.queryServer(para.getTextOnly())) {
				doc.add(new StringField("spotlight", entity, Field.Store.YES));
			}
		}

		return doc;
	}
}