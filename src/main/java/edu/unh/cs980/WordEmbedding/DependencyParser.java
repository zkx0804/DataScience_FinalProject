package edu.unh.cs980.WordEmbedding;
import java.util.ArrayList;

/********
 * 
 * // help taken from Stanford NLP
 * 
 *
 */
public class DependencyParser {

	
	public static class Paragraph {
		
		private ArrayList<Sentence> sentences;
		private String paragraphContent;

		public Paragraph() {

		}
		
		
		public ArrayList<Sentence> getSentences() {
			return sentences;
		}

		public void setSentences(ArrayList<Sentence> sentences) {
			this.sentences = sentences;
		}

		public ArrayList<String> getAllNounsInPara() {
			ArrayList<String> wordList = new ArrayList<String>();

			if (sentences != null || !sentences.isEmpty()) {
				for (Sentence sent : sentences) {
					wordList.addAll(sent.getAllNouns());
				}
			}

			return wordList;
		}
        public String getParaContent() {
			return paragraphContent;
		}

		public void setParaContent(String paraContent) {
			this.paragraphContent = paraContent;
		}

	

		public ArrayList<String> getAllVerbsInPara() {
			ArrayList<String> wordList = new ArrayList<String>();

			if (sentences != null || !sentences.isEmpty()) {
				for (Sentence sent : sentences) {
					wordList.addAll(sent.getAllVerbs());
				}
			}

			return wordList;
		}
	}

	public static class Sentence {
		private String sentContent;
		private ArrayList<String> allNouns;
		private ArrayList<String> allVerbs;

		public Sentence() {

		}

		public String getSentContent() {
			return sentContent;
		}

		public void setSentContent(String sentContent) {
			this.sentContent = sentContent;
		}

		public ArrayList<String> getAllNouns() {
			return allNouns;
		}

		public void setAllNouns(ArrayList<String> allNouns) {
			this.allNouns = allNouns;
		}

		public ArrayList<String> getAllVerbs() {
			return allVerbs;
		}

		public void setAllVerbs(ArrayList<String> allVerbs) {
			this.allVerbs = allVerbs;
		}

	}
}
