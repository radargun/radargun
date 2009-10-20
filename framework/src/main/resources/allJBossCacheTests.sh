#!/bin/bash

scaling="2 4 6 8"
configs="pess-repl-sync.xml pess-repl-sync-br.xml pess-repl-async.xml mvcc-repl-sync.xml mvcc-repl-sync-br.xml mvcc-repl-async.xml"
#configs="distributed replicated"
products="jbosscache-3.0.0"

mkdir output

for product in $products
do
	for config in $configs
	do
		for size in $scaling
		do
			nohup ./cluster.sh start $product $config $size

			outputFileName=data_${product}_${config}_${size}.csv
			while [ ! -e $outputFileName ]
			do
				echo "Waiting for report [ $outputFileName ]"
				sleep 5
			done
			sleep 60
			mv $outputFileName output/
		done
	done
done


echo Generating charts ... 

./generateChart -reportDir output
