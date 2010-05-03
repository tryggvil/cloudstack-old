#!/bin/bash

set -x

if [ "$1" == "" ]
then
  echo "Usage: $(basename $0) <instance>"
  exit 3
fi

instance=$1

for i in $(virsh list | grep $instance | awk '{print $2}' );
do
  files=$(virsh dumpxml $i | grep file | grep source | awk '{print $2}' | awk -F"=" '{print $2}'  );
  dir=` virsh dumpxml $i | grep file | grep source | awk '{print $2}' | awk -F"=" '{print $2}' | awk -F"/vm/" '{print $1"/vm/*"}' | awk -F"'" '{print $2}' |tail -1`;
echo "dir is $dir"
  echo "Destroying VM: $i"
  virsh destroy $i
  virsh undefine $i
  sleep 2
done
  echo "Destroying vms on storage..."
  echo "dir is $dir"
  rm -rf $dir