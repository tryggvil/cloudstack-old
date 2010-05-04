#/bin/bash

#set -x

mntpath() {
  local vmname=$1
  echo "/mnt/$vmname"
}

mount_local() {
   local vmname=$1
   local disk=$2
   local path=$(mntpath $vmname)

   mkdir -p ${path}
   mount $disk ${path} 

   return $?
}

umount_local() {
   local vmname=$1
   local path=$(mntpath $vmname)

   umount  $path
   local ret=$?
   
   rm -rf $path
   return $ret
}


patch() {
   local vmname=$1
   local patchfile=$2
   local path=$(mntpath $vmname)

   local oldmd5=
   local md5file=${path}/md5sum
   [ -f ${md5file} ] && oldmd5=$(cat ${md5file})
   local newmd5=$(md5sum $patchfile | awk '{print $1}')

   if [ "$oldmd5" != "$newmd5" ]
   then
     tar xzf $patchfile -C ${path}
     echo ${newmd5} > ${md5file}
   fi

   return 0
}

#
# To use existing console proxy .zip-based package file
#
patch_console_proxy() {
   local vmname=$1
   local patchfile=$2
   local path=$(mntpath $vmname)
   local oldmd5=
   local md5file=${path}/usr/local/vmops/consoleproxy/md5sum

   [ -f ${md5file} ] && oldmd5=$(cat ${md5file})
   local newmd5=$(md5sum $patchfile | awk '{print $1}')

   if [ "$oldmd5" != "$newmd5" ]
   then
     echo "All" | unzip $patchfile -d ${path}/usr/local/vmops/consoleproxy >/dev/null 2>&1
     chmod 555 ${path}/usr/local/vmops/consoleproxy/run.sh
     find ${path}/usr/local/vmops/consoleproxy/ -name \*.sh | xargs chmod 555
     echo ${newmd5} > ${md5file}
   fi

   return 0
}

consoleproxy_svcs() {
   local vmname=$1
   local path=$(mntpath $vmname)

   chroot ${path} /sbin/chkconfig vmops on
   chroot ${path} /sbin/chkconfig domr_webserver off
   chroot ${path} /sbin/chkconfig haproxy off ;
   chroot ${path} /sbin/chkconfig dnsmasq off
   chroot ${path} /sbin/chkconfig sshd off
   chroot ${path} /sbin/chkconfig httpd off

   cp ${path}/etc/sysconfig/iptables-domp ${path}/etc/sysconfig/iptables
}

routing_svcs() {
   local vmname=$1
   local path=$(mntpath $vmname)

   chroot ${path} /sbin/chkconfig vmops off
   chroot ${path} /sbin/chkconfig domr_webserver on ; 
   chroot ${path} /sbin/chkconfig haproxy on ; 
   chroot ${path} /sbin/chkconfig dnsmasq on
   chroot ${path} /sbin/chkconfig sshd on
   cp ${path}/etc/sysconfig/iptables-domr ${path}/etc/sysconfig/iptables
}

lflag=
dflag=

while getopts 't:v:i:m:e:E:a:A:g:l:n:d:b:B:p:I:N:Mx:X:' OPTION
do
  case $OPTION in
  l)	lflag=1
	vmname="$OPTARG"
        ;;
  t)    tflag=1
        vmtype="$OPTARG"
        ;;
  d)    dflag=1
        rootdisk="$OPTARG"
        ;;
  *)    ;;
  esac
done

if [ "$lflag$tflag$dflag" != "111" ]
then
  printf "Error: No enough parameter\n" >&2
  exit 1
fi


mount_local $vmname $rootdisk

if [ $? -gt 0 ]
then
  printf "Failed to mount disk $rootdisk for $vmname\n" >&2
  exit 1
fi

if [ -f $(dirname $0)/patch.tgz ]
then
  patch $vmname $(dirname $0)/patch.tgz
  if [ $? -gt 0 ]
  then
    printf "Failed to apply patch patch.zip to $vmname\n" >&2
    umount_local $vmname
    exit 4
  fi
fi

if [ "$vmtype" = "domp" ]  && [ -f $(dirname $0)/console-proxy.zip ]
then
  patch_console_proxy $vmname $(dirname $0)/console-proxy.zip
  if [ $? -gt 0 ]
  then
    printf "Failed to apply patch console-proxy.zip to $vmname\n" >&2
    umount_local $vmname
    exit 5
  fi
fi

# domr is 64 bit, need to copy 32bit chkconfig to domr
# this is workaroud, will use 32 bit domr
dompath=$(mntpath $vmname)
cp /sbin/chkconfig $dompath/sbin

if [ "$vmtype" = "domr" ]
then
  routing_svcs $vmname
  if [ $? -gt 0 ]
  then
    printf "Failed to execute routing_svcs\n" >&2
    umount_local $vmname
    exit 6
  fi
fi


if [ "$vmtype" = "domp" ]
then
  consoleproxy_svcs $vmname
  if [ $? -gt 0 ]
  then
    printf "Failed to execute consoleproxy_svcs\n" >&2
    umount_local $vmname
    exit 7
  fi
fi



umount_local $vmname

exit $?
