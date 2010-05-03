#!/usr/bin/env bash
# get_iqn.sh -- return iSCSI iqn of initiator (Linux) or target (OpenSolaris)

usage() {
  printf "Usage:  %s \n" $(basename $0) >&2
}

linux() {
  uname -a | grep "Linux" > /dev/null
  return $?
}

opensolaris() {
  uname -a | grep "SunOS" > /dev/null
  return $?
}

hosted() {
  uname -a | grep "101b" > /dev/null
  return $?
}

if [ $# -ne 0 ]
then
  usage
  exit 1
fi

if linux
then
  initiator_iqn=$(cat /etc/iscsi/initiatorname.iscsi | cut -d'=' -f2)
  printf "%s\n" $initiator_iqn
  exit 0
fi

if opensolaris && hosted
then
  printf "unique_iqn_per_zvol\n"
  exit 0
fi

if opensolaris
then
  tgt_iqn=$(itadm list-target | tail -1 | awk '{print $1}')
  printf "%s\n" $tgt_iqn
  exit 0
fi

printf "Unexpected operating system!\n" >&2
exit 2