#/bin/bash

#set -x

mntpath() {
  local vmname=$1
  if [ ! -d /mnt/$vmname ]
  then
    mkdir -p /mnt/$vmname
  fi
  echo "/mnt/$vmname"
}

NBD=kvm-nbd
mount_local() {
   local vmname=$1
   local disk=$2
   local path=$(mntpath $vmname)

   lsmod | grep nbd &> /dev/null
   if [ $? -ne 0 ]
   then
        modprobe nbd max_part=8 &> /dev/null 
        if [ $? -ne 0 ]
        then
            printf "No nbd module installed, failed to mount qcow2 image\n"
            return 1
        fi
    fi
    
    $NBD -c /dev/nbd0 $disk &> /dev/null
    if [ $? -ne 0 ]
    then
        printf "failed to create /dev/nbd0\n"   
        return 2
    fi

    mkdir -p ${path}
    retry=5
    while [ $retry -gt 0 ]
    do
        sleep 10
        mount -o sync /dev/nbd0p1 ${path}  &> /dev/null
        if [ $? -eq 0 ]
        then
            break
        fi
        retry=$(($retry-1))
    done
        

    if [ $retry -eq 0 ]
    then
        $NBD -d /dev/nbd0p1 &> /dev/null
        sleep 2
        $NBD -d /dev/nbd0 &> /dev/null
        printf "Faild to mount qcow2 image\n"
        return 3
    fi
    return $?
}

umount_local() {
    local vmname=$1
    local path=$(mntpath $vmname)

    umount  $path
    $NBD -d /dev/nbd0p1
    sleep 2
    $NBD -d /dev/nbd0
    local ret=$?

    rm -rf $path
    return $ret
}

mount_raw_disk() {
    local vmname=$1
    local datadisk=$2
    local path=$(mntpath $vmname)
    if [ ! -f $datadisk ]
    then
        printf "$datadisk doesn't exist" >&2
        return 2
    fi
    
    losetup -f $datadisk &>/dev/null

    if [ $? -gt 0 ]
    then
        printf "Failed to losetup $datadisk" 
        return 2
    fi

    local loopdev=$(losetup -j $datadisk 2>/dev/null|cut -d: -f 1)
    
    mount $loopdev $path  &>/dev/null
    if [ $? -gt 0 ]
    then
        printf "Failed to mount $datadisk to $path" >&2
        losetup -d $loopdev &>/dev/null
        exit 2
    fi 
    return 0
}

umount_raw_disk() {
    local vmname=$1
    local datadisk=$2
    local path=$(mntpath $vmname)
    local loopdev=$(losetup -j $datadisk 2>/dev/null|cut -d: -f 1 2> /dev/null)
    if [ "$loopdev" = "" ]
    then
       return 2 
    fi
    
    umount $loopdev &>/dev/null
    retry=10
    while [ $retry -gt 0 ]
    do
        losetup -d $loopdev &>/dev/null
        if [  $? -gt 0 ]
        then
            sleep 5
        else
            break;
        fi
        retry=$(($retry-1))
    done
    return $?
}

get_kernel() {
    local vmname=$1
    local rootdisk=$2
    local path=$(mntpath $vmname)
    local rootdiskFoder=`echo $rootdisk|sed 's/rootdisk//'`
    
    if [ ! -f $rootdiskFoder/vmops-domr-kernel ]
    then
        cp $path/boot/vmlinuz* $rootdiskFoder/vmops-domr-kernel -f
    fi
    if [ ! -f $rootdiskFoder/vmops-domr-initramfs ]
    then
        cp $path/boot/initramfs* $rootdiskFoder/vmops-domr-initramfs -f
    fi
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
   if [ ! -d ${path}/usr/local/vmops/consoleproxy ]
    then
        mkdir -p ${path}/usr/local/vmops/consoleproxy
    fi
   local md5file=${path}/usr/local/vmops/consoleproxy/md5sum

   [ -f ${md5file} ] && oldmd5=$(cat ${md5file})
   local newmd5=$(md5sum $patchfile | awk '{print $1}')

   if [ "$oldmd5" != "$newmd5" ]
   then
     echo "All" | unzip $patchfile -d ${path}/usr/local/vmops/consoleproxy >/dev/null 2>&1
     chmod 555 ${path}/usr/local/vmops/consoleproxy/run.sh
     echo ${newmd5} > ${md5file}
   fi

   return 0
}

patch_all() {
    local vmname=$1
    local domrpatch=$2
    local domppatch=$3
    local cmdline=$4
    local datadisk=$5
    local path=$(mntpath $vmname)
    alias cp='cp'

    cp -f $domrpatch $path/
    cp -f $domppatch $path/ 
    echo $cmdline > $path/cmdline 
    sed -i "s/,/\ /g" $path/cmdline
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
   chroot ${path} /sbin/chkconfig seteth1 on

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
   chroot ${path} /sbin/chkconfig seteth1 on
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
  p)    pflag=1
        cmdline="$OPTARG"
        ;;
  *)    ;;
  esac
done

if [ "$lflag$tflag$dflag" != "111" ]
then
  printf "Error: No enough parameter\n" >&2
  exit 1
fi

if [ "$vmtype" = "all" ]
then
    mount_raw_disk $vmname $rootdisk
    if [ $? -gt 0 ]
    then
        printf "Failed to mount $rootdisk"
        exit $?
    fi
    patch_all $vmname $(dirname $0)/patch.tgz $(dirname $0)/console-proxy.zip $cmdline $rootdisk
    umount_raw_disk $vmname $rootdisk    
    exit $?
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

get_kernel $vmname $rootdisk

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
