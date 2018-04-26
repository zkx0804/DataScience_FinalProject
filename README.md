## Results and Report
The newest results for prototype 3 can be found in the results_prototype3 directory. The results are as follows

**results_prototype3/report.pdf**: The group report for the current prototype.

**results_prototype3/jordan/**: Runfiles and trec eval stats for each of Jordan's methods

**results_prototype3/kevin/**: Trec eval stats and run files for Kevin's methods.

**results_prototype3/bindu/**: Trec eval stats and run files for Bindu's methods.
 
 **results_prototype3/public_test/** Compressed run files for runs on benchmark_kY1_public_query test data. These are the ones we want evaluated.
 

___
## Installation Instructions
Because of the balooning size of the precompiled jar, it is no longer being tracked on GitHub. You can find a precompiled version of prototype 3's program on the server at: **/trec_data/team_1/program.jar**

You may also compile the jar directly from the source code in this repository by entering the following command, or by running ./compile.sh while in the project directory:

```bash
mvn clean compile assembly:single
```

This will create a jar file in the target/ directory (which can be used instead of program.jar).
___
## Running Methods
The compiled jar file contains subcommands for querying, indexing, and other relevant functions.
**If you are just interested in running our methods**, you can call the run.sh script and pass as an argument the jar file obtained during the installation step. For example:

```bash
./run.sh /trec_data/team_1/program.jar
```

or using the assembled jar:
```bash
./run.sh target/DataSciene_FinalProject-0.3.0-SNAPSHOT-jar-with-dependencies.jar 
```


This will create a new directory called method_results. Each subdirectory inside of method_results contains the results of a particular method. This includes a run file and the output of trec_eval -c

Note that this run.sh script depends on files (such as our Lucene index) that are stored in /trec_eval on the server. If you are not running this on the server you will need to change the filepaths to point to the correct files (descriptions of the filepaths are in the comments of the run.sh script).
___
## Program Commands
The program is divided into the following subcommands:

 ___
 ### Sparql Downloader (sparql_downloader)

This command is used to query SPARQL and download abstracts (or links that are then uses to download Wikipedia pages). These are used to build topical models (from the first 50 abstracts). The topics are:  Biology, Computers, Cooking, Cuisine, Engineering, Environments, Events, Fashion, Games, Mathematics, Medicine, Organizations, People, Politics, Science, Society, Statistics, Technology, Tools, Travel, Warfare. 


```bash
program.jar sparql_downloader --method {abstracts, page}
```

Where:
average_abstract,combined,abstract_sdm,sdm_components,hyperlink,sdm,section_component

**method**: Is one of:
 - **abstracts**: Downloads topic abstracts and stores them them in an output directory named paragraphs/
 - **page**: Downloads Wikipedia pages and stores them in an output directory named pages/
 
  ___
 ### Topic Decomposer (topic_decomposer)

Decomposes each topic (a collection of paragraphs) into the most important paragraphs. Then decomposes the most important paragraphs into the most important sentences, and finally words. Decomposed topics are stored in the descent_data/ directory.


```bash
program.jar topic_decomposer --method run --paragraphs paragraphs/}
```

