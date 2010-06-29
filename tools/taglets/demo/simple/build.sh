#!/bin/sh
# Shell script to create the JavaDoc of the simple demo.

TAGLET_CLASS="net.sourceforge.taglets.Taglets"
TAGLET_PATH="../../taglets.jar"
TAGLETS="-taglet ${TAGLET_CLASS} -tagletpath ${TAGLET_PATH}"

rm -r doc
exec javadoc ${TAGLETS} -d doc -sourcepath src @packages.txt
