package edu.unh.cs980.WordEmbedding;

import org.apache.lucene.search.Query;

 class ContexualQueryObj {
	String queryStr;
	Query query;

	public void setQueryStr(String str) {
		this.queryStr = str;
	}

	public String getQueryStr() {
		return this.queryStr;
	}

	public void setQuery(Query q) {
		this.query = q;
	}

	public Query getQuery() {
		return this.query;
	}
}
