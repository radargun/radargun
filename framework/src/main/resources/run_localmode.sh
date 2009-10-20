#!/bin/bash

echo ""
echo "=== Cache Benchmark Framework ==="
echo " Runs a cache benchmark in LOCAL mode - no cluster is started."

help_and_exit() {
  echo "Usage: "
  echo '  $ run_localmode.sh [-f config_file] [-d product description or version] -p cache_product_module -c cache_config_file'
  echo ""
  echo "   -c   Path to the framework configuration file, which defaults to cachebench-local.xml on the classpath"
  echo ""
  echo "   -d   Some additional descriptive text about the product being tested, e.g., version or config used, for use in reports"
  echo ""
  echo "   -p   Cache product module.  REQUIRED."
  echo ""
  echo "   -c   Cache product configuration file.  REQUIRED."
  echo ""
  exit 0
}

CFG_FILE=cachebench-local.xml
DESC=""

### read in any command-line params
while ! [ -z $1 ]
do
  echo "processing arg [$1]"
  case "$1" in
    "-f")
      CFG_FILE=$2
      shift
      ;;
    "-d")
      DESC=$2
      shift
      ;;
    "-p")
      CACHE_PRODUCT=$2
      shift
      ;;
    "-c")
      CC=$2
      shift
      ;;
    *)
      help_and_exit
      ;;
  esac
  shift
done

if [ -z $CACHE_PRODUCT ] ; then
  echo "FATAL: Need to specify a cache product to run!"
  exit 1
fi 

if [ -z $CC ] ; then
  echo "FATAL: Need to specify a cache configuration!"
  exit 1
fi

## Build up classpath
if [[ $0 =~ bin/.*.sh ]] ; then
   DIR_PREFIX="."
else
   DIR_PREFIX=".."
fi

FWK_CP=${DIR_PREFIX}/conf:${DIR_PREFIX}/framework/cache-benchmark-framework.jar
for jar in ${DIR_PREFIX}/framework/lib/*.jar ; do
  FWK_CP=${FWK_CP}:${jar}
done

if ! [ -e ${DIR_PREFIX}/cache-providers/${CACHE_PRODUCT} ] ; then
  echo "FATAL: Unknown cache product module ${CACHE_PRODUCT}"
  exit 1
fi

MOD_CP=${DIR_PREFIX}/cache-providers/${CACHE_PRODUCT}/conf
for jar in ${DIR_PREFIX}/cache-providers/${CACHE_PRODUCT}/*.jar ; do
  MOD_CP=${MOD_CP}:${jar}
done
for jar in ${DIR_PREFIX}/cache-providers/${CACHE_PRODUCT}/lib/*.jar ; do
  MOD_CP=${MOD_CP}:${jar}
done

echo "  Starting JVM with options ${JVM_OPTIONS}"
echo "  Note: to set additional VM options, export these as JVM_OPTIONS"
echo ""

JVM_OPTIONS="${JVM_OPTIONS} -DcacheBenchFwk.productSuffix=${DESC} -DcacheBenchFwk.cacheProductName=${CACHE_PRODUCT} -DcacheBenchFwk.cacheConfigFile=${CC} -Djava.net.preferIPv4Stack=${preferIPv4Stack} -DlocalOnly=true"

java ${JVM_OPTIONS} -cp ${FWK_CP}:${MOD_CP} org.cachebench.CacheBenchmarkRunner ${CFG_FILE}








