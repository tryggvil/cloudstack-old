Taglets Collection - Extended Demo
================================================================================


Extended Demo
--------------------------------------------------------------------------------

This directory contains an extended demonstration use of the Taglets Collection.

To simplify build it uses Apache Ant, to see a sample that also lists command-
line options for JavaDoc please check out the 'simple' demo.

In contrast to the simple demo this extended demo makes use of:

- Configuration of the '@adm' tag.
- Mandatory use of '@since' (if omitted a warning is generated).
- Custom headers and footers that include a logo.
- Inclusion of a favourites icon in the docu generated.

Do not be surprised if you look at the JavaDoc generated, I tried to make some
Java/Chocolate theme. Well, ... you'll at least get the idea on how to customize
the output of JavaDoc like this ...


Apache Ant Configuration
--------------------------------------------------------------------------------

Check out the 'build.xml' to get an idea how to use the extended features of 
JavaDoc and the Taglets Collection.

The build file contains a couple of comments that should give some hints on
what the options do. More information is available in user guide HTML docu.


Taglets Configuration
--------------------------------------------------------------------------------

The extended demo uses a 'taglets.properties' file that extends the default
taglets configuration. The property file contains some comments about what it
does - as for the rest, please refer to the user guide HTML documentation.

The taglets default configuration is also included in the 'rsrc' folder of this
extended demo. The file 'taglets-default.properties' itself is not used in the 
build process but only included for reference if you are curious what the 
default configuration looks like.


================================================================================
Taglets Collection - Extended Demo
