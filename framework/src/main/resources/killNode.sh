#!/bin/sh

##################################################################################
#
# This script allows you to kill benchmark runner instances started with runNode.sh
#
##################################################################################

ps -elf | grep cacheBenchFwk.cacheWrapperClassName | grep -v grep | cut -c 14-20 | xargs -r kill -9
ps -elf | grep allJBossCacheTests.sh | grep -v grep | cut -c 14-20 | xargs -r kill -9
ps -elf | grep /runNode.sh | grep -v grep | cut -c 14-20 | xargs -r kill -9
exit 0