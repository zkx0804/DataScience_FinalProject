package edu.unh.cs980.nlp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.unh.cs980.nlp.NL_Document.Sentence;
import edu.unh.cs980.nlp.NL_Document.Word;

/**
 * @author Kaixin
 *
 */
public class NL_Processor {
	private static final Logger logger = Logger.getLogger(NL_Processor.class);
	private static StanfordCoreNLP pipeline;
	private static Boolean initWithOpenIE;
	private static NL_Processor instance;

	public NL_Processor() {
		initPipeline(true);
	}

	public static NL_Processor getInstance() {
		if (instance == null) {
			instance = new NL_Processor();
		}
		return instance;
	}

	// Won't retrieve relationships between entities in each sentence. Run
	// faster.
	public NL_Document.Paragraph convertToNL_DocumentWithOutOpenIE(String para_text) {
		return convertToNL_Document(para_text, false);
	}

	// Retrieve relationships between entities in each sentence.
	public NL_Document.Paragraph convertToNL_DocumentWithOpenIE(String para_text) {
		return convertToNL_Document(para_text, true);
	}

	/**
	 * @param openie
	 *            Please try to initialize the pipeline once. There will be a
	 *            time cost & logs when re-init the pipeline. If the pipeline
	 *            already exists and no change on openIE option, then skip the
	 *            initialization.
	 */
	private static void initPipeline(Boolean openie) {

		String annotators = "tokenize,ssplit,pos";
		if (pipeline == null) {
			if (openie) {
				annotators = "tokenize,ssplit,pos,lemma,depparse,natlog,openie";
				initWithOpenIE = openie;
				logger.info("Initialize NLP pipeline with OpenIE.");
			} else {
				initWithOpenIE = openie;
				logger.info("Initialize Standard NLP pipeline.");

			}

			Properties props = new Properties();
			props.put("annotators", annotators);
			pipeline = new StanfordCoreNLP(props);
			logger.info("Pipeline initialized.");
		} else {
			if (openie != initWithOpenIE) {
				if (openie) {
					annotators = "tokenize,ssplit,pos,lemma,depparse,natlog,openie";
					initWithOpenIE = openie;
					logger.info("Re-Initialize NLP pipeline with OpenIE.");
				} else {
					initWithOpenIE = openie;
					logger.info("Re-Initialize Standard NLP pipeline without OpenIE.");

				}
				Properties props = new Properties();
				props.put("annotators", annotators);
				pipeline = new StanfordCoreNLP(props);
				logger.info("Pipeline initialized.");
			}
		}
	}

	public NL_Document.Paragraph convertToNL_Document(String para_text, Boolean openie) {

		// logger.debug("Processing text with stanford NLP... ");

		NL_Document.Paragraph para = new NL_Document.Paragraph();
		CoreDocument document = new CoreDocument(para_text);

		pipeline.annotate(document);

		// Convert paragraph text into a list of sentences
		List<CoreSentence> coreSentences = document.sentences();
		ArrayList<Sentence> sentences = new ArrayList<>();

		for (CoreSentence entryLine : coreSentences) {
			// Iterate through each sentence
			NL_Document.Sentence sentence = new NL_Document.Sentence();
			ArrayList<Word> allNouns = new ArrayList<Word>();
			ArrayList<Word> allVerbs = new ArrayList<Word>();

			String sentConent = entryLine.text();
			// logger.debug("Sentence: " + sentConent);
			// Iterate through each word in a sentence
			for (CoreLabel token : entryLine.tokens()) {
				Word word = new Word();
				word.setText(token.word());
				String pos = token.tag();
				word.setPosTag(pos);
				if (isNoun(pos)) {
					allNouns.add(word);
				} else if (isVerb(pos)) {
					allVerbs.add(word);
				} else {
					// Ignore
				}
			}
			sentence.setSentContent(sentConent);
			sentence.setAllNouns(allNouns);
			sentence.setAllVerbs(allVerbs);

			if (openie) {
				// Retrieve dependency graph.
				SemanticGraph dependencies = entryLine.dependencyParse();
				sentence.setDependencyGraph(dependencies);

				// Retrieve all relations
				Collection<RelationTriple> triples = entryLine.coreMap()
						.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
				sentence.setTriples(new ArrayList<RelationTriple>(triples));

			}

			sentences.add(sentence);
		}

		para.setParaContent(para_text);
		para.setSentences(sentences);

		return para;
	}

	// Check if the word is noun
	private Boolean isNoun(String pos) {
		// POS: NN, NNS, NNP, NNPS
		return pos.toUpperCase().contains("NN");
	}

	// Check if the word is verb
	private Boolean isVerb(String pos) {
		// POS: VB, VBD, VBG, VBN, VBP, VBZ
		return pos.toUpperCase().contains("VB");
	}

}
