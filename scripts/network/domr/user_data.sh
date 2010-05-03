#!/bin/bash
# user_data.sh -- add dhcp entry on domr
#

usage() {
  printf "Usage: %s: -r <domr-ip> -d <user data file encoded in base64> -v <vm ip> -n <vm name>\n" $(basename $0) >&2
  exit 2
}

cert="$(dirname $0)/id_rsa"
port=3922

add_user_data() {
  local domr=$1
  local datafile=$2
  local ip=$3
  local vm=$4
  ssh -p $port -o StrictHostKeyChecking=no -i $cert root@$domr "mkdir -p /var/www/html/userdata/$ip/" >/dev/null
  local result=$?
  if [ $result -eq 0 ]
  then
    scp -P $port -o StrictHostKeyChecking=no -i $cert $datafile root@$domr:/var/www/html/userdata/$ip/user-data >/dev/null
    result=$?
  fi
  return $result
}

domrIp=
userData=
vmIp=
vmName=

while getopts 'r:d:v:n:' OPTION
do
  case $OPTION in
  r)	domrIp="$OPTARG"
		;;
  v)	vmIp="$OPTARG"
		;;
  d)	dataFile="$OPTARG"
		;;
  n)	vmName="$OPTARG"
		;;
  ?)    usage
		exit 1
		;;
  esac
done

[ "$domrIp" == "" ] || [ "$vmIp" == "" ] || [ "$dataFile" == "" ] || [ "$vmName" == "" ] && usage 

add_user_data $domrIp $dataFile $vmIp $vmName

exit $?
