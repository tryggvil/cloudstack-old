#!/usr/bin/env bash
# createvm.sh -- create a vm image 
#

usage() {
  echo "Usage (clone VM from template): createvm.sh -t <template dir>  -i <rootdisk dir> -f <datadisk folder> -n <datadisk name> -s <datadisk size in GB>\n\
Usage (create blank rootdisk): createvm.sh -i <rootdisk dir> -S <rootdisk size in GB> \n" 
}

check_params() {
  # Either this VM is being cloned from a template, or it is being booted from an ISO and the rootdisk size will be specified
  if [ "$tflag$Sflag" != "10" ] && [ "$tflag$Sflag" != "01" ]
  then
    return 1
  fi

  # A rootdisk folder must be specified
  if [ "$iflag" != "1" ]
  then
    return 1
  fi

  # If a datadisk folder is specified, then a datadisk size and a datadisk name must be specified
  if [ "$fflag" == "1" ]
  then
    if [ "$sflag$nflag" != "11" ]
    then
      return 1
    fi
  fi
  
  return 0
}

cleanup_and_exit_if_error() {
  local return_code=$1
  local msg=$2
  local rootdiskfolder=$3
  local datadiskfolder=$4
  local datadiskname=$5
  
  if [ $return_code -gt 0 ]
  then
    cleanup_disks $rootdiskfolder $datadiskfolder $datadiskname
    exit_if_error $return_code "$msg"
  fi
}

cleanup_disks() {
  local rootdiskfolder=$1
  local datadiskfolder=$2
  local datadiskname=$3

  local datadiskpath=""
  if [ "$datadiskfolder" != "" ] && [ "$datadiskname" != "" ]
  then
    datadiskpath="${datadiskfolder}/${datadiskname}"
  fi
  
  if [ "$rootdiskfolder" != "" ] && [ -d $rootdiskfolder ]
  then
    rm -rf $rootdiskfolder
  fi
  
  if [ "$datadiskpath" != "" ] && [ -f $datadiskpath ]
  then
    rm $datadiskpath
  fi
  
  return 0
}

exit_if_error() {
  local return_code=$1
  local msg=$2
  
  if [ $return_code -gt 0 ]
  then
    printf "${msg}\n"
    exit 1
  fi
}

make_folder() {
  local folder=$1
  
  if [ ! -d ${folder} ]
  then
    mkdir -p ${folder}
  fi
}

check_rootdisk() {
  local rootdiskfolder=$1

  make_folder $rootdiskfolder

  if [ -f ${rootdiskfolder}/rootdisk ]
  then
    return 1
  else
    return 0
  fi
}

check_datadisk() {
  local datadiskfolder=$1
  local datadiskname=$2
  
  make_folder $datadiskfolder
  
  if [ -f ${datadiskfolder}/${datadiskname} ]
  then
    return 1
  else
    return 0
  fi
}

strip_leading_slash() {
  local folder=$1
  
  if [ ${folder:0:1} != / ]
  then
    folder=/$folder
  fi
  
  echo $folder
}

clone_template_to_rootdisk() {
  local rootdiskfolder=$1
  local templatepath=$2

  curDir=$(pwd)
  cd $rootdiskfolder
  qemu-img create -f qcow2 -b $templatepath ${rootdiskfolder}/rootdisk	
  cd $curDir

  return $?
}

create_blank_rootdisk() {
  local rootdiskfolder=$1
  local rootdisksize=$2
  
  rootdisksize=$(convert_size_to_gb $rootdisksize)
  
  if [ $? -gt 0 ]
  then
    return 1
  fi

  qemu-img create -f qcow2 ${rootdiskfolder}/rootdisk $rootdisksize

  return $?
}

create_datadisk() {
  local datadiskfolder=$1
  local datadiskname=$2
  local datadisksize=$3
  
  datadisksize=$(convert_size_to_gb $datadisksize)
  
  if [ $? -gt 0 ]
  then
    return 1
  fi

  qemu-img create -f qcow2 ${datadiskfolder}/${datadiskname} $datadisksize
  
  return $?
}

convert_size_to_gb() {
  local size=$1
  
  suffix=${size:(-1)}
  case $suffix in
    G)   
         ;;
    [0-9])   size=${size}G
         ;;
    *)   printf "Error in disk size: expect G as a suffix or no suffix\n"
         return 1
         ;;
  esac
  
  echo $size
  return 0
}

# set -x

tflag=0
iflag=0
Sflag=0
fflag=0
sflag=0
nflag=0

while getopts 't:i:S:f:s:n:u:' OPTION
do
  case $OPTION in
  t)	tflag=1
		templatepath="$OPTARG"
		;;
  i)	iflag=1
		rootdiskfolder="$OPTARG"
		;;
  S)	Sflag=1
  		rootdisksize="$OPTARG"
  		;;
  f)	fflag=1
  		datadiskfolder="$OPTARG"
  		;;
  s)	sflag=1
		datadisksize="$OPTARG"
		;;
  n)	nflag=1
  		datadiskname="$OPTARG"
  		;;
  ?)	usage
		exit 2
		;;
  esac
done

# Check all parameters
check_params
exit_if_error $? "$(usage)"

# Create the rootdisk folder if necessary, and make sure there is no existing rootdisk there
check_rootdisk $rootdiskfolder
exit_if_error $? "Failed to create rootdisk; a rootdisk already exists at $rootdiskfolder."

if [ "$tflag" == "1" ]
then
  # A template path was passed in, so clone the template to a new rootdisk
  clone_template_to_rootdisk $rootdiskfolder $templatepath
  exit_if_error $? "Failed to clone template $templatepath to $rootdiskfolder/rootdisk."
else
  # A template path was not passed in, so create a blank rootdisk at the rootdisk folder
  create_blank_rootdisk $rootdiskfolder $rootdisksize 
  exit_if_error $? "Failed to create a blank rootdisk at $rootdiskfolder/rootdisk."
fi

if [ -n "$datadisksize" ]
then
  # Create the datadisk folder if necessary, and make sure there is no existing datadisk there
  check_datadisk $datadiskfolder $datadiskname 
  cleanup_and_exit_if_error $? "Failed to create datadisk in $datadiskfolder; datadisk with $datadiskname already exists." $rootdiskfolder 

  # Create the datadisk
  create_datadisk $datadiskfolder $datadiskname $datadisksize
  cleanup_and_exit_if_error $? "Failed to create datadisk in $datadiskfolder of size $datadisksize." $rootdiskfolder $datadiskfolder $datadiskname
fi

exit 0
