package edu.unh.cs980.variations;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;

import edu.unh.cs980.utils.ProjectUtils;

public class UnigramAnalyzer extends Analyzer {
	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer source = new StandardTokenizer();
		CharArraySet stopWords = ProjectUtils.getCustomStopWordSet();

		TokenStream filter = new LowerCaseFilter(source);
		TokenStream filter2 = new StopFilter(filter, stopWords);
		return new TokenStreamComponents(source, filter2);
	}

}