Terracotta replicates through an centralized server, so this needs to be installed and configured as well.
Before running the tests on your environment, please follow the following steps in order to install the Terracotta server.

1) Download and install terracotta from http://www.terracotta.org. For simplicity we'll assume that Terracotta is install in TC_ROOT
2) Create the DSO boot jar by running TC_ROOT/bin/make-boot-jar.sh. This will generate a jar file in the directory TC_ROOT/lib/dso-boot
 E.g. lib/dso-boot/dso-boot-hotspot_linux_150_11.jar. This jar file is system specific (as its name shows), so it's name might
 vary from system to system.
3) Update ./config.sh
  a) make sure TC_HOME points to the terracotta installation directory (i.e. TC_ROOT)
  b) make sure that the jar file generated at 2) is in the class path
  Note: each line that needs to be changed is prefixed by a comment: "#next line should be modified based on the environment"
4) Before running the tests, make sure you start the TC server first. A server with default configuration can be started using the script: TC_ROOT/samples/start-demo-server.sh
5) Run the tests by by using allJBossCacheTests.sh or runNode.sh. The third parameter of runNode.sh the test config file name, which is missing in the case of Terracotta.
   Make sure you supply a meaningful placeholder for this (e.g. 'teracotta-250'), as the BenchmarkFwk relies on this name for prefixing the report file.

Notes:
  - steps 1-3 should only be performed once
  - if you would like to use another Terracotta client config file, update the "-Dtc.config=$THIS_DIR/tc-client-config.xml" part in ./config.sh

