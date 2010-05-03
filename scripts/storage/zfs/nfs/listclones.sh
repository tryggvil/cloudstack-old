#!/usr/bin/env bash
# listclones.sh -- list all cloned filesystems under a parent fs
#

usage() {
  printf "Usage: %s: -p <parent-fs> \n" $(basename $0) >&2
}


#set -x

pflag=

while getopts 'p:' OPTION
do
  case $OPTION in
  p)	pflag=1
		parentFs="$OPTARG"
		;;
  esac
done

if [ "$pflag" != "1" ]
then
 usage
 exit 2
fi


zfs list -H -r -o name,origin $parentFs | awk '$2 != "-" {print $1}' | grep -v datadisk
