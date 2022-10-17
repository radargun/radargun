#!/usr/bin/env bash

# -d 7778 --debug-suspend
export JAVA_HOME=/Users/dlovison/.sdkman/candidates/java/current

./clean.sh
./main.sh -c example.xml
./worker.sh
./worker.sh


tail -f stdout_main.out
