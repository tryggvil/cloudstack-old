#!/usr/bin/env bash
#
# iscsimon.sh
#
# Monitor iscsi connections for failures, and stop vm's if necessary
#

err=0
for sid in `iscsiadm -m session | awk '{print $2}' | tr -d '[]'`
do
  state=`iscsiadm -m session -r $sid -P 1 | grep "iSCSI Session State:" | awk '{print $NF}'`
  if [ $state == "FREE" -o $state == "FAILED" ]
  then
    echo "DOWN" $sid `iscsiadm -m session -r $sid -P 1 | grep Target | awk '{print $2}'` `iscsiadm -m session -r $sid -P 1 | grep "Current Portal" | awk '{print $3}'`
    err=1
  fi
done

if [ $err -eq 0 ] 
then
  echo "OK"
fi
