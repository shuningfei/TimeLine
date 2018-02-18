#!/bin/sh

# $1: -out.mln
# $2: .result
# $3: -test.db
# $4: relation to be classified

./infer -ms -i $1 -r $2 -e $3 -q $4