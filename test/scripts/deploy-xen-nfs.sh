#!/bin/bash

. /etc/rc.d/init.d/functions

set -x

usage() {
  printf "Usage: %s: [-d] [-r] \n" $(basename $0) >&2
}

deploy_server() {
  ssh root@$1 "yum remove -y \"vmops*\" && yum clean all && cd /usr/share/vmops/ && rm -rf * ; yum install -y vmops-client vmops-premium"
  if [ $? -gt 0 ]; then echo "failed to install on $1"; return 2; fi
 echo "Management server is deployed successfully"
}

deploy_db() {
  echo "Deploying database on $1"
  ssh root@$1 "cp /root/deploy/server-setup.xml /usr/share/vmops/management/setup/. ; cp /root/deploy/templates.sql /usr/share/vmops/management/setup/. ; cp /root/deploy/agent.properties /etc/vmops/agent/agent.properties"
ssh root@$1 "vmops-setup-databases user@$1 --deploy-as=root --auto=/usr/share/vmops/management/setup/server-setup.xml"
  if [ $? -gt 0 ]; then echo "failed to deploy db on $1"; return 2; fi
  echo "Database is deployed successfully"
}


kill_server() {
  pids=$(ssh root@$1 "for i in \$(ps -ef | grep java | grep vmops | grep -v grep | awk '{print \$2}'); do pwdx \$i | grep $2 ;done"  | cut -d: -f1)
 for p in $pids
 do
   ssh root@$1 "kill -9 $p"
   echo "Management server is killed"
 done
}

run_agent_solaris() {
  local mgmtip=$4
  kill_agent_solaris $1 $2
  echo -n "Running: "
  ssh root@$1 "svcadm enable vmops"
  if [ $? -gt 0 ]; then  failure ; else success;fi
}

run_server() {
  kill_server $1 $SERVERDIR/bin
  ssh root@$1 "service vmops-management start; service vmops-agent start; service vmops-usage start 2>&1 &"
  echo "server $1 is started under  $SERVERDIR/bin"
}

dir=$(dirname "$0")
if [ -f $dir/../deploy.properties ]; then
  . "$dir/../deploy.properties"
fi

if [ "$USER" == "" ]; then
  printf "ERROR: Need tospecify the user\n"
  exit 4
fi

if [ "$SERVER" == "" ]; then
  printf "ERROR: Need to specify the management server\n"
  exit 1
fi


deployflag=
runflag=
killflag=
setupflag=
mgmtIp=
mode=expert
distdir=dist

while getopts 'dkrsb:D' OPTION
do
  case $OPTION in
  d)    deployflag=1
        ;;
  k)    killflag=1
        ;;
  r)    runflag=1
        ;;
  b)    distdir="$OPTARG"
        ;;
  s)    setupflag=1
        mode=setup
        ;;
  D)    developer=1; distdir=dist/developer;;

  ?)    usage
  esac
done


if [ "$setupflag$deployflag$killflag$runflag" == "" ]
then
  usage
  exit
fi


if [ "$deployflag" == "1" ]
then
    echo "Deploying server...."

    for i in $SERVER
    do
      echo -n "$i: "
      deploy_server $i $USER server $distdir $mode
      if [ $? -gt 0 ]; then  failure ; else success;fi
      echo
    done

    for i in $DB
    do
      echo -n "$i: "
      deploy_server $i $USER server $distdir $mode
      if [ $? -gt 0 ]; then  failure ; else success;fi
      echo
    done

    echo "Deploying database..."
    for i in $DB
    do
      echo -n "$i: "
      deploy_server $i $USER server $distdir $mode
      deploy_db $i
      if [ $? -gt 0 ]; then  failure ; else success;fi
      echo
    done
fi


if [ "$runflag" == "1" ]
then
   echo "Starting Management server"
   for i in $SERVER
   do
                run_server $i
                echo "Sleeping for 30 seconds before connecting the hosts...."
            sleep 30
                for j in $COMPUTE
                do
                        echo -n "Connecting host $j: "
                        wget "http://$i:8096/?command=addHost&zoneId=1&podId=1&url=http://$j&username=root&password=password"
                done
        done
fi