#!/bin/bash

. /etc/rc.d/init.d/functions

set -x


kill_agent_linux() {
   echo "Killing linux agents..."
   ssh root@$1 "service vmops stop 2>&1 &"
}


kill_agent_solaris() {
   echo "Killing solaris agents..."
   ssh root@$1 "svcadm disable vmops 2>&1 &"
}


kill_server() {
echo "Killing management server and usage process..."
ssh root@$1 "service cloud-management stop; service cloud-usage stop 2>&1 &"
}


cleanup_linux() {
  kill_agent_linux $1 $2
  echo $2
  ssh root@$1 "cd $2/agent; ./scripts/iscsi/stopvm.sh -a -u -f" > /dev/null
  if [ $? -gt 0 ]; then echo "failed to cleanup vm on $1"; return 2; fi
  ssh root@$1 "rm -f /dev/disk/by-vm/*" &> /dev/null
  if [ $? -gt 0 -a $? -ne 255 ]; then echo "failed to cleanup iscsi mounts on $1"; return 2; fi
  ssh root@$1 "cd $2/agent; ./scripts/vnetcleanup.sh -a" &> /dev/null
  ssh root@$1 "iscsiadm -m session -u all; iscsiadm -m node -o delete" &> /dev/null
  if [ $? -gt 0 ]; then echo "failed to cleanup vnet on $1"; return 2; fi
  t=$(date  +"%h%d_%H_%M_%S")
  ssh root@$1 "mkdir /var/log/xen/logs.$t; mv /var/log/xen/xend.log /var/log/xen/logs.$t/xend.log.$t; echo sssssssssssssssssssssssss > /var/log/xen/xend.log"
  echo "Cleaning logs for linux on $1"
  ssh root@$1 "mkdir $3/logs.$t; mv $3/agent.log $3/logs.$t/agent.log.$t; echo sssssssssssssssssssssssss > $3/agent.log"
  return 0
}

cleanup_solaris() {
  kill_agent_solaris $1 $3
  t=$(date  +"%h%d_%H_%M_%S")
  guid=$(ssh root@$1 "zfs list | grep tank/vmops.*/template\$ | awk 'BEGIN { FS = \"/\" } { print \$3 }' | awk 'BEGIN { FS = \"\$\" } { print \$1 }'")
  ssh root@$1 "svcadm disable vmops; $3/agent/scripts/iscsi/comstar/zfs_destroy.sh tank/vmops/$guid/vm; $3/agent/scripts/iscsi/comstar/host_group_destroy.sh" > /dev/null
  if [ $? -gt 0 ]; then echo "failed to destroy on $1"; return 2; fi
  ssh root@$1 "zfs create $2; zfs set sharenfs=rw,anon=0 $2" > /dev/null
  if [ $? -gt 0 ]; then echo "failed to enable sharing on $1"; return 2; fi
  ssh root@$1 "mkdir $4/logs.$t; mv $4/logs.$t/agent.log $4/agent.log.$t  ;echo sssssssssssssssssssssssss > $4/agent.log"
  echo "Cleaning logs for solaris on $1"
  ssh root@$1 "rm -f $4/create*.log $4/del*.log"
  return 0
}

cleanup_server() {
  kill_server $1 $2
  t=$(date  +"%h%d_%H_%M_%S")
  ssh root@$1 "mkdir $3/logs.$t; mv $3/vmops.log $3/logs.$t/vmops.log.$t  ;echo sssssssssssssssssssssssss > $3/vmops.log"
  ssh root@$1 "mv $3/vmops_usage.log $3/logs.$t/vmops_usage.log.$t  ;echo sssssssssssssssssssssssss > $3/vmops_usage.log.log"
  ssh root@$1 "mv $3/cloud-management.stderr $3/logs.$t/cloud-management.stderr.$t  ;echo sssssssssssssssssssssssss > $3/cloud-management.stderr"
  ssh root@$1 "mv $3/cloud-usage.stderr $3/logs.$t/cloud-usage.stderr.$t  ;echo sssssssssssssssssssssssss > $3/cloud-usage.stderr"
  echo "Cleaning logs for management server"
  return 0
}


dir=$(dirname "$0")
if [ -f $dir/../deploy.properties ]; then
  . "$dir/../deploy.properties"
fi

if [ "$COMPUTE" == "" ]; then
  printf "ERROR: Need to specify the computing servers\n"
  exit 1
fi

if [ "$ROUTER" == "" ]; then
  printf "ERROR: Need to specify the routing servers\n"
  exit 2
fi

if [ "$STORAGE" == "" ]; then
  printf "ERROR: Need to specify the storage servers\n"
  exit 3
fi

if [ "$USER" == "" ]; then
  printf "ERROR: Need tospecify the user\n"
  exit 4
fi

for h in "$COMPUTE $ROUTER"
do
  kill_agent_linux $h $VMOPSDIR
done

for h in "$STORAGE"
do
  kill_agent_solaris $h $VMOPSDIR
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
  cleanup_solaris $i $VMDIR  $VMOPSDIR $LOGDIR
done

for i in $SERVER
do
  echo "Starting cleanup on $i";
  cleanup_server $i $VMOPSDIR $LOGDIR
done
