#!/usr/bin/env bash
#run.sh runs the console proxy.

# make sure we delete the old files from the original template 
rm console-proxy.jar
rm console-common.jar
rm conf/vmops.properties

CP=./:./conf
for file in *.jar
do
  CP=${CP}:$file
done

CMDLINE=$(cat /proc/cmdline)
for i in $CMDLINE
  do
     KEY=$(echo $i | cut -d= -f1)
     VALUE=$(echo $i | cut -d= -f2)
     case $KEY in
       host)
          MGMT_HOST=$VALUE
          ;;
       port)
          MGMT_PORT=$VALUE
          ;;
       zone)
          MGMT_ZONE=$VALUE
          ;;
       pod)
          MGMT_POD=$VALUE
          ;;
       guid)
          GUID=$VALUE
          ;;
       proxy_vm)
          PROXY_VM=$VALUE
          ;;
     esac
  done
   
java -mx700m -cp $CP com.vmops.agent.AgentShell host=$MGMT_HOST port=$MGMT_PORT zone=$MGMT_ZONE pod=$MGMT_POD guid=$GUID proxy_vm=$PROXY_VM $@
