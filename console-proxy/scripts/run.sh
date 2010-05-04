#!/usr/bin/env bash
#run.sh runs the console proxy.

# make sure we delete the old files from the original template 
rm console-proxy.jar
rm console-common.jar
rm conf/vmops.properties

set -x

CP=./:./conf
for file in *.jar
do
  CP=${CP}:$file
done
keyvalues=
if [ -f /mnt/cmdline ]
then
    CMDLINE=$(cat /mnt/cmdline)
else
    CMDLINE=$(cat /proc/cmdline)
fi
#CMDLINE="graphical utf8 eth0ip=0.0.0.0 eth0mask=255.255.255.0 eth1ip=192.168.140.40 eth1mask=255.255.255.0 eth2ip=172.24.0.50 eth2mask=255.255.0.0 gateway=172.24.0.1 dns1=72.52.126.11 template=domP dns2=72.52.126.12 host=192.168.1.142 port=8250 mgmtcidr=192.168.1.0/24 localgw=192.168.140.1 zone=5 pod=5"
for i in $CMDLINE
  do
     KEY=$(echo $i | cut -s -d= -f1)
     VALUE=$(echo $i | cut -s -d= -f2)
     [ "$KEY" == "" ] && continue
     case $KEY in
        *)
          keyvalues="${keyvalues} $KEY=$VALUE"
     esac
  done
   
java -mx700m -cp $CP com.vmops.agent.AgentShell $keyvalues $@
