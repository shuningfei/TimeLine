#!/bin/sh

# $1: .mln
# $2: -out.mln
# $3: -train.db
# $4: relation to be classified
./learnwts -d -i $1 -o $2 -t $3 -ne $4