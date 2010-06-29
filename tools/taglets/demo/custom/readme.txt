Taglets Collection - Custom Demo
================================================================================


Custom Demo
--------------------------------------------------------------------------------

This directory contains an a example that shows how to create custom taglets.

It is strongly recommended you first have a look at the simple and the extended
demo before attempting to write your own taglets to get an idea how the
Taglets Collection works in general.


Objective
--------------------------------------------------------------------------------

For the custom demo I have chosen to create a simple inline tag that generates
the JavaDoc generation date and time in a customizable format.

The syntax for the new tag should be {@dateTime [dateFormat]} and when used
without a date format, i.e. {@dateTime} it should simply print the date/time
in a preconfigured format.

To simplify things the "dateFormat" option string accept simply uses the one
accepted by java.text.SimpleDateFormat, e.g. things like "yyyy/MM/dd HH:mm:ss".

The configuration parameters supported in the taglets properties file should
include "language" to set the output language (two letter lowercase ISO 639 
code like e.g. "en" for english) and the "format" to use by default.

The tag configuration in the properties file should therefore look something
like this for the {@dateTime} tag:

Taglets.taglet.dateTime= \
	net.sourceforge.taglets.simple.inline.DateTimeInlineTaglet
Taglets.taglet.dateTime.language= en
Taglets.taglet.dateTime.format= yyyy/MM/dd HH:mm:ss


Implementation
--------------------------------------------------------------------------------

As you can see from the configuration options above I have decided to make the 
implementation of the tag in class DateTimeInlineTaglet. The source code for 
this class is included in the "src" folder of this demo. 

To get an idea how to create other custom inline and block tags please have a
look at the source code of the simple taglets of the main distribution. You
will find plenty of examples that should help you get started creating the
custom tags you need...

(Except for the unit tests the sources of the Taglets Collection are included
in file "taglets-sources.jar" of your binary distribution.)


Build and Test
--------------------------------------------------------------------------------

The Ant build file included with the custom demo first compiles the new taglet
and creates a JAR archive that contains the taglet as well as any resources
needed to build the JavaDoc using the custom taglet.

I have decided to go for a JAR archive to bundle definition of the new inline 
tags {@dateTime}, {@germanDateTime} and {@frenchDateTime} (done in the 
properties file "rsrc/taglets.properties") with the implementation of the new
DateTimeInlineTaglet.

Using the "custom.jar" archive the call to JavaDoc simply uses the custom
archive plus the original "taglets.jar" in the JavaDoc taglet path, e.g.

javadoc \
  -taglet net.sourceforge.taglets.Taglets \
  -tagletpath custom.jar:taglets.jar \
  -d doc -sourcepath src @packages.txt

(Note: The \ is the commandline continuation character, it is used only to
improve readability of the sample above. Usually you write the whole command
on a single line...)


================================================================================
Taglets Collection - Custom Demo
