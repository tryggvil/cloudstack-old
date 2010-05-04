#!/usr/bin/env bash
PATHSEP=':'
if [[ $OSTYPE == "cygwin" ]] ; then
  PATHSEP=';'
fi

#directory with jar files
DST='../src/'

CP=${DST}commons-httpclient-3.1.jar${PATHSEP}${DST}commons-logging-1.1.1.jar${PATHSEP}${DST}commons-codec-1.4.jar${PATHSEP}${DST}testclient.jar${PATHSEP}${DST}log4j-1.2.15.jar${PATHSEP}${DST}trilead-ssh2-build213.jar${PATHSEP}${DST}cloud-utils.jar${PATHSEP}.././conf
java -cp $CP com.vmops.test.stress.TestClientWithAPI $*


