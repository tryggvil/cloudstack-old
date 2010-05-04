#!/usr/bin/env bash
# networkUsage.sh -- create iptable rules to gather network stats
#
#
usage() {
  printf "Usage: %s -[c|g|r] -i <domR eth1 ip> \n" $(basename $0)  >&2
}

# check if gateway domain is up and running
check_gw() {
  ping -c 1 -n -q $1 > /dev/null
  if [ $? -gt 0 ]
  then
    sleep 1
    ping -c 1 -n -q $1 > /dev/null
  fi
  return $?;
}

cert="$(dirname $0)/id_rsa"

create_usage_rules () {
  local dRIp=$1
   ssh -p 3922 -o StrictHostKeyChecking=no -i $cert root@$dRIp "\
     iptables -N NETWORK_STATS > /dev/null;
     iptables -I FORWARD -j NETWORK_STATS > /dev/null;
     iptables -A NETWORK_STATS -i eth0 -o eth2 > /dev/null;
     iptables -A NETWORK_STATS -i eth2 -o eth0 > /dev/null;
     "
     return 1
}

get_usage () {
  local dRIp=$1
   ssh -p 3922 -o StrictHostKeyChecking=no -i $cert root@$dRIp "\
     iptables -L NETWORK_STATS -n -v -x | awk '\$1 ~ /^[0-9]+\$/ { printf \"%s:\", \$2}'
     "
  if [ $? -gt 0  -a $? -ne 2 ]
  then
     printf $?
     return 1
  fi
}

reset_usage () {
  local dRIp=$1
   ssh -p 3922 -o StrictHostKeyChecking=no -i $cert root@$dRIp "\
     iptables -Z NETWORK_STATS > /dev/null;
     "
  if [ $? -gt 0  -a $? -ne 2 ]
  then
     return 1
  fi
}

#set -x

cflag=
gflag=
rflag=
iflag=

while getopts 'cgri:' OPTION
do
  case $OPTION in
  c)	cflag=1
		;;
  g)	gflag=1
		;;
  r)	rflag=1
		;;
  i)	iflag=1
		domRIp="$OPTARG"
		;;
  ?)	usage
		exit 2
		;;
  esac
done

# check if gateway domain is up and running
if ! check_gw "$domRIp"
then
   printf "Unable to ping the routing domain, exiting\n" >&2
   exit 3
fi


if [ "$cflag" == "1" ] 
then
  create_usage_rules $domRIp  
  exit $?
fi

if [ "$gflag" == "1" ] 
then
  get_usage $domRIp  
  exit $?
fi

if [ "$rflag" == "1" ] 
then
  reset_usage $domRIp  
  exit $?
fi

exit 0

