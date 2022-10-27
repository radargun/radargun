#!/usr/bin/env bash

# -d 7778 --debug-suspend
export JAVA_HOME=/Users/dlovison/.sdkman/candidates/java/current

./clean.sh
./main.sh -c example.xml
# ./worker.sh -J "-Djava.net.preferIPv4Stack=true -agentpath:/Users/dlovison/Documents/GitHub/diego-kitchen/ispn/async-profiler-2.8.3-macos/build/libasyncProfiler.so=start,event=cpu,file=profile1-%p.html,include=org/jgroups/*"
# ./worker.sh -J "-Djava.net.preferIPv4Stack=true -agentpath:/Users/dlovison/Documents/GitHub/diego-kitchen/ispn/async-profiler-2.8.3-macos/build/libasyncProfiler.so=start,event=cpu,file=profile2-%p.html,include=org/jgroups/*"

./worker.sh -d 7778 --debug-suspend
./worker.sh


tail -f stdout_main.out
