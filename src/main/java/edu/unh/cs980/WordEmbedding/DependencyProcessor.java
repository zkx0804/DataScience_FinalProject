package edu.unh.cs980.WordEmbedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**********
 * 
 * This program is being used for creating parse tree .
 *
 */
public class DependencyProcessor {

	private static StanfordCoreNLP parsetree;

	// Checking the semantic structure for noun
	private static Boolean checkNoun(String pos) {

		return pos.toUpperCase().contains("NN");
	}

	// Checking the semantic structure for verb
	private static Boolean checkVerb(String pos) {

		return pos.toUpperCase().contains("VB");
	}

	/***
	 * @param para_text
	 *            =Paragraph text
	 * @return
	 */

	public static DependencyParser.Paragraph convertToNL_Document(String para_text) {

		if (parsetree == null) {
			Properties props = new Properties();
			  props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
			parsetree = new StanfordCoreNLP(props);
		}

		DependencyParser.Paragraph para = new DependencyParser.Paragraph();
		Annotation annotation = new Annotation(para_text);

		parsetree.annotate(annotation);

		// Convert paragraph text into a list of sentences
		List<CoreMap> coreMap = annotation.get(SentencesAnnotation.class);
		ArrayList<DependencyParser.Sentence> sentences = new ArrayList<>();

		for (CoreMap entryLine : coreMap) {
			// Iterate through each sentence
			DependencyParser.Sentence sentence = new DependencyParser.Sentence();
			ArrayList<String> allNouns = new ArrayList<String>();
			ArrayList<String> allVerbs = new ArrayList<String>();

			String sentConent = entryLine.get(TextAnnotation.class);

			// Iterate through each word in a sentence
			for (CoreLabel token : entryLine.get(TokensAnnotation.class)) {

				String word = token.get(TextAnnotation.class);
				String pos = token.get(PartOfSpeechAnnotation.class);

				if (checkNoun(pos)) {
					allNouns.add(word);
				} else if (checkVerb(pos)) {
					allVerbs.add(word);
				} else {
					// Ignore
				}
			}
			sentence.setSentContent(sentConent);
			sentence.setAllNouns(allNouns);
			sentence.setAllVerbs(allVerbs);

			sentences.add(sentence);
		}

		para.setParaContent(para_text);
		para.setSentences(sentences);

		return para;
	}

}
