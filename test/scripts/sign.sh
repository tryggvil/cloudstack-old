#!/usr/bin/env bash
set -e
DST='../src/'
java -cp ${DST}commons-httpclient-3.1.jar:${DST}mysql-connector-java-5.1.7-bin.jar:${DST}commons-logging-1.1.1.jar:${DST}commons-codec-1.3.jar:${DST}testclient.jar:${DST}log4j-1.2.15.jar:${DST}trilead-ssh2-build213.jar:${DST}cloud-utils.jar:.././conf com.vmops.test.utils.SignRequest $*