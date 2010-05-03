#!/bin/bash

. /etc/rc.d/init.d/functions

set -x

kill_agent_linux() {
   echo "Killing linux agents..."
   ssh root@$1 "service vmops stop 2>&1 &"
}

kill_server() {
echo "Killing management server and usage process..."
ssh root@$1 "service vmops-management stop; service vmops-usage stop 2>&1 &"
}

cleanup_linux() {
  kill_agent_linux $1 $2
  echo $2
  scp -r kvm_agent_test_scripts root@$1:/$2/agent/scripts/
  ssh root@$1 "cd $2/agent/scripts/kvm_agent_test_scripts; dos2unix *sh; chmod +x *sh; virsh list | grep ' *[0-9]' | awk ' { print \$1 } ' | xargs -n 1 ./cleanup.sh" > /dev/null
  if [ $? -gt 0 ]; then echo "failed to cleanup vm on $1"; fi
  t=$(date  +"%h%d_%H_%M_%S")
  echo "Cleaning logs for linux on $1"
  ssh root@$1 "mkdir $3/logs.$t; mv $3/agent.log $3/logs.$t/agent.log.$t; echo sssssssssssssssssssssssss > $3/agent.log"
  return 0
}

cleanup_solaris() {
  ssh root@$1 "cd /tank/vmops-nfs/vm && rm -rf * " > /dev/null
  if [ $? -gt 0 ]; then echo "failed to cleanup user vm on $1"; fi
  ssh root@$1 "cd /tank/vmops-nfs/template/private && cp -rf u000000 /root/. && rm -rf * && mv /root/u000000 ." > /dev/null
  if [ $? -gt 0 ]; then echo "failed to cleanup private templates on $1"; fi
  return 0
}


cleanup_server() {
  kill_server $1 $2
  t=$(date  +"%h%d_%H_%M_%S")
  ssh root@$1 "mkdir $3/logs.$t; mv $3/vmops.log $3/logs.$t/vmops.log.$t  ;echo sssssssssssssssssssssssss > $3/vmops.log"
  ssh root@$1 "mv $3/vmops_usage.log $3/logs.$t/vmops_usage.log.$t  ;echo sssssssssssssssssssssssss > $3/vmops_usage.log.log"
  ssh root@$1 "mv $3/vmops-management.stderr $3/logs.$t/vmops-management.stderr.$t  ;echo sssssssssssssssssssssssss > $3/vmops-management.stderr"
  ssh root@$1 "mv $3/vmops-usage.stderr $3/logs.$t/vmops-usage.stderr.$t  ;echo sssssssssssssssssssssssss > $3/vmops-usage.stderr"
  echo "Cleaning logs for management server"
  return 0
}


dir=$(dirname "$0")
if [ -f $dir/../deploy.properties ]; then
  . "$dir/../deploy.properties"
fi

if [ "$USER" == "" ]; then
  printf "ERROR: Need tospecify the user\n"
  exit 4
fi

for h in "$COMPUTE $ROUTER"
do
  kill_agent_linux $h $VMOPSDIR
done

for h in "$SERVER"
do
  kill_server $h $VMOPSDIR
done

for i in $COMPUTE
do
  echo "Starting cleanup on $i";
  cleanup_linux $i $VMOPSDIR $LOGDIR
done

for i in $ROUTER
do
  echo "Starting cleanup on $i";
  cleanup_linux $i $VMOPSDIR $LOGDIR
done

for i in $STORAGE
do
  echo "Starting cleanup on $i";
  cleanup_solaris $i
done

for i in $SERVER
do
  echo "Starting cleanup on $i";
  cleanup_server $i $VMOPSDIR $LOGDIR
done
