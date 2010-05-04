#!/bin/bash

. /etc/rc.d/init.d/functions

set -x

kill_server() {
ssh root@$1 "service cloud-management stop; service cloud-usage stop 2>&1 &"
}

cleanup_solaris() {
  kill_agent_solaris $1 $3
  t=$(date  +"%h%d_%H_%M_%S")
  guid=$(ssh root@$1 "zfs list | grep tank/vmops.*/template\$ | awk 'BEGIN { FS = \"/\" } { print \$3 }' | awk 'BEGIN { FS = \"\$\" } { print \$1 }'")
  ssh root@$1 "svcadm disable vmops; $3/agent/scripts/storage/zfs/iscsi/comstar/zfs_destroy.sh tank/vmops/$guid/vm; $3/agent/scripts/storage/zfs/iscsi/comstar/host_group_destroy.sh; for zf in \`zfs list | grep tank/vmops/$guid/template/private | grep -v tank/vmops/$guid/template/private/u000000.* | grep -v tank/vmops/$guid/template/private\$ | awk '{print \$1}'\`; do echo \$zf; zfs destroy -r \$zf; done" > /dev/null
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


cleanup_linux(){
ssh root@$1 "for vm in \`xe vm-list | grep name-label | grep -v Control | awk '{print \$4}'\`; do uuid=\`xe vm-list name-label=\$vm | grep uuid | awk '{print \$5}'\`; echo \$uuid; xe vm-shutdown uuid=\$uuid; xe vm-destroy uuid=\$uuid; done"
ssh root@$1 "skip=\`xe vlan-list tag=20 |grep uuid| awk '{print \$5}'\`; for vlan in \`xe vlan-list | grep uuid | grep -v \$skip | awk '{print \$5}'\`; do echo \$vlan; xe vlan-destroy uuid=\$vlan; done"
ssh root@$1 "for vlan in \`xe network-list | grep name-label | grep VLAN| awk '{print \$4}'\`; do echo \$vlan; uuid=\`xe network-list name-label=\$vlan | grep uuid | awk '{print \$5}'\`; xe network-destroy uuid=\$uuid; done"
}


dir=$(dirname "$0")
if [ -f $dir/../deploy.properties ]; then
  . "$dir/../deploy.properties"
fi

if [ "$USER" == "" ]; then
  printf "ERROR: Need tospecify the user\n"
  exit 4
fi


#Delete all active users and kill the management server
for h in "$SERVER"
do
  kill_server $h $VMOPSDIR
done

#Kill vms and delete vlans on all computing hosts
for i in $COMPUTE
do
  cleanup_linux $i
done

#Cleanup zfs
for i in $STORAGE
do
  echo "Starting cleanup on $i";
  cleanup_solaris $i $VMDIR  $VMOPSDIR $LOGDIR
done

#Cleanup management server
for i in $SERVER
do
  echo "Starting cleanup on $i";
  cleanup_server $i $VMOPSDIR $LOGDIR
done