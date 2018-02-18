#!/bin/bash

# Run Caevo on raw semeval-Data

RAW='corpus1/raw/*'

export JWNL=src/test/resources/jwnl_file_properties.xml
echo $JWNL
for file in $RAW; do
	filex=$(basename $file)
	echo $filex
	./runcaevoraw.sh corpus1/raw/$filex
	cp sieve-output.xml CAEVO_Output/corpus1/$filex #${file%.txt}
	echo "running Caevo on $file"
done