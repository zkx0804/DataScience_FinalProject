#!/bin/bash

# Desc:     This script will run all of the methods and store them in the method_runs directory.
#           It is assumed that you have access to the /trec_data folder on the server where all of
#           the required files are stored. If not, you will need to change the paths 
#           for the variables pointing to trec_data/ (see comments below)

# Note:     For Kevin's and Bindu's methods, when given a choice between page or section variants,
#           I only demonstrate their page variants below. This is because the section variants are taking
#           too long to run (and may not work) on the server.

#           If you want to evaluate the section variants for query_rm_qe, entitySimilarity, and doc_rm_qe,
#           Then change the page type to section (see my comments for those methods below)
#           This also means that they should be evaluated with hierarchical, not article, qrels.

# Argument: Location of Team 1's JAR file (either in target/ after compiling or in /bin)

JAR=$1

# Pathway to unprocessedAllButBenchmark cbor (contains Wikipedia pages)
ALL_BUT_BENCH=/trec_data/unprocessedAllButBenchmark/unprocessedAllButBenchmark.cbor

# Pathway to Trec Car's paragraphCorpus cbor file
PAR_CORPUS=/trec_data/paragraphCorpus/dedup.articles-paragraphs.cbor

# Pathway to paragraphCorpus index (made using jar command: index)
INDEX=/trec_data/team_1/myindex

# Pathway to abstract index (made using jar command: abstract_indexer)
ABSTRACT=/trec_data/team_1/abstract

# Pathway to hyperlink database (made using jar command: hyperlink_indexer)
HYPERLINK=/trec_data/team_1/entity_mentions.db

# Pathway to -gram Lucene index (made using jar command: gram_indexer)
GRAM=/trec_data/team_1/gram

# Pathway to topic paragraphs
PARAGRAPHS=/trec_data/team_1/paragraphs/

#Pathway to descent data
DESCENT=/trec_data/team_1/descent_data/

# Pathway to trec_eval program
TREC_EVAL=/trec_data/trec_eval

# Pathway to benchmarkY1's hierarchical qrels file
TRAIN_HIER_QRELS=/trec_data/benchmarkY1-train/train.pages.cbor-hierarchical.qrels

# Pathway to articles qrels file
TRAIN_ARTICLE_QRELS=/trec_data/benchmarkY1-train/train.pages.cbor-article.qrels

# Pathway to benchmarkY1's train.pages.cbor-outlines file
TRAIN_OUTLINE_CBOR=/trec_data/benchmarkY1-train/train.pages.cbor-outlines.cbor

# This function is used to run methods run through the ranklib_query command in the jar.
run_ranklib_query_method () {
    # Create subdirectory to hold method results and eval stats
    METHOD=$1
    METHOD_DIR=method_results/$1
    METHOD_RESULT=$METHOD_DIR/query_results.run
    EVAL_RESULT=$METHOD_DIR/trec_eval_stats.txt
    mkdir -p $METHOD_DIR

    # Run method and write to corresponding method_results dir
    echo "=== Running Method: $1 ==="
    java -jar $JAR embedding query\
        --descent_data $DESCENT\
        --paragraphs $PARAGRAPHS\
        --out $METHOD_RESULT\
        --indexPath $INDEX\
        --method $METHOD\
        --queryPath $TRAIN_OUTLINE_CBOR
    echo -e Runfile Stored in $METHOD_RESULT \\n

    # Write trec eval stats of method to corresponding method_results dir
    echo "=== Evaluating Method: $1 ==="
    $TREC_EVAL -c $TRAIN_HIER_QRELS $METHOD_RESULT > $EVAL_RESULT
    echo -e Trec eval results stored in $EVAL_RESULT \\n\\n
}

# Create directory to store results if it doesn't exist
mkdir -p method_results

# Jordan's Methods

run_ranklib_query_method hier_ascent
run_ranklib_query_method hier_clusters
run_ranklib_query_method hier_subclusters
run_ranklib_query_method hier_query_variations
run_ranklib_query_method hier_reduction_methods
run_ranklib_query_method hier_metrics
run_ranklib_query_method perturbation_embedding


# Kevin's method
METHOD_NAME=nlp_query_variation
echo "=== Running $METHOD_NAME  ==="
mkdir -p method_results/$METHOD_NAME
java -jar $JAR $METHOD_NAME\
    page\
    false\
    $INDEX\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/$METHOD_NAME/query_results.run

echo "=== Evaluating Method: $METHOD_NAME (only on article qrels) ==="
ERESULT=method_results/$METHOD_NAME/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/$METHOD_NAME/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n


# Kevin + Bindu's method
METHOD_NAME=context_queryeexpansion
echo "=== Running $METHOD_NAME  ==="
mkdir -p method_results/$METHOD_NAME
java -jar $JAR $METHOD_NAME\
    page\
    false\
    $INDEX\
    $ABSTRACT\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/$METHOD_NAME/query_results.run

echo "=== Evaluating Method: $METHOD_NAME (only on article qrels) ==="
ERESULT=method_results/$METHOD_NAME/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/$METHOD_NAME/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n


# Bindu's pararank
METHOD_NAME=pararank_with_depparser
echo "=== Running $METHOD_NAME  ==="
mkdir -p method_results/$METHOD_NAME
java -jar $JAR $METHOD_NAME\
    $INDEX\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/$METHOD_NAME/query_results.run

echo "=== Evaluating Method: $METHOD_NAME (only on article qrels) ==="
ERESULT=method_results/$METHOD_NAME/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/$METHOD_NAME/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n


# Bindu's top_k
METHOD_NAME=top_k_treecontextualsimilarity
echo "=== Running $METHOD_NAME  ==="
mkdir -p method_results/$METHOD_NAME
java -jar $JAR $METHOD_NAME\
    $INDEX\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/$METHOD_NAME/query_results.run

echo "=== Evaluating Method: $METHOD_NAME (only on article qrels) ==="
ERESULT=method_results/$METHOD_NAME/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/$METHOD_NAME/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n





