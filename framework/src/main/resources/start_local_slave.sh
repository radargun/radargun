#!/bin/bash

echo ""
echo "=== Cache Benchmark Framework ==="
echo " This script is used to launch the local slave process."
echo ""

help_and_exit() {
  echo "Usage: "
  echo '  $ start_local_slave.sh -m MASTER_IP:PORT -plugin plugin_name'
  echo ""
  echo "   -m     Connection to MASTER server.  IP address and port is needed.  This is REQUIRED."
  echo ""
  echo "   -h     Displays this help screen"
  echo ""
  exit 0
}

### read in any command-line params
while ! [ -z $1 ] 
do
  case "$1" in
    "-m")
      MASTER=$2
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

if [ -z $MASTER ] ; then
  echo "FATAL: required information (-m) missing!"
  help_and_exit
fi

if [ -z $PLUGIN ] ; then
  echo "FATAL: required information (-plugin) missing!"
  help_and_exit
fi


echo "Master: $MASTER Plugin: $PLUGIN"

cp="conf"
for jar in lib/*.jar ; do
  cp=$cp:$jar
done
for jar in plugins/${PLUGIN}/lib/*.jar ; do
  cp=$cp:$jar
done
cp=$cp:plugins/${PLUGIN}/conf

nohup java -cp $cp -Xms1G -Xmx1G -Djava.net.preferIPv4Stack=true -Dbind.address=${MYTESTIP_2} -cp $cp org.cachebench.fwk.BenchmarkNode -serverHost $MASTER > out_slave_`hostname`.txt 2>&1 &

echo ""
echo "... done! Slave process started!"
echo ""

