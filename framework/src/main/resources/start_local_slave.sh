#!/bin/bash

## Load includes
if [ "x$CBF_HOME" = "x" ]; then DIRNAME=`dirname $0`; CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
. ${CBF_HOME}/bin/includes.sh

help_and_exit() {
<<<<<<< HEAD
  echo "Usage: "
  echo '  $ start_local_slave.sh -m MASTER_IP -plugin plugin_name'
  echo ""
  echo "   -mh       MASTER host. This is REQUIRED."
  echo ""
  echo "   -mp       MASTER's port. Optional, if not present defauls to 2103."
  echo ""
  echo "   -i        Node's index. This will be prepended to the log file."
  echo ""
  echo "   -plugin   The plugin to be benchmarked. Shoule be a dir in ../plugins/<plugin_dir>"
  echo ""
  echo "   -h        Displays this help screen"
  echo ""
=======
  wrappedecho "Usage: "
  wrappedecho '  $ start_local_slave.sh -m MASTER_IP:PORT -plugin plugin_name'
  wrappedecho ""
  wrappedecho "   -m     Connection to MASTER server.  IP address and port is needed.  This is REQUIRED."
  wrappedecho ""
  wrappedecho "   -h     Displays this help screen"
  wrappedecho ""
>>>>>>> 332864b8b4621622ab21ca5d1a614001314d6c59
  exit 0
}

welcome "This script is used to launch the local slave process."

### read in any command-line params
while ! [ -z $1 ]
do
  case "$1" in
    "-mh")
      MASTER_HOST=$2
      shift
      ;;
    "-mp")
      MASTER_PORT=$2
      shift
      ;;
    "-i")
      NODE_INDEX=$2
      shift
      ;;
    "-plugin")
      PLUGIN=$2
      shift
      ;;
    *)
      help_and_exit
      ;;
  esac
  shift
done

if [ -z $MASTER_HOST ] ; then
  echo "FATAL: required information (-m) missing!"
  help_and_exit
fi

CONF=""
if [ "$MASTER_PORTx" != "x" ] ; then
  CONF="$CONF -masterPort $MASTER_PORT"
fi

DJAVA=""
if [ "$NODE_INDEXx" != "x" ] ; then
  DJAVA="$DJAVA -Dlog4j.file.prefix=$NODE_INDEX"
fi

if [ -z $PLUGIN ] ; then
  echo "FATAL: required information (-plugin) missing!"
  help_and_exit
fi

<<<<<<< HEAD
if ! [ -d ${CBF_HOME}/plugins/$PLUGIN ] ; then
  echo "FATAL: unknown plugin ${PLUGIN}! Directory doesn't exist in ${CBF_HOME}/plugins!"
  exit 2
fi


echo "MASTER_HOST: $MASTER_HOST Plugin: $PLUGIN Master port: $MASTER_PORT nodeIndex: $NODE_INDEX"

cp=${CBF_HOME}/conf
for jar in ${CBF_HOME}/lib/*.jar ; do
  cp=$cp:$jar
done
for jar in ${CBF_HOME}/plugins/${PLUGIN}/lib/*.jar ; do
  cp=$cp:$jar
done
cp=$cp:${CBF_HOME}/plugins/${PLUGIN}/conf

nohup java -cp $cp -Xms1G -Xmx1G $DJAVA -Djava.net.preferIPv4Stack=true -Dbind.address=${MYTESTIP_2} -cp $cp org.cachebench.fwk.Slave -masterHost $MASTER_HOST $CONF > out_slave_`hostname`.txt 2>&1 &
=======
add_fwk_to_classpath
add_plugin_to_classpath $PLUGIN
set_env

nohup java -classpath $CP ${JVM_OPTS} -Djava.net.preferIPv4Stack=true -Dbind.address=${BIND_ADDRESS} -Djgroups.bind_addr=${BIND_ADDRESS} org.cachebench.fwk.BenchmarkNode -serverHost $MASTER > out_slave_`hostname`.txt 2>&1 &
>>>>>>> 332864b8b4621622ab21ca5d1a614001314d6c59

echo "... done! Slave process started!"
echo ""

