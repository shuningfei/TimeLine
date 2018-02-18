#!/bin/bash
#author: Jani, Sunhyung
#date: June 2015

# Run Caevo on raw semeval-Data

RAW='../rawtrain/wdot/*'
#CAE='../caevo-master'
FLIST='../rawtrain/wdot/*' #filelist'

cd ../caevo-master/
./download-wordnet-dictionaries 
export JWNL=src/test/resources/jwnl_file_properties.xml 
echo $JWNL
mvn compile
for file in $RAW; do
	filex=$(basename $file)
	./runcaevoraw.sh ../rawtrain/wdot/$filex
	cp sieve-output.xml ../CAEVO_Output/$filex #${file%.txt}
	echo "running Caevo on $file"
done
