Taglets Collection - Simple Demo
================================================================================


Simple Demo
--------------------------------------------------------------------------------

This directory contains a simple demonstration use of the Taglets Collection.
It contains a couple of sample sources that do not serve any purpose except
to provide a basis for JavaDoc to work on.

To build the demo JavaDoc check out the sections below:
- Commandline JavaDoc Execution
- Batchfile JavaDoc Execution
- Apache Ant JavaDoc Execution

This simple demo will only include the Taglets Collection in the generation
of the JavaDoc documentation. To see a more advanced application of the 
Taglets Collection that also configures some taglets features and creates a 
documentation similar to the one included in the documentation check out the 
'extended' sample...


Commandline JavaDoc Execution
--------------------------------------------------------------------------------

To build the JavaDoc of this sample using commandline tools execute the
commands described below. (To simplify command invocation all packages
required to build the JavaDoc have been stored in a package file suitable
for use with the JavaDoc '@file' argument.)

Windows:
--------

> javadoc ^
    -taglet net.sourceforge.taglets.Taglets -tagletpath ..\..\taglets.jar ^
    -d doc -sourcepath src @packages.txt

(Note: The ^ is the commandline continuation character, it is used only to
improve readability of the sample above. Usually you write the whole command
on a single line...)

Unix/Mac OSX:
-------------

% javadoc \
    -taglet net.sourceforge.taglets.Taglets -tagletpath ../../taglets.jar \
    -d doc -sourcepath src @packages.txt

(Note: The \ is the commandline continuation character, it is used only to
improve readability of the sample above. Usually you write the whole command
on a single line...)


Batchfile JavaDoc Execution
--------------------------------------------------------------------------------

To give an idea how you might invoke JavaDoc that uses the Taglets Collection
simply check out the following batchfiles. (Note: Make sure you execute them
in the directory they are stored, they use relative pathes.)

Windows:
--------

> build.cmd

Unix/Mac OSX:
-------------

% sh build.sh


Ant JavaDoc Execution
--------------------------------------------------------------------------------

By far the most simple method of building the JavaDoc for the sample is by
using Apache Ant. Not only will it work on any platform, it is also the most
simple way to configure all the JavaDoc features.

Check out the 'build.xml' for a simple application of the Taglets Collection
and build the JavaDoc using:

$ ant doc


================================================================================
Taglets Collection - Simple Demo
