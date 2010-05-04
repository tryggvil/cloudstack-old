#!/bin/bash

. /etc/rc.d/init.d/functions

set -x

usage() {
  printf "Usage: %s: [-b <dist dir>] [ -m <mgmt-ip-address> ] [-d] [-k] [-r] [-s] \n" $(basename $0) >&2
}

get_my_ip () {
  local dev=eth0
 ifconfig $dev | awk '/inet addr/ {split ($2,A,":"); print A[2]}'
}

deploy_linux() {
  ssh root@$1 "mkdir -p $AGENTDIR; cd $AGENTDIR; rm -rf *"
  scp -q $5/deploy-agent.sh root@$1:$AGENTDIR
  echo "Started copying agent from $5 to $1 $AGENTDIR"
  scp -q $5/agent.zip root@$1:$AGENTDIR
  if [ $? -gt 0 ]; then echo "failed to copy to $1"; return 2; fi
  echo "Parameters are $4 $3 and $6"
  ssh root@$1 "cd $AGENTDIR; ./deploy-agent.sh  -d $AGENTDIR -t $4 -z $AGENTDIR/agent.zip -h $3 -m $6"
  ssh root@$1 "/bin/rm -f /usr/local/vmops/agent/conf/agent.properties"
  if [ $? -gt 0 ]; then echo "failed to install on $1"; return 2; fi
}

deploy_solaris() {
  ssh root@$1 "mkdir -p $AGENTDIR; cd $AGENTDIR; rm -rf *"
  scp -q $5/deploy-agent.sh root@$1:$AGENTDIR
  scp -q $5/agent.zip root@$1:$AGENTDIR
  if [ $? -gt 0 ]; then echo "failed to copy to $1"; return 2; fi
  ssh root@$1 "cd $AGENTDIR; ./deploy-agent.sh  -d $AGENTDIR -t storage -z $AGENTDIR/agent.zip -h $3 -m $6" > /dev/null
  ssh root@$1 "/bin/rm -f /usr/local/vmops/agent/conf/agent.properties"
  echo "Storage deployed successfully"
  if [ $? -gt 0 ]; then echo "failed to install on $1"; return 2; fi
}

deploy_server() {
echo "copy from $4 to $1 directory $MANAGEMENTDIR"
  ssh root@$1 rm -rf $MANAGEMENTDIR/'*' $DBDIR/'*'

  scp -r ../templates.sql ../server-setup.xml $4/vmops*rpm $4/daemonize*x86_64*rpm root@$1:$MANAGEMENTDIR
  if [ $? -gt 0 ]; then echo "failed to copy to $1"; return 2; fi
  echo "Deploying management server"
  ssh root@$1 "cd $MANAGEMENTDIR; yum remove -y cloud-management cloud-usage cloud-update-agent daemonize daemonize-debuginfo ;rm -rf /usr/local/tomcat/*;  rpm -ivh --replacefiles --replacepkgs cloud-management* cloud-usage* cloud-agent-update* daemonize-*"
  scp -r ../db.properties  root@$1:$SERVERDIR/conf/
  ipaddress=$(ssh root@$1 "ifconfig  | grep 'inet addr:'| grep -v '127.0.0.1' | cut -d: -f2 | awk {'print \$1'};")
  ssh root@$1 "cd $SERVERDIR/conf; sed -e 's/cluster.node.IP=.*$/cluster.node.IP=$ipaddress/g' db.properties > db.properties1; dos2unix db.properties1; mv -f db.properties1 db.properties"
  ssh root@$1 "cd $SERVERDIR/conf; sed -e 's/db.vmops.host=.*$/db.vmops.host=$DB/g' db.properties > db.properties1; dos2unix db.properties1; mv -f db.properties1 db.properties"
  ssh root@$1 "cd $SERVERDIR/conf; sed -e 's/db.usage.host=.*$/db.usage.host=$DB/g' db.properties > db.properties1; dos2unix db.properties1; mv -f db.properties1 db.properties"
  ssh root@$1 "line=\`grep -n 'export JAVA_OPTS' /etc/init.d/cloud-management | tail -1 | cut -d : -f 1\`; line=\`expr \$line - 1\`; awk '{print} NR == '\$line' {print \"JAVA_OPTS=\\\"\$JAVA_OPTS -Xms256M -Xmx512M -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=n -ea -Dcom.sun.management.jmxremote.port=8788 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false\\\"\"}' /etc/init.d/cloud-management > /etc/init.d/cloud-management1; mv -f /etc/init.d/cloud-management1 /etc/init.d/cloud-management; chmod +x /etc/init.d/cloud-management"
  if [ $? -gt 0 ]; then echo "failed to install on $1"; return 2; fi
 echo "Management server is deployed successfully"
}


deploy_db() {
  echo "Deploying database on $1"
  ssh root@$1 "cd $DBDIR; ./deploy-db-dev.sh $MANAGEMENTDIR/server-setup.xml $MANAGEMENTDIR/templates.sql"
  if [ $? -gt 0 ]; then echo "failed to deploy db on $1"; return 2; fi
  echo "Database is deployed successfully"
}



kill_agent_linux() {
   ssh root@$1 "service vmops stop"
}

kill_agent_solaris() {
   ssh root@$1 "svcadm disable vmops"
}


kill_server() {
  pids=$(ssh root@$1 "for i in \$(ps -ef | grep java | grep ClassLoaderLogManager | grep -v grep | awk '{print \$2}'); do pwdx \$i  | grep $2 ;done"  | cut -d: -f1)
 for p in $pids
 do
   ssh root@$1 "kill -9 $p"
   echo "Management server is killed"
 done
}


run_agent_linux() {
  local mgmtip=$4
  kill_agent_linux $1 $2
  if [ $? -gt 0 ]; then  failure ; else success;fi
  echo
  echo -n "Running: "
  ssh root@$1 "service vmops start"
  if [ $? -gt 0 ]; then  failure ; else success;fi
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
  ssh root@$1 "service cloud-management start; service cloud-usage start 2>&1 &"
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
  if [ "$developer" == "" ]
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
fi


if [ "$killflag" == "1" ]
then
  for i in $COMPUTE $ROUTER
  do
   echo -n "killing $i"
   kill_agent_linux $i $USER
   if [ $? -gt 0 ]; then  failure ; else success;fi
   echo
  done
  if [ "$developer" == "" ]
  then
    for i in $SERVER
    do
    kill_server $i $SERVERDIR/bin
    done
  fi



  for i in $STORAGE
  do
   echo -n "killing $i"
   kill_agent_solaris $i $USER
   if [ $? -gt 0 ]; then  failure ; else success;fi
   echo
  done
  if [ "$developer" == "" ]
  then
    for i in $SERVER
    do
    kill_server $i $SERVERDIR/bin
    done
  fi

fi


if [ "$runflag" == "1" ]
then
  echo "Running agents on comupting and routing hosts...."
  for i in $COMPUTE $ROUTER
  do
   echo -n "$i: "
   run_agent_linux $i $USER $DEBUG $mgmtIp
   if [ $? -gt 0 ]; then  failure ; else success;fi
   echo
  done


  echo "Running agents on storage hosts...."
  for i in $STORAGE
  do
   echo -n "$i: "
   run_agent_solaris $i $USER $DEBUG $mgmtIp
   if [ $? -gt 0 ]; then  failure ; else success;fi
   echo
  done

  if [ "$developer" == "" ]
  then
    echo "Starting Management server"
      for i in $SERVER
      do
       run_server $i
      done
  fi
fi