Where:
**paragraphs/**: Is the location to the paragraphs directory (retrieved by sparql_downloader). Defaults to /trec_data/team_1/paragraphs
  ___
 ### Embedding (embedding)
This command explores the embedding methods using topics describes previously. The command is divided into two subcommands: train and query. Train generates a file that can be run in RankLib (used for training weights). Query is used to run the actual queries and generate runfiles.

```bash
program.jar embedding query --indexPath INDEXPATH --queryPath QUERYPATH --out OUT --paragraphs PARAGRAPHS --descent_data DESCENT_DATA --method METHOD }
```

Where:
**INDEXPATH**: Location of the Lucene index (default: /trec_data/team_1/myindex)
**QUERYPATH**: Location of the query .cbor file
**OUT**: Location of the runfile to generate (default: query_results.run)
**PARAGRAPHS**: Location of the folder containing topic paragraphs (obtained by sparql_downloader) (default: /trec_data/team_1/paragraphs)
**DESCENT_DATA**: Location of the folder containing decomposed topics (obtained by topic_decomposer) (default: /trec_data/team_1/descent_data)

**METHOD**: is one of the follow (these methods are described in the report)
 - hier_ascent
 - hier_clusters
 - hier_subclusters
 - hier_query_variations
 - hier_reduction_variations
 - hier_metrics
 - perturbation_embedding
 
 

___
#### Abstract Indexer (abstract_indexer)
Given the location of unprocessAllButBenchmark page corpus, extracts page names (used to represent an entity) and the first three paragraphs (used to represent the abstract of the entity). Unigrams, bigrams, and windowed bigrams are also indexed for each of the entity abstracts.

The resulting Lucene index will be named "abstract" and is created in the current working directory. **Note that an already indexed version of abstract can be found on the server at: /trec_data/team_1/abstract**

```bash
program.jar abstract_indexer corpus
```

Where **corpus** is the location of the allButBenchmark corpus. 
 - A copy of the corpus is located on the server at: /trec_data/unprocessedAllButBenchmark/unprocessedAllButBenchmark.cbor
___

### Indexer (index)
Creates a Lucene index from a given paragraph corpus. Also annotates paragraphs with entities using Spotlight if relevant spotlight directory is given. **Note that an existing copy of this index is already on the server at: /trec_data/team_1/myindex**

```bash
program.jar index corpus [--spotlight_folder ""] [--out "index"]
```
Where:

**corpus**: Is the paragraphCorpus.cbor to create the Lucene index from.

**--out**: Is the name of the directory that the Lucene index will be created in. Default: "index"

**--spotlight_folder**: Is the directory where a runnable DBPedia Spotlight Jar and model are located. If the folder does not contain the required files, the contents are automatically downloaded and unpacked to the folder. If no folder is specified, then entity-linking with Spotlight is skipped during indexing. **A copy of the spotlight_folder is available at: /trec_data/team_1/spotlight_server**

 ___
### Natural Language Processor with entities relations relevance model:
Recognize entities from query using DBpedia spotlight API.
Initial run with BM25, then use NLP to process the contents from feedback run to get all existing entities relationships.  
If the entities from query also participate in the relationships, then increase the score of current document.
Finally produce a re-ranked documents.  
*Notice*: Since using NLP and DBpedia Spotligth API, the time cost for this method is increased. It will take approximately 40-50 minutes for all sections path queries.

```bash
program.jar nlp_query_variation query_type multi_thread index query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query using BM25
 - **section**: Section path query using BM25

  **multi_thread** is one of:
 - **true**: Use multi-thread function to create thread for each 50 queries. 
 - **false**: Use normal function to go through each query. 
 
 **index**: Is the location of the Lucene index directory.
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index. (benchmarkY1-train/train.pages.cbor)
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
  ___
### Similarity with Query Expansion along with dep parser:

Predict relevant entities by annotated abstract of the entities from query
Build expanded query = query + words from entity's page (like RM3/Relevance Model); run this query against paragraph index 
Using Page Queries: Using Lucene to retrieve abstract text of entities in query, and use Spotlight API to annotate the abstract to get entities relevant to query. Then rank the entities to expand the query, finally run expanded query against index using BM25. Using Dependency parser to get the parsed tree of the paragraph and for ranking traverse the paragraph from root to top five tree level  and find out the query word or query expansion word and ranked the paragraph based on the BM25 similarity.
In this method, I propose a method for measuring contextual similarity, an important type of semantic relationship, between entities. we can locate the page relevant to an entity. Using their tree, the semantic similarity between entities can be measured in different dimensions.Currently I am using on the Page Queries , However My belief is it should work well on section level .  

```bash
program.jar context_queryeexpansion query_type mulit_thread index abstract query_file [--out ContextQuerySimilarity.run]
```

Where:

**query_type** is one of:
 - **page**: Page query
 
 **multi_thread** is one of:
 - **true**: Use multi-thread function to generate expanded query. 
 - **false**: Use normal function. (Recommended)
 
 **index**: Is the location of the Lucene index directory.
 
 **abstract**: Is the location of the Lucene abstract index directory. 
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index. (benchmarkY1-train/train.pages.cbor)
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: ContextQuerySimilarity.run
 ___
 ### Similarity using Top level dependency parser tree:

In this method, Using Dependency parser to get the parsed tree of the paragraph of section path and for ranking traverse the paragraph from root to top three tree level  and find out the root query word and rank the paragraph based on  root query word with the BM25 similarity.I propose a method for measuring contextual similarity, an important type of semantic relationship, between entities. we can locate the page relevant to an entity. Using their tree, the semantic similarity between entities can be measured in different dimensions.

```bash
program.jar top_k_treecontextualsimilarity index cborfile outputlocationfile
```
___
### 	Similarity using Top words dependency Parser 
In this we are choosing some words from dependency parser . Dependency parser gives a nice tree  and it also tells about the semantic structure of the tree.. Which shows a hierarchical structure of the sentence. Instead of considering all the word of the paragraph, consider only, nsubj,cc,compound,root,nummod. Instead of taking all the paragraph in this method only focusing on the above word and based on the word doing ranking with BM25 Similarity.


```bash
program.jar pararank_with_depparser index cborfile outputlocationfile
```
___

