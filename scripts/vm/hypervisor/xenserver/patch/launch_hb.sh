#!/bin/bash

#set -x
 
usage() {
  printf "Usage: %s [uuid of this host] [interval in seconds]\n" $(basename $0) >&2

}

if [ -z $1 ]; then
  usage
  exit 2
fi

if [ -z $2 ]; then
  usage
  exit 3
fi

if [ ! -f /opt/xensource/bin/heartbeat.sh ]; then
  printf "Error: Unable to find heartbeat.sh to launch\n" >&2
  exit 4
fi

for psid in `ps -ef | grep heartbeat | grep -v grep | awk '{print $2}'`; do
  kill $psid
done

nohup /opt/xensource/bin/heartbeat.sh $1 $2 >/var/log/heartbeat.log 2>&1 &
echo "======> DONE <======"
