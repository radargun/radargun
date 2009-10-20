#!/bin/bash

runOnCluster()
{
	config="$1"
	scaling="2 4 6 8"
	for size in $scaling
	do
		nohup ./cluster.sh start $product $config $size
		outputFileName=data_${product}_${config}_${size}.csv
      		while [ ! -e $outputFileName ]
     		do
         		echo "Waiting for report [ $outputFileName ]"
         		sleep 10
      		done
      		sleep 30
      		mv $outputFileName output/
	done
}

doLoop()
{
	echo Product is $product and cfg set is $configs
	for config in $configs
	do
		echo running set for $product using $config
		runOnCluster $config
	done
}

jbc300()
{
	#### JBoss Cache 3.0.0
	product="jbosscache-3.0.0"
	configs="mvcc-repl-sync.xml mvcc-repl-sync-br.xml mvcc-repl-async.xml"
	doLoop $product
}

jbc220()
{
	#### JBoss Cache 2.2.0
	product="jbosscache-2.2.0"
	configs="pess-repl-sync.xml pess-repl-sync-br.xml pess-repl-async.xml"
	doLoop $product
}

ehcache()
{
	#### EHCache
	product="ehcache-1.5.0"
	configs="ehcache-repl-sync.xml ehcache-repl-async.xml"
        doLoop $product
}

coherence()
{
	#### Coherence
	product="coherence-3.3.1"
	configs="repl-cache.xml dist-cache.xml"
        doLoop $product 
}

startTime=`date +%s`
configs="" # This is global and accessible by all functions.

if [ -e "output" ]
then
	mv output output`date +%s`.old
fi
mkdir output

# jbc300
# jbc220
# ehcache
coherence
echo Generating charts ...

./generateChart.sh -reportDir output

endTime=`date +%s`
minsTaken=`echo "scale=4;($endTime - $startTime)/60" | bc`

echo -----
echo -----
echo Took $minsTaken minutes to run!
echo -----
echo -----

