#!/usr/bin/env bash
# vnetcleanup.sh -- clean up vnet artifacts (mainly bridges)
#

usage() {
  printf "Usage: %s: <-a|-v vnet>\n" $(basename $0) >&2
}


#set -x
delete_all_vnbrs() {
  for brs in `brctl show | grep vnbr`;  
  do  
    case $brs in  vnbr*)     
       ifconfig $brs down; 
       brctl delbr $brs;      
       iptables -D FORWARD -i $brs -o $brs -j ACCEPT
       ;;   
     *)     
       ;; 
    esac;
  done
}

vflag=
aflag=
vnetid=""

while getopts 'v:a' OPTION
do
  case $OPTION in
  v)	vflag=1
		vnetid="$OPTARG"
		;;
  a)	aflag=1
		;;
  ?)	usage
		exit 2
		;;
  esac
done

#either all or only 1
if [ "$aflag$vflag" != "1" ]
then
 usage
 exit 2
fi

if [ "$aflag" == "1" ]
then
 for i in `/usr/local/vmops/agent/scripts/vnet/vn vnets -a | grep id  | cut -d' ' -f6|cut -d')' -f1`
 do 
   vn vnet-delete -b $i &> /dev/null
 done
 delete_all_vnbrs
 exit 0
fi

if [ "$vflag" == "1" ]
then
  bridgeid="vnbr""$vnetid"
  longvnetid="0000:0000:0000:0000:0000:0000:0000:""$vnetid"
  if ! vn vnet-delete -b $longvnetid &> /dev/null
  then
    ifconfig $bridgeid down; 
    brctl delbr $bridgeid;      
  fi
  iptables -D FORWARD -i $bridgeid -o $bridgeid -j ACCEPT
 exit 0
fi

exit 0
