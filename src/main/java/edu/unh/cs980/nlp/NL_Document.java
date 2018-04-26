package edu.unh.cs980.nlp;

import java.util.ArrayList;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.semgraph.SemanticGraph;

public class NL_Document {

	public static class Paragraph {
		private String paraContent;
		private ArrayList<Sentence> sentences;

		public Paragraph() {

		}

		public String getParaContent() {
			return paraContent;
		}

		public void setParaContent(String paraContent) {
			this.paraContent = paraContent;
		}

		public ArrayList<Sentence> getSentences() {
			return sentences;
		}

		public void setSentences(ArrayList<Sentence> sentences) {
			this.sentences = sentences;
		}

		public ArrayList<Word> getAllNounsInPara() {
			ArrayList<Word> wordList = new ArrayList<Word>();

			if (sentences != null && !sentences.isEmpty()) {
				for (Sentence sent : sentences) {
					wordList.addAll(sent.getAllNouns());
				}
			}

			return wordList;
		}

		public ArrayList<Word> getAllVerbsInPara() {
			ArrayList<Word> wordList = new ArrayList<Word>();

			if (sentences != null && !sentences.isEmpty()) {
				for (Sentence sent : sentences) {
					wordList.addAll(sent.getAllVerbs());
				}
			}

			return wordList;
		}

		public ArrayList<RelationTriple> getAllRelationsTriple() {
			ArrayList<RelationTriple> relations = new ArrayList<>();

			if (sentences != null && !sentences.isEmpty()) {
				for (Sentence sent : sentences) {
					relations.addAll(sent.getTriples());
				}
			}

			return relations;
		}

	}

	public static class Sentence {
		private String sentContent;
		private ArrayList<RelationTriple> triples;
		private ArrayList<Word> allNouns;
		private ArrayList<Word> allVerbs;
		private SemanticGraph dependencies;

		public Sentence() {

		}

		public String getSentContent() {
			return sentContent;
		}

		public void setSentContent(String sentContent) {
			this.sentContent = sentContent;
		}

		public ArrayList<RelationTriple> getTriples() {
			return triples;
		}

		public void setTriples(ArrayList<RelationTriple> triples) {
			this.triples = triples;
		}

		public ArrayList<Word> getAllNouns() {
			return allNouns;
		}

		public void setAllNouns(ArrayList<Word> allNouns) {
			this.allNouns = allNouns;
		}

		public ArrayList<Word> getAllVerbs() {
			return allVerbs;
		}

		public void setAllVerbs(ArrayList<Word> allVerbs) {
			this.allVerbs = allVerbs;
		}

		public SemanticGraph getDependencyGraph() {
			return dependencies;
		}

		public void setDependencyGraph(SemanticGraph dependencies) {
			this.dependencies = dependencies;
		}

		@Override
		public String toString() {
			return "Sentence: " + sentContent;
		}
	}

	public static class Word {
		private String text;
		private String posTag;
		private String nerTag;

		public Word() {

		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public String getPosTag() {
			return posTag;
		}

		public void setPosTag(String posTag) {
			this.posTag = posTag;
		}

		public String getNerTag() {
			return nerTag;
		}

		public void setNerTag(String nerTag) {
			this.nerTag = nerTag;
		}

		@Override
		public String toString() {
			return text + " (" + posTag + ")";
		}

	}
}
