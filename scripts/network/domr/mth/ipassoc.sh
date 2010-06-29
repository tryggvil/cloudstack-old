#!/usr/bin/env bash
# $Id: ipassoc.sh 9132 2010-06-04 20:17:43Z manuel $ $HeadURL: svn://svn.lab.vmops.com/repos/branches/2.0.0/java/scripts/network/domr/mth/ipassoc.sh $
# ipassoc.sh -- associate/disassociate a public ip with an instance
#
#
usage() {
  printf "Usage:\n %s -A  -i <domR eth1 ip>  -l <public-ip-address>  -r <domr name> [-f] \n" $(basename $0) >&2
  printf " %s -D -i <domR eth1 ip> -l <public-ip-address> -r <domr name> [-f] \n" $(basename $0) >&2
}


cert="$(dirname $0)/../id_rsa"

#verify if supplied ip is indeed in the public domain
check_public_ip() {
 if [[ $(expr match $1 "10.") -gt 0 ]] 
  then
    echo "Public IP ($1) cannot be a private IP address!\n"
    exit 1
  fi
}

#ensure that dom0 is set up to do routing and proxy arp
check_ip_fw () {
  if [ $(cat /proc/sys/net/ipv4/ip_forward) != 1 ];
  then
    printf "Warning. Dom0 not set up to do forwarding.\n" >&2
    printf "Executing: echo 1 > /proc/sys/net/ipv4/ip_forward\n" >&2
    printf "To make this permanent, set net.ipv4.ip_forward = 1 in /etc/sysctl.conf\n" >&2
    echo 1 > /proc/sys/net/ipv4/ip_forward
  fi
  #if [ $(cat /proc/sys/net/ipv4/conf/eth0/proxy_arp) != 1 ];
  #then
    #printf "Warning. Dom0 not set up to do proxy ARP.\n"
    #printf "Executing: echo 1 > /proc/sys/net/ipv4/conf/eth0/proxy_arp\n"
    #printf "To make this permanent, set net.ipv4.conf.eth0.proxy_arp = 1 in /etc/sysctl.conf\n"
    #echo 1 > /proc/sys/net/ipv4/conf/eth0/proxy_arp
  #fi
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

#Add the NAT entries into iptables in the routing domain
add_nat_entry() {
  local dRIp=$1
  local pubIp=$2
   ssh -p 3922 -o StrictHostKeyChecking=no -i $cert root@$dRIp "\
      ip addr add dev eth2 $pubIp
      iptables -t nat -I POSTROUTING   -j SNAT -o eth2 --to-source $pubIp ;
      /sbin/arping -c 3 -I eth2 -A -U -s $pubIp $pubIp;
     "
  if [ $? -gt 0  -a $? -ne 2 ]
  then
     return 1
  fi

  return 0
}

#remove the NAT entries into iptables in the routing domain
del_nat_entry() {
  local dRIp=$1
  local pubIp=$2
   ssh -p 3922 -o StrictHostKeyChecking=no -i $cert root@$dRIp "\
      iptables -t nat -D POSTROUTING   -j SNAT -o eth2 --to-source $pubIp;
      ip addr del dev eth2 $pubIp/32
     "
 
  if [ $? -gt 0  -a $? -ne 2 ]
  then
     return 1
  fi

  return $?
}

_vifname() {
 local vmname=$1
 local domid=$(xm domid $vmname)
 echo vif${domid}
}

add_acct_rule() {
  local vmname=$1
  local intf=$2
  local dstip=$3

  local vifname=$(_vifname $vmname)

  iptables -A FORWARD -m physdev  --physdev-out $vifname.$intf -d $dstip -j ACCEPT

  return $?
}

remove_acct_rule() {
  local vmname=$1
  local intf=$2
  local dstip=$3

  local vifname=$(_vifname $vmname)

  iptables -D FORWARD -m physdev  --physdev-out $vifname.$intf -d $dstip -j ACCEPT

  return $?
}

add_first_ip() {
  local domRname=$1
  local domRip=$2
  local publicIp=$3
  local eth2mac=$4
  local vifname=""
  local domid=0

  if  ! xm list $domRname  &>/dev/null
  then
    printf "Error: routing domain $domRname does not exist\n" >&2
    exit 2
  fi


  #check_public_ip "$publicIp"

    
  # Ensure that dom0 is set up to do routing
  #check_ip_fw
   
  
  #program ip tables in domR and route in dom0
  if  ! add_nat_entry $domRIp $publicIp 
  then
     printf  "Unable add nat entry on gateway, exiting\n" >&2
     return 4
  fi

  return 0
}

add_an_ip () {
  local dRIp=$1
  local pubIp=$2
   ssh -p 3922 -o StrictHostKeyChecking=no -i $cert root@$dRIp "\
      ip addr add dev eth2 $pubIp ;
      /sbin/arping -c 3 -I eth2 -A -U -s $pubIp $pubIp;
     "
   return $?
}

remove_an_ip () {
  local dRIp=$1
  local pubIp=$2
   ssh -p 3922 -o StrictHostKeyChecking=no -i $cert root@$dRIp "\
      ip addr del dev eth2 $pubIp/32
     "
  if [ $? -gt 0  -a $? -ne 2 ]
  then
     return 1
  fi
}

#set -x

rflag=
iflag=
lflag=
aflag=
nflag=
fflag=
vflag=
gflag=
nflag=
op=""

while getopts 'fADr:i:a:l:v:g:n:' OPTION
do
  case $OPTION in
  A)	Aflag=1
		op="-A"
		;;
  D)	Dflag=1
		op="-D"
		;;
  f)	fflag=1
		;;
  r)	rflag=1
		domRname="$OPTARG"
		;;
  i)	iflag=1
		domRIp="$OPTARG"
		;;
  l)	lflag=1
		publicIp="$OPTARG"
		;;
  a)	aflag=1
		eth2mac="$OPTARG"
		;;
  v)	vflag=1
  		vlanId="$OPTARG"
  		;;
  g)	gflag=1
  		gateway="$OPTARG"
  		;;
  n)	nflag=1
  		netmask="$OPTARG"
  		;;
  ?)	usage
		exit 2
		;;
  esac
done

#Either the A flag or the D flag but not both
if [ "$Aflag$Dflag" != "1" ]
then
 usage
 exit 2
fi

if [ "$Aflag$lflag$iflag$rflag" != "1111" ] && [ "$Dflag$lflag$iflag$rflag" != "1111" ]
then
   exit 2
fi

# check if gateway domain is up and running
if ! check_gw "$domRIp"
then
   printf "Unable to ping the routing domain, exiting\n" >&2
   exit 3
fi


if [ "$fflag" == "1" ] && [ "$Aflag" == "1" ]
then
  add_acct_rule $domRname 2 $publicIp
  add_nat_entry $domRIp $publicIp 
  exit $?
fi

if [ "$Aflag" == "1" ]
then  
  add_acct_rule $domRname 2 $publicIp
  add_an_ip $domRIp $publicIp 
  exit $?
fi

if [ "$fflag" == "1" ] && [ "$Dflag" == "1" ]
then
  remove_acct_rule $domRname 2 $publicIp
  del_nat_entry $domRIp $publicIp 
  exit $?
fi

if [ "$Dflag" == "1" ]
then
  remove_acct_rule $domRname 2 $publicIp
  remove_an_ip $domRIp $publicIp 
  exit $?
fi

exit 0

