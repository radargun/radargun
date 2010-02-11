### Util functions for all of the shell scripts
#
# NOTE that this *needs* to be loaded via the following snippet:
#
#
#   if [ "x$CBF_HOME" = "x" ]; then CBF_HOME=`cd $DIRNAME/..; pwd` ; fi; export CBF_HOME
#   . ${CBF_HOME}/bin/includes.sh
#
# as a "side effect", the snippet also sets the CBF_HOME environment variable.

welcome() {
  SCRIPTNAME=`basename ${0}`
  echo "=== Cache Benchmark Framework: ${SCRIPTNAME} ==="
  wrappedecho "${1}"
  echo ""
}

wrappedecho() {
  echo "${1}" | fold -s -w 80
}

check_plugin_exists() {
  PLUGIN=${1}
  if ! [ -d ${CBF_HOME}/plugins/$PLUGIN ] ; then
    echo "FATAL: unknown plugin ${PLUGIN}! Directory doesn't exist in ${CBF_HOME}/plugins!"
    exit 2
  fi
}

add_to_classpath() {
  if [ -z $CP ] ; then
    CP=${1}
  else
    CP=${CP}:${1}
  fi
}

add_plugin_to_classpath() {
  PLUGIN=${1}
  check_plugin_exists $PLUGIN
  for i in ${CBF_HOME}/plugins/${PLUGIN}/lib/*.jar ; do
    add_to_classpath $i
  done
  add_to_classpath ${CBF_HOME}/plugins/${PLUGIN}/conf
}

add_fwk_to_classpath() {
  for i in ${CBF_HOME}/lib/*.jar ; do
    add_to_classpath $i
  done
  add_to_classpath ${CBF_HOME}/conf
}

set_env() {
   . ${CBF_HOME}/bin/environment.sh
}

wait_for_process() {
  PID=$1
  echo "Waiting for process $PID to complete"
  while ! [ "`ps ax | grep $PID | grep -v grep`" = "" ] ; do 
    echo "Waiting for process completion."
    sleep 5
  done  
}

get_port() {
  HOST_PORT=$1
  if [ "x`echo $HOST_PORT | grep :`" = "x" ]
  then
    PORT=""
  else
    PORT=`echo $HOST_PORT | sed -e 's/^.*://g'`
  fi
}

get_host() {
  HOST_PORT=$1
  HOST=`echo $HOST_PORT | sed -e 's/:.*$//g' -e 's/^\s*//g'`
}
