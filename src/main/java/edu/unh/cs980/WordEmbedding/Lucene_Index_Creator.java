/******************
 * This class is being used for indexing of paragraph Corpus.
 * 
 */
package edu.unh.cs980.WordEmbedding;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

public class Lucene_Index_Creator {
	private static final String NORMAL = "NORMAL";
	
	
    private IndexWriter iw;
    private final String paragraphCorpusFile;
    private final String indexOutDirectory;

    private final String indexChoice;
    Lucene_Index_Creator(String choice, String corpusFile, String indexOut) {
        indexChoice = choice;
        paragraphCorpusFile = corpusFile;
        indexOutDirectory = indexOut;
    }

    void initializeIndexWriter() throws IOException {
        Path indexLocation = Paths.get(indexOutDirectory);
        Directory indexOutDirectory = FSDirectory.open(indexLocation);
        IndexWriterConfig indexConfiguration = new IndexWriterConfig(new StandardAnalyzer());
        iw = new IndexWriter(indexOutDirectory, indexConfiguration);
    }


    void run() throws IOException {
        final FileInputStream fileStream = new FileInputStream(new File(paragraphCorpusFile));
        Iterable<Data.Paragraph> ip = DeserializeData.iterableParagraphs(fileStream);
        for (Data.Paragraph p : DeserializeData.iterableParagraphs(new FileInputStream(new File(paragraphCorpusFile)))) {
			Document doc = createDocument(p);
			System.out.println(doc);
			iw.addDocument(doc);

		}
        iw.close();
    }
    


    private Document createDocument(Data.Paragraph p) {
        final Document doc = new Document();
        final String content = p.getTextOnly();
        doc.add(new TextField("text", content, Field.Store.YES));
        doc.add(new StringField("paragraphid", p.getParaId(), Field.Store.YES));

        // index stored entities
        for (String entity : p.getEntitiesOnly()) {
            doc.add(new StringField("entity", entity, Field.Store.YES));
        }

    return doc;
    }

}

