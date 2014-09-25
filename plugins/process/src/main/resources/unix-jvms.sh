#!/bin/sh
source $(dirname $0)/unix-func.sh
findjvms $1
echo $JVMS;

