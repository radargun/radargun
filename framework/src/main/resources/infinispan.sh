#!/bin/bash

scaling="2 4 6 8"
configs="repl-sync.xml repl-async.xml dist-sync.xml dist-async.xml"

products="infinispan-4.0.0"

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

configs="mvcc-repl-sync.xml mvcc-repl-async.xml mvcc-repl-sync-br.xml mvcc-repl-async-br.xml"

products="jbosscache-3.0.0"

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

./generateChart.sh -reportDir output
