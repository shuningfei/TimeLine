#!/bin/bash

# crawl all data with a provided crawler


DEFILE = '../files/de-*.txt'

for file in ${DEFILE}; do
    ./bin/crawl.sh ${file} de sent/${file}-text.txt
done
