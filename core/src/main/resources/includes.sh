### Util functions for all of the shell scripts
#
# NOTE that this *needs* to be loaded via the following snippet:
#
#
#   if [ "x$RADARGUN_HOME" = "x" ]; then RADARGUN_HOME=`cd $DIRNAME/..; pwd` ; fi; export RADARGUN_HOME
#   . ${RADARGUN_HOME}/bin/includes.sh
#
# as a "side effect", the snippet also sets the RADARGUN_HOME environment variable.

welcome() {
  SCRIPTNAME=`basename ${0}`
  echo "=== Radargun: ${SCRIPTNAME} ==="
  wrappedecho "${1}"
  echo ""
}

wrappedecho() {
  echo "${1}" | fold -s -w 80
}

check_plugin_exists() {
  PLUGIN=${1}
  if ! [ -d ${RADARGUN_HOME}/plugins/$PLUGIN ] ; then
    echo "FATAL: unknown plugin ${PLUGIN}! Directory doesn't exist in ${RADARGUN_HOME}/plugins!"
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
  for i in ${RADARGUN_HOME}/plugins/${PLUGIN}/lib/*.jar ; do
    add_to_classpath $i
  done
  add_to_classpath ${RADARGUN_HOME}/plugins/${PLUGIN}/conf
}

add_fwk_to_classpath() {
  for i in ${RADARGUN_HOME}/lib/*.jar ; do
    add_to_classpath $i
  done
  add_to_classpath ${RADARGUN_HOME}/conf
}

set_env() {
  . `dirname $0`/environment.sh

  # If the user specified a JAVA_HOME, use that
  if [ -n "$JAVA_HOME" ]
  then
    JAVA=$JAVA_HOME/bin/java
  else
    JAVA=`which java`
  fi

  # If on cygwin, java is a regular Windows program.
  # So we have to convert the classpath to Windows paths.
  if [ "$OSTYPE" == "cygwin" ]
  then
    CP=`cygpath -wp "$CP"`
  fi
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
