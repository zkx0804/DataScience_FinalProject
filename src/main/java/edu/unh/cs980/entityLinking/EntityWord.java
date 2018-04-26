package edu.unh.cs980.entityLinking;

public class EntityWord {

	// "@URI": "http://dbpedia.org/resource/Kingdom_of_Prussia",
	// "@support": "5158",
	// "@types":
	// "Schema:Place,DBpedia:Place,DBpedia:PopulatedPlace,Schema:Country,DBpedia:Country",
	// "@surfaceForm": "Prussia",
	// "@offset": "79",
	// "@similarityScore": "0.5914864908487477",
	// "@percentageOfSecondRank": "0.6763498164798217"

	private String URI;

	private int support;
	private String types;
	private String surfaceForm; // Entity Name
	private int offset;
	private float similarityScore;
	private float percentageOfSecondRank;

	public String getURI() {
		return URI;
	}

	public void setURI(String uRI) {
		URI = uRI;
	}

	public int getSupport() {
		return support;
	}

	public void setSupport(int support) {
		this.support = support;
	}

	public String getTypes() {
		return types;
	}

	public void setTypes(String types) {
		this.types = types;
	}

	public String getSurfaceForm() {
		return surfaceForm;
	}

	public void setSurfaceForm(String surfaceForm) {
		this.surfaceForm = surfaceForm;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public float getSimilarityScore() {
		return similarityScore;
	}

	public void setSimilarityScore(float similarityScore) {
		this.similarityScore = similarityScore;
	}

	public float getPercentageOfSecondRank() {
		return percentageOfSecondRank;
	}

	public void setPercentageOfSecondRank(float percentageOfSecondRank) {
		this.percentageOfSecondRank = percentageOfSecondRank;
	}

	@Override
	public String toString() {
		return "EntityWord [" + surfaceForm + "]";
	}
}
