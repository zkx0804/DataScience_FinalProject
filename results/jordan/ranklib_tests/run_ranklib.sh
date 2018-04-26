# These are the RankLib parameters used to train the weights of the methods in this directory
 java -jar RankLib-2.1-patched.jar \
     -train ranklib_features.txt \
     -ranker 4 \
     -metric2t map \
     -kcv 5 \
     -tvs 0.3 \
     -kcvmd models/
