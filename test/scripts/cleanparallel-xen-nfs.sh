#!/bin/bash

. /etc/rc.d/init.d/functions

set -x

kill_server() {
ssh root@$1 "service vmops-management stop && service vmops-agent stop 2>&1 &"
}

cleanup_solaris() {
  ssh root@$1 "cd $PRIMARY && rm -rf * " > /dev/null
  if [ $? -gt 0 ]; then echo "failed to cleanup user vm on $1"; fi
  ssh root@$1 "cd $SECONDARY && rm -rf * " > /dev/null
  if [ $? -gt 0 ]; then echo "failed to cleanup secondary storage on $1"; fi
  return 0
}


cleanup_server() {
  kill_server $1
t=$(date  +"%h%d_%H_%M_%S")
ssh root@$1 "mkdir $2/logs.$t; mv $2/management-server.log $2/logs.$t/management-server.log.$t  ;echo sssssssssssssssssssssssss > $2/management-server.log"
ssh root@$1 "mkdir $2/logs.$t; mv $2/catalina.out $2/logs.$t/catalina.out.$t  ;echo sssssssssssssssssssssssss > $2/catalina.out"
  return 0
}


cleanup_linux(){
ssh root@$1 "for vm in \`xe vm-list | grep name-label | grep -v Control | awk '{print \$4}'\`; do uuid=\`xe vm-list name-label=\$vm | grep uuid | awk '{print \$5}'\`; echo \$uuid; xe vm-shutdown --force uuid=\$uuid; xe vm-destroy uuid=\$
uuid; done"
ssh root@$1 "skip=\`xe vlan-list tag=$VLAN |grep uuid| awk '{print \$5}'\`; for vlan in \`xe vlan-list | grep uuid | grep -v \$skip | awk '{print \$5}'\`; do echo \$vlan; xe vlan-destroy uuid=\$vlan; done"
ssh root@$1 "for vlan in \`xe network-list | grep name-label | grep VLAN| awk '{print \$4}'\`; do echo \$vlan; uuid=\`xe network-list name-label=\$vlan | grep uuid | awk '{print \$5}'\`; xe network-destroy uuid=\$uuid; done"
ssh root@$1 "for sr in \`xe sr-list type=nfs name-description=storage | grep uuid | awk '{print \$5}'\`; do pbd=\`xe pbd-list sr-uuid=\$sr | grep ^uuid | awk '{ print \$5}'\` ; echo \$pbd; xe pbd-unplug uuid=\$pbd; xe pbd-destroy uuid=\$
pbd; xe sr-forget uuid=\$sr; done"
ssh root@$1 "for sr in \`xe sr-list type=nfs | grep uuid | awk '{print \$5}'\`; do pbd=\`xe pbd-list sr-uuid=\$sr | grep ^uuid | awk '{ print \$5}'\` ; echo \$pbd; xe pbd-unplug uuid=\$pbd; xe pbd-destroy uuid=\$pbd; xe sr-forget uuid=\$
sr; done"
ssh root@$1 "sr=\`xe sr-list type=lvm | grep uuid | awk '{print \$5}'\`; for vdi in \`xe vdi-list sr-uuid=\$sr | grep ^uuid | awk '{ print \$5}'\` ; do echo \$vdi; xe vdi-destroy uuid=\$vdi; done"
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
for i in $SERVER
do
  kill_server $i
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
  cleanup_solaris $i
done

#Cleanup management server
for i in $SERVER
do
  echo "Starting cleanup on $i";
  cleanup_server $i $LOGDIR
done