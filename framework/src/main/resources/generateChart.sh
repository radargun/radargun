#!/bin/bash

CP=.:classes/production/Framework:./conf

for i in lib/*.jar
do
   CP=$CP:$i
done

java -cp $CP org.cachebench.reportgenerators.ChartGenerator ${*}
