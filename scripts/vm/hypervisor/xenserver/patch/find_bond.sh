#!/bin/sh
# $Id: find_bond.sh 9132 2010-06-04 20:17:43Z manuel $ $HeadURL: svn://svn.lab.vmops.com/repos/branches/2.0.0/java/scripts/vm/hypervisor/xenserver/patch/find_bond.sh $

#set -x
 

cleanup_vlan() {
  for vlan in `xe vlan-list | grep uuid | awk '{print $NF}'`; do 
    untagged=$(xe vlan-param-list uuid=$vlan | grep untagged | awk '{print $NF}')
    network=$(xe pif-param-get param-name=network-uuid uuid=$untagged)
    xe vlan-destroy uuid=$vlan
    xe network-destroy uuid=$network
  done
}

usage() {
  echo "$0 device"
  exit 1
}

sflag=
dflag=

while getopts 'sd' OPTION
do
  case $OPTION in
  d)    dflag=1
         ;;
  s)    sflag=1
         ;;
  ?)    usage
         exit 1
         ;;
  esac
done

shift $(($OPTIND - 1))
nic=$1

[ -z "$nic" ] && usage

addr=$(ip addr | grep $nic | grep inet | awk '{print $2}')
addr=${addr%/*}
bridges=$(brctl show | grep -v bridge | awk '{print $1}')

host_uuid=$(xe host-list | grep -B 1 $(hostname) | grep uuid | awk '{print $NF}')
if [ -z "$host_uuid" ]; then
  printf "Unable to find host uuid using $(hostname)\n" >&2
  exit 2
fi


if [ -z "$addr" ]; then
  printf "Unable to find an ip address for $nic\n" >&2
  exit 3
fi

current=$(brctl show | grep xenbr0 | awk '{print $NF}')
for dev in `ip addr | grep mtu | grep -v -E "\.[0-9]*@|lo|$nic|$current" | awk '{print $2}'`
do
  dev=${dev%:}
  echo $bridges | grep $dev >/dev/null 2>&1
  rc=$?
  if [ $rc -ne 0 ]
  then
    ifconfig eth1 | grep UP >/dev/null 2>&1
    rc=$?
    if [ $rc -eq 1 ]; then
      ifconfig $dev up
      sleep 4
    fi
    arping -q -c 1 -w 2 -D -I $dev $addr >/dev/null 2>&1
    rc=$?
    if [ $rc -ne 1 ]; then
      continue;
    fi

    pif_uuid=$(xe pif-list device=$dev host-uuid=$host_uuid | grep -B 3 "( RO): -1" | grep uuid | awk '{print $NF}')
    if [ -z $pif_uuid ]; then
      mac=$(ifconfig $dev | grep HWaddr | awk '{print $NF}')
      pif_uuid=$(xe pif-introduce host-uuid=$host_uuid device=$dev mac=$mac)
    fi

    if [ -z $pif_uuid ]; then
      continue;
    fi

    bridge=$(xe network-list PIF-uuids=$pif_uuid | grep bridge | awk '{print $NF}')
    if [ -z $bridge ]; then
      continue;
    fi

    echo ">>>$dev<<<"
    exit 0    
  fi
done
exit 4
