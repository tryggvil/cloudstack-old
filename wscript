#! /usr/bin/env python
# -*- coding: utf-8 -*-

# the following two variables are used by the target "waf dist"
VERSION = '1.9.10'
APPNAME = 'vmops'
# if you change it here, you need to change it also in vmops.spec, add a %changelog entry there, and add an entry in debian/changelog

import shutil,os
import email,time
import optparse
import platform
import Utils,Node,Options,Logs,Scripting,Environment,Build,Configure
from subprocess import Popen as _Popen,PIPE
import os
import sys
from os import unlink as _unlink, makedirs as _makedirs, getcwd as _getcwd, chdir as _chdir
from os.path import abspath as _abspath, basename as _basename, dirname as _dirname, exists as _exists, isdir as _isdir, split as _split, join as _join, sep, pathsep, pardir
from glob import glob as _glob
import zipfile,tarfile
try:
  from os import chmod as _chmod,chown as _chown
  import pwd,stat,grp
except ImportError:
  _chmod,_chown,pwd,stat,grp = (None,None,None,None,None)

# these variables are mandatory ('/' are converted automatically)
srcdir = '.'
blddir = 'artifacts'

# list of JARs that provide the build dependencies
# "j" generally means /usr/share/java
# when adding a new system JAR file:
# 1. check the hard_deps variable at the bottom and add the appropriate packaging information,
# 2. add the RPM package that contains the jarfile into the vmops.spec file
# 3. (the jars specified here will be looked up at runtime with build-classpath, on linux installs)
systemjars = {
	'common':
	(
		"commons-collections.jar",
		"commons-daemon.jar",
		"commons-dbcp.jar",
		"commons-logging.jar",
		"commons-logging-api.jar",
		"commons-pool.jar",
		"commons-httpclient.jar",
		"ws-commons-util.jar",
	),
	'Fedora':
	(
		"tomcat6-servlet-2.5-api.jar",
		#"tomcat6/catalina.jar", # all supported distros put the file there
	),
	'CentOS':
	(
		"tomcat6-servlet-2.5-api.jar",
		#"tomcat6/catalina.jar", # all supported distros put the file there
	),
	'Ubuntu':
	(
		"servlet-api-2.5.jar",
		#"catalina.jar",
	),
	'Windows':
	(
		"servlet-api.jar", # this ships with Tomcat in lib/
		#"catalina.jar",
	),
	'Mac':
	(
		"servlet-api.jar",
	),	
}

#A JAR dependency may be:

#- required during compile-time
#- required during run-time

#A JAR file can be available:

#- in Tomcat lib/
#- as a Linux system package
#- in the vmops-deps package
#- in the vmops-premium package

# servlet-api:

#- is required during compile-time
#- is required during management server run-time
#- is available in Tomcat lib/ (Windows/Mac) AND as a system package (Linux)
#  -> ERGO, we do not need to include it 
#           but it should be named on the SYSTEMJARS (which is then used in the tomcat6.conf to build the MS classpath),
#           and it should also be in the compile-time CLASSPATH

# =====================================

# list of dependencies for configure check
# (classname,fedora package name,ubuntu package name)
# when adding a package name here, also add to the spec or debian control file
hard_deps = [
	('java.io.FileOutputStream',"java-devel","openjdk-6-jdk"),
	('javax.servlet.http.Cookie',"tomcat6-servlet-2.5-api","libservlet2.5-java"),
	('org.apache.naming.resources.Constants',"tomcat6-lib","libtomcat6-java"),
]

# things not to include in the source tarball
# exclude by file name or by _glob (wildcard matching)
for _globber in [
	["dist",    # does not belong in the source tarball
	"build",   # semi-obsolete
	"system",  # for windows
	"tools",  # not needed
	"override",  # not needed
	"eclipse", # only there to please eclipse
	"repomanagement",  # internal management stuff
	"console-viewer",  # internal management stuff
	"target",],  # eclipse workdir
	_glob("*/*.spec"),
	_glob("*/spec/*.spec"),
	_glob("./*.xml"),
	]:
	for f in _globber: Scripting.excludes.append(_basename(f)) # _basename() only the filename

Configure.autoconfig = True

# Support functions

def inspect(x):
	"""Look inside an object"""
	for m in dir(x): print m,":	",getattr(x,m)

def _trm(x,y):
	if len(x) > y: return x[:y] + "..."
	return x

def _in_srcdir(directory,f):
	"""Wrap a function f around a chdir() call, and when the function returns, rewind the directory stack."""
	def g(*args,**kw):
		olddir = _getcwd()
		_chdir(directory)
		try: return f(*args,**kw)
		finally: _chdir(olddir)
	return g

def getrpmdeps():
	def rpmdeps(fileset):
		for f in fileset:
			lines = file(f).readlines()
			lines = [ x[len("BuildRequires: "):] for x in lines if x.startswith("BuildRequires") ]
			for l in lines:
				deps = [ x.strip() for x in l.split(",") ]
				for d in deps:
					if "%s-"%APPNAME in d: continue
					yield d
		yield "rpm-build"

	deps = set(rpmdeps(_glob("./*.spec")))
	return deps

def getdebdeps():
	def debdeps(fileset):
		for f in fileset:
			lines = file(f).readlines()
			lines = [ x[len("Build-Depends: "):] for x in lines if x.startswith("Build-Depends") ]
			for l in lines:
				deps = [ x.strip() for x in l.split(",") ]
				for d in deps:
					if "%s-"%APPNAME in d: continue
					yield d
		yield "build-essential"
		yield "devscripts"
		yield "debhelper"

	deps = set(debdeps(["debian/control"]))
	return deps

# CENTOS does not have this -- we have to put this here
try:
	from subprocess import check_call as _check_call
	from subprocess import CalledProcessError
except ImportError:
	def _check_call(*popenargs, **kwargs):
		import subprocess
		retcode = subprocess.call(*popenargs, **kwargs)
		cmd = kwargs.get("args")
		if cmd is None: cmd = popenargs[0]
		if retcode: raise CalledProcessError(retcode, cmd)
		return retcode

	class CalledProcessError(Exception):
		def __init__(self, returncode, cmd):
			self.returncode = returncode ; self.cmd = cmd
		def __str__(self): return "Command '%s' returned non-zero exit status %d" % (self.cmd, self.returncode)
def throws_command_errors(f):
	def g(*args,**kwargs):
		try: return f(*args,**kwargs)
		except CalledProcessError,e:
			raise Utils.WafError("system command %s failed with error value %s"%(e.cmd[0],e.returncode))
		except IOError,e:
			if e.errno is 32:
				raise Utils.WafError("system command %s terminated abruptly, closing communications with parent's pipe"%e.cmd[0])
			raise
	return g

def c(cmdlist):
	# Run a command with _check_call, pretty-printing the cmd list
	Utils.pprint("BLUE"," ".join(cmdlist))
	return _check_call(cmdlist)

def svninfo(*args):
	try: p = _Popen(['svn','info']+list(args),stdin=PIPE,stdout=PIPE,stderr=PIPE)
	except OSError,e:
		if e.errno == 2: return '' # svn command is not installed
		raise
	stdout,stderr = p.communicate('')
	retcode = p.wait()
	# If the guess fails, just return nothing.
	if retcode: return
	return stdout

def _getbuildnumber():
	n = Options.options.BUILDNUMBER
	if n:
		# luntbuild prepends "build-" to the build number.  we work around this here:
		if n.startswith("build-"): n = n[6:]
		# SVN identifiers prepend "$Revision:" to the build number.  we work around this here:
		if n.startswith("$Revision:"): n = n[11:-2].strip()
		return n
	else:
		# Try to guess the SVN revision number by calling SVN info.
		stdout = svninfo()
		if not stdout: return ''
		# Filter lines.
		rev = [ x for x in stdout.splitlines() if x.startswith('Revision') ]
		if not rev: return ''
		# Parse revision number.
		rev = rev[0][10:].strip()
		return rev

def _subst_add_destdir(x,bld):
	a = "${DESTDIR}" + x
	a = a.replace("${DESTDIR}",Options.options.destdir)
	a = Utils.subst_vars(a,bld.env)
	if a.startswith("//"): a = a[1:]
	return a

def setownership(ctx,path,owner,group,mode=None):
	def f(bld,path,owner,group,mode):
		dochown = not Options.options.NOCHOWN \
				and hasattr(os,"getuid") and os.getuid() == 0 \
				and _chown \
				and _chmod \
				and pwd \
				and grp \
				and stat
		if not dochown: return
		
		try: uid = pwd.getpwnam(owner).pw_uid
		except KeyError,e: raise Utils.WafError("If installing as root, please either create a %s user or use the --nochown parameter of waf install to install the files as root")
		try: gid = grp.getgrnam(group).gr_gid
		except KeyError,e: raise Utils.WafError("If installing as root, please either create a %s group or use the --nochown parameter of waf install to install the files as root")
		
		path = _subst_add_destdir(path,bld)
		current_uid,current_gid = os.stat(path).st_uid,os.stat(path).st_gid
		if current_uid != uid:
			print os.stat(path)
			print current_uid, uid
			Utils.pprint("GREEN","* setting owner of %s to UID %s"%(path,uid))
			_chown(path,uid,current_gid)
			current_uid = uid
		if current_gid != gid:
			Utils.pprint("GREEN","* setting group of %s to GID %s"%(path,gid))
			_chown(path,current_uid,gid)
			current_gid = gid
		if mode is not None:
			current_mode = stat.S_IMODE(os.stat(path).st_mode)
			if current_mode != mode:
				Utils.pprint("GREEN","* adjusting permissions on %s to mode %o"%(path,mode))
				_chmod(path,mode)
				current_mode = mode
	
	if Options.is_install:
		ctx.add_post_fun(lambda ctx: f(ctx,path,owner,group,mode))

def mkdir_p(directory):
	if not _isdir(directory):
		Utils.pprint("GREEN","Creating directory %s and necessary parents"%directory)
		_makedirs(directory)

# this will enforce the after= ordering constraints in the javac task generators
from TaskGen import after, feature
@feature('*')
@after('apply_core', 'apply_java', 'apply_subst')
def process_after(self):
	lst = self.to_list(getattr(self, 'after', []))
	for x in lst:
		obj = self.bld.name_to_obj(x,self.bld.env)
		if not obj: break
		obj.post()
		for a in obj.tasks:
			for b in self.tasks:
				b.set_run_after(a)

def _get_context():
	ctx = Build.BuildContext()
	ctx.load_dirs(_abspath(srcdir),_abspath(blddir))
	ctx.load_envs()
	return ctx

@throws_command_errors
def run_java(classname,classpath,options=None,arguments=None):
	ctx = _get_context()
	conf = ctx
	
	if not options: options = []
	if not arguments: arguments = []
	if type(classpath) in [list,tuple]: classpath = pathsep.join(classpath)
	
	Utils.pprint("BLUE","Run-time CLASSPATH:")
	for v in classpath.split(pathsep): Utils.pprint("BLUE","     %s"%v)

	cmd = ["java","-classpath",classpath] + options + [classname] + arguments
	Utils.pprint("BLUE"," ".join([ _trm(x,32) for x in cmd ]))
	_check_call(cmd)

# This is where the real deal starts

def dist_hook():
	stdout = svninfo("..")
	if stdout:
		# SVN available
		rev = [ x for x in stdout.splitlines() if x.startswith('Revision') ]
		if not rev: rev = ''
		else: rev = "SVN " + rev[0].strip()
		url = [ x for x in stdout.splitlines() if x.startswith('URL') ]
		if not url: url = ''
		else: url = "SVN " + url[0].strip()
		f = file("sccs-info","w")
		if rev: f.write("%s\n"%rev)
		if url: f.write("%s\n"%url)
		f.flush()
		f.close()
	else:
		# No SVN available
		if _exists("sccs-info"):
			# If the file already existed, we preserve it
			return
		else:
			f = file("sccs-info","w")
			f.write("No revision control information could be detected when the source distribution was built.")
			f.flush()
			f.close()
			

def set_options(opt):
	"""Register command line options"""
	opt.tool_options('gnu_dirs')
	if platform.system() not in ['Windows',"Darwin"]: opt.tool_options('compiler_cc')
	opt.tool_options('python')
	
        inst_dir = opt.get_option_group('--bindir') # get the group that contains bindir
	inst_dir.add_option('--javadir', # add javadir to the group that contains bindir
		help = 'Java class and jar files [Default: ${DATADIR}/java]',
		default = '',
		dest = 'JAVADIR')
	inst_dir.add_option('--with-tomcat', # add javadir to the group that contains bindir
		help = 'Path to installed Tomcat 6 environment [Default: ${DATADIR}/tomcat6 (unless %%CATALINA_HOME%% is set)]',
		default = '',
		dest = 'TOMCATHOME')
        inst_dir = opt.get_option_group('--srcdir') # get the group that contains the srcdir
	inst_dir.add_option('--with-db-host', # add javadir to the group that contains bindir
		help = 'Database host to use for waf deploydb [Default: 127.0.0.1]',
		default = '127.0.0.1',
		dest = 'DBHOST')
	inst_dir.add_option('--with-db-user', # add javadir to the group that contains bindir
		help = 'Database user to use for waf deploydb [Default: root]',
		default = 'root',
		dest = 'DBUSER')
	inst_dir.add_option('--with-db-pw', # add javadir to the group that contains bindir
		help = 'Database password to use for waf deploydb [Default: ""]',
		default = '',
		dest = 'DBPW')
	inst_dir.add_option('--tomcat-user',
		help = 'UNIX user that the management server initscript will switch to [Default: <autodetected>]',
		default = '',
		dest = 'MSUSER')
	inst_dir.add_option('--no-dep-check',
		action='store_true',
		help = 'Skip dependency check and assume JARs already exist',
		default = False,
		dest = 'NODEPCHECK')
	inst_dir.add_option('--fast',
		action='store_true',
		help = 'does ---no-dep-check',
		default = False,
		dest = 'NODEPCHECK')
        inst_dir = opt.get_option_group('--force') # get the group that contains the force
	inst_dir.add_option('--nochown',
		action='store_true',
		help = 'skip chown and chmod upon install (skipped on Windows or by non-root users by default)',
		default = False,
		dest = 'NOCHOWN')
	inst_dir.add_option('--preserve-config',
		action='store_true',
		help = 'do not install configuration files',
		default = False,
		dest = 'PRESERVECONFIG')

	debugopts = optparse.OptionGroup(opt.parser,'run/debug options')
	opt.add_option_group(debugopts)
	debugopts.add_option('--debug-port', # add javadir to the group that contains bindir
		help = 'Port on which the debugger will listen when running waf debug [Default: 8787]',
		default = '8787',
		dest = 'DEBUGPORT')
	debugopts.add_option('--debug-suspend',
		action='store_true',
		help = 'Suspend the process upon startup so that a debugger can attach and set breakpoints',
		default = False,
		dest = 'DEBUGSUSPEND')
	debugopts.add_option('--run-verbose',
		action='store_true',
		help = 'Run Tomcat in verbose mode (java option -verbose)',
		default = False,
		dest = 'RUNVERBOSE')
	
	rpmopts = optparse.OptionGroup(opt.parser,'RPM/DEB build options')
	opt.add_option_group(rpmopts)
	rpmopts.add_option('--build-number', # add javadir to the group that contains bindir
		help = 'Build number [Default: SVN revision number for builds from checkouts, or empty for builds from source releases]',
		default = '',
		dest = 'BUILDNUMBER')
	rpmopts.add_option('--prerelease', # add javadir to the group that contains bindir
		help = 'Branch name to append to the release number (if specified, alter release number to be a prerelease); this option requires --build-number=X [Default: nothing]',
		default = '',
		dest = 'PRERELEASE')
		
def showconfig(conf):
	"""prints out the current configure environment configuration"""
	if not hasattr(conf,"env"):
		conf = _get_context()
		Utils.pprint("WHITE","Build environment:")
	
	for key,val in sorted(conf.env.get_merged_dict().items()):
		if "CLASSPATH" in key:
			Utils.pprint("BLUE","  %s:"%key)
			for v in val.split(pathsep):
				Utils.pprint("BLUE","     %s"%v)
			continue
		Utils.pprint("BLUE","  %s:	%s"%(key,val))

def configure(conf):
	"""examines environment, then:
	     - configures classpath as per environment or command-line options
	     - detects Tomcat (on Windows)
	     - detects or configures directories according to command-line options"""
	conf.check_tool('misc')
	conf.check_tool('java')
	conf.check_tool("gnu_dirs")
	conf.check_tool("python")
	conf.check_python_version((2,4,0))
	
	conf.check_message_1('Detecting distribution')
	if platform.system() == 'Windows': conf.env.DISTRO = "Windows"
	elif platform.system() == 'Darwin': conf.env.DISTRO = "Mac"
	elif _exists("/etc/network"): conf.env.DISTRO = "Ubuntu"
	elif _exists("/etc/fedora-release"): conf.env.DISTRO = "Fedora"
	elif _exists("/etc/centos-release") or _exists("/etc/redhat-release"): conf.env.DISTRO = "CentOS"
	else: conf.env.DISTRO = "unknown"
	if conf.env.DISTRO == "unknown": c = "YELLOW"
	else: 				    c = "GREEN"
	conf.check_message_2(conf.env.DISTRO,c)

	if conf.env.DISTRO not in ["Windows","Mac"]:
		conf.check_tool('compiler_cc')
		conf.check_cc(lib='pthread')
		conf.check_cc(lib='dl')

	# waf uses slashes somewhere along the line in some paths.  we fix them on windows.
	if conf.env.DISTRO in ['Windows']:
		for pth in [ x for x in conf.env.get_merged_dict().keys() if x.endswith("DIR") ]:
			conf.env[pth] = conf.env[pth].replace("/","\\")
	
	for a in "DBHOST DBUSER DBPW DBDIR".split():
		conf.env[a] = getattr(Options.options, a, '')

        conf.check_message_1('Determining management server user name')
	msuser = getattr(Options.options, 'MSUSER', '')
	if msuser:
		conf.env.MSUSER = msuser
		conf.check_message_2("%s (forced through --tomcat-user)"%conf.env.MSUSER,"GREEN")
	else:
		if conf.env.DISTRO in ['Windows','Mac']:
			conf.env.MSUSER = 'root'
			conf.check_message_2("%s (not used on Windows or Mac)"%conf.env.MSUSER,"GREEN")
		else:
			conf.env.MSUSER = 'vmops'
			conf.check_message_2("%s (Linux default)"%conf.env.MSUSER,"GREEN")

        conf.check_message_1('Detecting Tomcat')
	tomcathome = getattr(Options.options, 'TOMCATHOME', '')
	if tomcathome:
		conf.env.TOMCATHOME = tomcathome
		conf.check_message_2("%s (forced through --with-tomcat)"%conf.env.TOMCATHOME,"GREEN")
	else:
		if    "TOMCAT_HOME" in conf.environ and conf.environ['TOMCAT_HOME'].strip():
			conf.env.TOMCATHOME = conf.environ["TOMCAT_HOME"]
			conf.check_message_2("%s (got through environment variable %%TOMCAT_HOME%%)"%conf.env.TOMCATHOME,"GREEN")
		elif  "CATALINA_HOME" in conf.environ and conf.environ['CATALINA_HOME'].strip():
			conf.env.TOMCATHOME = conf.environ['CATALINA_HOME']
			conf.check_message_2("%s (got through environment variable %%CATALINA_HOME%%)"%conf.env.TOMCATHOME,"GREEN")
		elif _isdir("/usr/share/tomcat6"):
			conf.env.TOMCATHOME = "/usr/share/tomcat6"
			conf.check_message_2("%s (detected existence of system directory)"%conf.env.TOMCATHOME,"GREEN")
		else:
			conf.env.TOMCATHOME = _join(conf.env.DATADIR,'tomcat6')
			conf.check_message_2("%s (assumed presence of Tomcat there)"%conf.env.TOMCATHOME,"GREEN")

	conf.env.AGENTPATH = _join(APPNAME,"agent")
	conf.env.CPPATH = _join(APPNAME,"console-proxy")
	conf.env.MSPATH = _join(APPNAME,"management")
	conf.env.USAGEPATH = _join(APPNAME,"usage")
	conf.env.SETUPPATH = _join(APPNAME,"setup")
	
	if conf.env.DISTRO in ['Windows','Mac']:
		conf.env.MSENVIRON = conf.env.TOMCATHOME
		conf.env.MSCONF  = _join(conf.env.TOMCATHOME,"conf")
		conf.env.MSLOGDIR = _join(conf.env.TOMCATHOME,"logs")
		conf.env.MSMNTDIR = _join(conf.env.TOMCATHOME,"mnt")
	else:
		conf.env.MSENVIRON = _join(conf.env.DATADIR,conf.env.MSPATH)
		conf.env.MSCONF    = _join(conf.env.SYSCONFDIR,conf.env.MSPATH)
		conf.env.MSLOGDIR = _join(conf.env.LOCALSTATEDIR,"log",conf.env.MSPATH)
		conf.env.MSMNTDIR = _join(conf.env.SHAREDSTATEDIR,APPNAME,"mnt")

	conf.env.PIDDIR = _join(conf.env.LOCALSTATEDIR,"run")
	conf.env.LOCKDIR = _join(conf.env.LOCALSTATEDIR,"lock","subsys")

        conf.check_message_1('Detecting JAVADIR')
	javadir = getattr(Options.options, 'JAVADIR', '')
	if javadir:
		conf.env.JAVADIR = javadir
		conf.check_message_2("%s (forced through --javadir)"%conf.env.JAVADIR,"GREEN")
	elif conf.env.DISTRO in ['Windows','Mac']:
		conf.env.JAVADIR = _join(conf.env['TOMCATHOME'],'lib')
		conf.check_message_2("%s (using Tomcat's lib/ directory)"%conf.env.JAVADIR,"GREEN")
	else:
		conf.env.JAVADIR = _join(conf.env.DATADIR,'java')
		conf.check_message_2("%s (using default ${DATADIR}/java directory)"%conf.env.JAVADIR,"GREEN")

	if conf.env.DISTRO in ["Windows","Mac"]:
		conf.env.PREMIUMJAVADIR = conf.env.JAVADIR
		conf.env.SYSTEMJAVADIR = conf.env.JAVADIR
	else: 
		conf.env.PREMIUMJAVADIR = _join(conf.env.JAVADIR,"vmops-premium")
		conf.env.SYSTEMJAVADIR = "/usr/share/java"
	
	in_javadir = lambda name: _join(conf.env.JAVADIR,_basename(name)) # $PREFIX/share/java
	in_system_javadir = lambda name: _join(conf.env.SYSTEMJAVADIR,name) # /usr/share/java
	in_premiumjavadir = lambda name: _join(conf.env.PREMIUMJAVADIR,name) # $PREFIX/share/java/vmops-premium

	conf.check_message_1('Building classpaths')
	
	# == Here we build the run-time classpaths ==
	
	# The system classpath points to JARs we expect the user has installed using distro packages
	# not used for Windows and Mac (except for servlet-api.jar) because we install them from the thirdparty/ directory
	sysjars = list(systemjars['common'])
	if conf.env.DISTRO in systemjars.keys(): sysjars = sysjars + list(systemjars[conf.env.DISTRO])
	conf.env.SYSTEMJARS = " ".join(sysjars) # used by build-classpath in the initscripts
	conf.env.SYSTEMCLASSPATH = pathsep.join([ in_system_javadir(x) for x in sysjars ]) # used for compile, waf run and simulate_agent
	
	# the deps classpath points to JARs that are installed in the vmops-deps package
	# these will install on Tomcat6's lib/ directory on Windows and Mac
	depsclasspath = [ in_javadir(_basename(x)) for x in _glob(_join(conf.srcdir,"deps","*.jar")) ]
	conf.env.DEPSCLASSPATH = pathsep.join(depsclasspath)

	# the MS classpath points to JARs required to run the management server
	msclasspath = [ in_javadir("vmops-%s.jar"%x) for x in "utils core server premium".split() ]
	conf.env.MSCLASSPATH = pathsep.join(msclasspath)

	# the agent and simulator classpaths point to JARs required to run these two applications
	agentclasspath = [ in_javadir("vmops-%s.jar"%x) for x in "utils core server premium agent console console-proxy".split() ]
	conf.env.AGENTCLASSPATH = pathsep.join(agentclasspath)
	conf.env.AGENTSIMULATORCLASSPATH = pathsep.join(agentclasspath+[in_javadir("vmops-agent-simulator.jar")])

	usageclasspath = [ in_javadir("vmops-%s.jar"%x) for x in "utils core server premium usage".split() ]
	conf.env.USAGECLASSPATH = pathsep.join(usageclasspath)

	# the premium classpath points to JARs that are installed in the vmops-premium-deps package
	# these will install on Tomcat6's lib/ directory on Windows and Mac
	premiumclasspath = [ in_premiumjavadir(_basename(x)) for x in _glob(_join(conf.srcdir,"thirdparty","*.jar")) ]
	conf.env.PREMIUMCLASSPATH = pathsep.join(premiumclasspath)

	# The compile path is composed of the
	projects = [ _basename(_split(x)[0]) for x in _glob(_join(conf.srcdir,"*","src")) ]
	srcdirs = [ _join(conf.blddir,conf.envname,x,"src") for x in projects ]
	# 1. source directories (without including the JARs)
	# JARs are not included because in case of parallel compilation (IOW, all the time), javac picks up half-written JARs and die
	compilecp = list(srcdirs)
	# 2.a) the thirdparty/ directory in the source if on Windows / Mac
	# 2.b) the deps/ directory in the source if on Linux
	if conf.env.DISTRO in ["Windows","Mac"]:  compilecp+= _glob(_join(conf.srcdir,"thirdparty","*.jar"))
	else:					  compilecp+= _glob(_join(conf.srcdir,"deps","*.jar"))
	# 3. the system classpath (system-installed JARs)
	compilecp+= [ conf.env.SYSTEMCLASSPATH ]
	compilecp+= _glob(_join(conf.env.TOMCATHOME,'bin',"*.jar"))
	compilecp+= _glob(_join(conf.env.TOMCATHOME,'lib',"*.jar"))
	conf.env.CLASSPATH = pathsep.join(compilecp)
	conf.check_message_2('Done','GREEN')
	
	buildnumber = _getbuildnumber()
	if buildnumber: buildnumber = "." + buildnumber
	conf.env.Specification_Version = VERSION + buildnumber
	conf.env.Implementation_Version = VERSION + buildnumber
	
	conf.env.AGENTLIBDIR = Utils.subst_vars(_join("${LIBDIR}","${AGENTPATH}"),conf.env)
	conf.env.AGENTSYSCONFDIR = Utils.subst_vars(_join("${SYSCONFDIR}","${AGENTPATH}"),conf.env)
	conf.env.AGENTLOGDIR = Utils.subst_vars(_join("${LOCALSTATEDIR}","log","${AGENTPATH}"),conf.env)

	conf.env.USAGELOGDIR = Utils.subst_vars(_join("${LOCALSTATEDIR}","log","${USAGEPATH}"),conf.env)
	conf.env.USAGESYSCONFDIR = Utils.subst_vars(_join("${SYSCONFDIR}","${USAGEPATH}"),conf.env)

	conf.env.MSLOG = _join(conf.env.MSLOGDIR,"management-server.log")
	conf.env.APISERVERLOG = _join(conf.env.MSLOGDIR,"api-server.log")
	conf.env.AGENTLOG = _join(conf.env.AGENTLOGDIR,"agent.log")
	conf.env.USAGELOG = _join(conf.env.USAGELOGDIR,"usage.log")
	
	conf.env.CPLIBDIR = Utils.subst_vars(_join("${LIBDIR}","${CPPATH}"),conf.env)
	conf.env.SETUPDATADIR = Utils.subst_vars(_join("${DATADIR}","${SETUPPATH}"),conf.env)
	
	 # log4j config and property config files require backslash escapes on Windows
	if conf.env.DISTRO in ["Windows"]:
		for log in "MSLOG APISERVERLOG AGENTLIBDIR USAGELOG AGENTLOG".split(): conf.env[log] = conf.env[log].replace("\\","\\\\")

	no_java_class = lambda x: conf.check_java_class(x,with_classpath=conf.env.CLASSPATH) != 0
	def require_dependency(javacls,packagenames):
		if no_java_class(javacls):
			conf.fatal("Cannot find the Java class %s (available in the distro packages: %s)"%(
				javacls, ", ".join(packagenames)))
	def check_dependencies(deps):
		for d in deps:
			cls,pkgs = d[0],d[1:]
			require_dependency(cls,pkgs)

	if not getattr(Options.options, 'NODEPCHECK', ''): check_dependencies(hard_deps)
	
	Utils.pprint("WHITE","Configure finished.  Use 'python waf showconfig' to show the configure-time environment.")

def build(bld):
	"""builds the entire stack"""
	#For every matching build change here, that produces new installable
	#files, the vmops.spec file and the Debian control files must be
	#revised and tested.

	required_env = [
		"APISERVERLOG",
		"MSLOG",
		"PIDDIR",
		"CPPATH",
		"AGENTSIMULATORCLASSPATH",
		"SYSTEMJAVADIR",
		"USAGELOG",
	]
	for e in required_env:
		if e not in bld.env: raise Utils.WafError("configure required: new variable %s added"%e)

	builddir = bld.path.abspath(bld.env)
	distro = bld.env.DISTRO.lower()
	
	tomcatenviron = bld.env.MSENVIRON
	tomcatconf    = bld.env.MSCONF
	usageconf    = bld.env.USAGESYSCONFDIR
	webappdir = _join(tomcatenviron,'webapps','client')

	bld.env.append_value("JAVACFLAGS",['-g:source,lines,vars'])

	# build / install declarations of the daemonization utility - except for Windows
	if distro not in ['windows','mac']:
		bld(
			name='daemonize',
			features='cc cprogram',
			source='daemonize/daemonize.c',
			target='daemonize/vmops-daemonize')

	# build / install declarations of premium-deps and deps projects

	bld.install_files('${PREMIUMJAVADIR}','thirdparty/*.jar')
	bld.install_files('${JAVADIR}','deps/*.jar')

	# inner function to shorten declarations below

	def buildjar(proj,after=''):
		"""Compile an src/ tree of files, build manifest, make a jar
		proj: project name (directory name)
		after: space-separated list of "XXX_javac" tasks that must
		  be finished first because this task depends on them
		This is just a convenience to avoid copying and pasting
		"""
		#print after
		# we strip the classpath of jarfiles because those might still be written to
		# since we do jar and javac in parallel, and parallel javac's will attempt to
		# load up the "damaged" zip file while it is being built
		# Alex found this bug.
		bld(features='subst', name='%s_manifest'%proj,
		    source='%s/META-INF/MANIFEST.in'%proj, target='%s/META-INF/MANIFEST.MF'%proj)
		bld(features='javac', name='%s_javac'%proj,
		    srcdir='%s/src'%proj, compat='1.6', after=after)
		bld(features='jar', name='%s_jar'%proj,
		    basedir='%s/src'%proj, jarcreate = 'cfm',
		    jaropts = ['%s/META-INF/MANIFEST.MF'%_join(builddir,proj)],
		    destfile='vmops-%s.jar'%proj,
		    after="%s_javac %s_manifest"%(proj,proj))
		bld.install_files('${JAVADIR}','vmops-%s.jar'%proj)

	# build / install declarations of utils project
	proj = 'utils' ;	buildjar(proj)
	sccsinfo = _join(srcdir,"sccs-info")
	if _exists(sccsinfo): bld.install_files("${DOCDIR}","sccs-info")
	
	# build / install declarations of core project
	proj = 'core';		buildjar(proj,after='utils_javac')

	# build / install declarations of client-api project
	proj = 'client-api' ;	buildjar(proj,after='utils_javac')

	# build / install declarations of test project
	proj = 'test' ;		buildjar(proj,after='utils_javac')
	start_path = bld.path.find_dir("test/scripts")
	bld.install_files(_join('${LIBDIR}','vmops','test'),
		start_path.ant_glob("**",src=True,bld=False,dir=False,flat=True),
		postpone=False,cwd=start_path,relative_trick=True)
	bld.install_files(_join('${SHAREDSTATEDIR}','vmops','test'),'%s/metadata/*'%proj) # install metadata
	bld(features='subst', name='testprogram', source='%s/vmops-run-test.in'%proj, target='%s/vmops-run-test'%proj)
	bld.install_files('${BINDIR}','%s/vmops-run-test'%proj,chmod=0755) # install binary
	bld.install_files('${SYSCONFDIR}/vmops/%s'%proj,'%s/conf/*'%proj) # install config

	# build / install declarations of console project
	proj = 'console' ;	buildjar(proj)

	# build / install declarations of console-proxy project
	proj = 'console-proxy';	buildjar(proj,after='console_javac')
	start_path = bld.path.find_dir("console-proxy")
	patterns = 'css/** images/** js/** ui/**'
	bld.install_files("${CPLIBDIR}",
		start_path.ant_glob(patterns,src=True,bld=False,dir=False,flat=True),
		postpone=False,cwd=start_path,relative_trick=True)

	# HEADS UP: the construction of the console proxy zip (tgz file) is done below, when the agent scripts are done

	# build / install declarations of console-viewer project

	# build / install declarations of premium
	proj = 'premium' ;	buildjar(proj,after='utils_javac core_javac server_javac console-proxy_javac')

	# build / install declarations of server project
	proj = 'server' ;       buildjar(proj,after='utils_javac core_javac')
	bld.install_files(_join(webappdir,'WEB-INF'),'%s/web.xml'%proj) # install web.xml file

	# build / install declarations of agent project
	proj = 'agent' ;	buildjar(proj,after='utils_javac core_javac')
	start_path = bld.path.find_dir(proj)
	bld.install_files("${AGENTLIBDIR}",
		start_path.ant_glob("storagepatch/**",src=True,bld=False,dir=False,flat=True),
		postpone=False,cwd=start_path,relative_trick=True)
	for f in _glob(_join(proj,"conf","*")):
		if f.endswith("~") or _basename(f) == "override": continue
		f = dev_override(f)
		if f.endswith(".in"):
			source = f ; target = f[:-3]
			bld(features='subst', source=f, target=f[:-3])
		else:
			source = f ; target = f
		if not Options.options.PRESERVECONFIG:
			bld.install_files('${AGENTSYSCONFDIR}',target)
	bld(features='subst', source='%s/libexec/%s-runner.in'%(proj,proj),
		target='%s/libexec/%s-runner'%(proj,proj))
	bld.install_files("${LIBEXECDIR}",'%s/libexec/%s-runner'%(proj,proj),chmod=0755)
	bld(features='subst', source='%s/libexec/vmops-setup-%s.in'%(proj,proj),
                target='%s/libexec/vmops-setup-%s'%(proj,proj))
        bld.install_files("${LIBEXECDIR}",'%s/libexec/vmops-setup-%s'%(proj,proj),chmod=0755)


	for infile in _glob(_join(proj,"bin","*.in")):
		source = infile ; target = infile[:-3]
		bld(features='subst', source=source, target=target)
		bld.install_files("${BINDIR}",'%s'%(target),chmod=0755)

	# build / install declarations of agent-simulator project
	proj = 'agent-simulator' ;	buildjar(proj,after='core_javac agent_javac')

	if distro not in ['windows','mac']:
		proj = 'vnet'
		files = """vnetd/connection.c vnetd/select.c vnetd/timer.c vnetd/spinlock.c vnetd/skbuff.c
			vnetd/vnetd.c vnet-module/skb_util.c vnet-module/sxpr_util.c vnet-module/timer_util.c
			vnet-module/etherip.c vnet-module/vnet.c vnet-module/vnet_eval.c vnet-module/vnet_forward.c
			vnet-module/vif.c vnet-module/tunnel.c vnet-module/sa.c vnet-module/varp.c
			libxutil/allocate.c libxutil/enum.c libxutil/file_stream.c libxutil/hash_table.c
			libxutil/iostream.c libxutil/lexis.c libxutil/socket_stream.c libxutil/string_stream.c
			libxutil/sxpr.c libxutil/sxpr_parser.c libxutil/sys_net.c libxutil/sys_string.c libxutil/util.c"""
		files = [ "vnet/src/%s"%s.strip() for s in files.split() if s.strip() ]
		bld(
			name='vnetd',
			features='cc cprogram',
			source= files,
			includes="vnet/src/libxutil vnet/src/vnet-module vnet/src/vnetd",
			lib='dl pthread'.split(),
			target='vnet/vmops-vnetd',
			install_path="${SBINDIR}"
			)
		bld.install_as("${SBINDIR}/%s-vn"%APPNAME,   "%s/vn"%proj,   chmod=0755)
		obj = bld(features = 'py',name='vnetpy')
		obj.find_sources_in_dirs('%s/lib'%proj, exts=['.py'])
	
	
	# initscripts are installed below, in the code that does distro-specific support

	# build / install declarations of client UI project
	proj = 'ui'
	start_path = bld.path.find_dir("ui")
	patterns = '*.html *.ico content/** css/** images/** scripts/** test/**'
	bld.install_files(webappdir,
		start_path.ant_glob(patterns,src=True,bld=False,dir=False,flat=True),
		postpone=False,cwd=start_path,relative_trick=True)
	
	# build / install declarations of agent scripts project
	start_path = bld.path.find_dir("scripts")
	globs = ["**/*.sh","**/*.py","**/*.exp"]
	for glb in globs:
		bld.install_files("${AGENTLIBDIR}/scripts",
			start_path.ant_glob(glb,src=True,bld=False,dir=False,flat=True),
			postpone=False,cwd=start_path,relative_trick=True,chmod=0755)
	def m(x,mode):
		filez = start_path.ant_glob(x,src=True,bld=False,dir=False,flat=True)
		bld.install_files("${AGENTLIBDIR}/scripts",filez,postpone=False,
			cwd=start_path,relative_trick=True,chmod=mode)
	m("network/domr/id_rsa",0600)
	setownership(bld,"${AGENTLIBDIR}/scripts/network/domr/id_rsa",bld.env.MSUSER,"root")
	m("util/qemu-ifup",0755)
	m("vm/hypervisor/xenserver/patch/**",0755)
	m("vm/hypervisor/xenserver/patch/patch",0644)
	m("vm/hypervisor/kvm/patch/patch.tgz",0644)
	
		# in this subsection we construct the console-proxy.zip file out of the console proxy sources and jar
		# this file actually goes into the agent-scripts package so we do it here for clarity

	proj = 'console-proxy'
	
	patterns = '%s/css/** %s/images/** %s/js/** %s/ui/** %s/conf/** %s/scripts/*.sh'%(proj,proj,proj,proj,proj,proj)
	sources = " ".join( [ bld.path.ant_glob(x,src=True,bld=False,dir=False,flat=True) for x in patterns.split() ] )
	thirdparties = [ s.strip() for s in
	"""
	xmlrpc-client-3.1.3.jar
	xmlrpc-common-3.1.3.jar
	ws-commons-util-1.0.2.jar
	log4j-1.2.15.jar
	gson-1.3.jar
	apache-log4j-extras-1.0.jar
	commons-httpclient-3.1.jar
	commons-logging-1.1.1.jar
	commons-collections-3.2.1.jar
	commons-codec-1.4.jar
	commons-pool-1.4.jar
	libvirt-0.4.0.jar
	jna.jar
	cglib-nodep-2.2.jar
	""".split() ]
	sources += " " + " ".join( "thirdparty/%s"%s for s in thirdparties )
	artifacts = "vmops-console-proxy.jar vmops-console.jar vmops-agent.jar vmops-utils.jar vmops-core.jar"

	def tar_up(task):
		tgt = task.outputs[0].bldpath(task.env)
		if _exists(tgt): _unlink(tgt)
		z = zipfile.ZipFile(tgt,"w")
		for inp in task.inputs:
			if inp.id&3==Node.BUILD:
				src = inp.bldpath(task.env)
				srcname = "/".join(src.split("/")[1:])
			else:
				src = inp.srcpath(task.env)
				srcname = "/".join(src.split("/")[2:])
			if srcname.endswith('run.sh'): srcname = 'run.sh'
			z.write(src,srcname)
		z.close()
		return 0
	tgen = bld(
		rule   = tar_up,
		source = sources + " " + artifacts,
		target = 'console-proxy.zip',
		name   = 'console-proxy_zip',
		after  = 'console-proxy_jar console_jar agent_jar core_jar utils_jar',
	)
	process_after(tgen)

	bld.install_files("${AGENTLIBDIR}/scripts/vm/hypervisor/xenserver/patch", "console-proxy.zip")
	bld.install_files("${AGENTLIBDIR}/scripts/vm/hypervisor/kvm/patch", "console-proxy.zip")

	# management server and agent configuration files
	# client and premium might have something to install into configuration directories

	for proj in ['client','premium']:
		
		# 1. substitute and install generic tomcat config
		for infile in _glob(_join(proj,"tomcatconf","*.in")):
			source = infile ; target = infile[:-3]
			bld(features='subst', source=source, target=target)
			if not Options.options.PRESERVECONFIG:
				bld.install_files(tomcatconf,'%s'%(target))

		for infile in _glob(_join(proj,"bin","*.in")):
			source = infile ; target = infile[:-3]
			bld(features='subst', source=source, target=target)
			if not Options.options.PRESERVECONFIG:
				bld.install_files("${BINDIR}",'%s'%(target),chmod=0755)

	bld.install_files(tomcatconf,'client/tomcatconf/db.properties',chmod=0640) # special case permissions FIXME should also be group vmops

	# build / install declarations of usage
	proj = 'usage' ;	buildjar(proj,after='utils_javac core_javac server_javac premium_javac')
	for f in _glob(_join(proj,"conf","*")):
		if f.endswith("~") or _basename(f) == "override": continue
		f = dev_override(f)
		if f.endswith(".in"):
			source = f ; target = f[:-3]
			bld(features='subst', source=f, target=f[:-3])
		else:
			source = f ; target = f
		if not Options.options.PRESERVECONFIG:
			bld.install_files(usageconf,target)
	bld.install_files(usageconf,'client/tomcatconf/db.properties',chmod=0640) # special case permissions FIXME should also be group vmops
	bld(features='subst', source='%s/libexec/%s-runner.in'%(proj,proj), target='%s/libexec/%s-runner'%(proj,proj))
	bld.install_files("${LIBEXECDIR}",'%s/libexec/%s-runner'%(proj,proj),chmod=0755)

	for proj in ['client','premium','agent','vnet','usage']:

		# apply distro-specific config on top of the 'all' generic vmopsmanagement config
		distrospecificdirs=_glob(_join(proj,"distro",distro,"*"))
		for dsdir in distrospecificdirs:
			start_path = bld.srcnode.find_dir(dsdir)
			subpath,varname = _split(dsdir)
			dsdirwithvar = _join("${%s}"%varname)
			files = bld.srcnode.ant_glob('%s/**/*.in'%dsdir,src=True,bld=False,dir=False,flat=True)
			for f in Utils.to_list(files):
				source = f ; target = f[:-3]
				if stat and _chmod: mode = stat.S_IMODE(os.stat(source).st_mode)
				else: mode = None
				bld(features='subst', source=source, target=target, chmod=mode)
				instdir = _dirname(_join(dsdirwithvar,target[len(dsdir)+1:]))
				bld.install_files(instdir,target,chmod=mode)
			nontemplates = start_path.ant_glob('**',src=True,bld=False,dir=False,flat=True,excl="**/*.in\n"+Node.exclude_regs)
			if nontemplates:
				bld.install_files(dsdirwithvar, nontemplates, postpone=False, cwd=start_path, relative_trick=True)

	# build / install declarations of setup tool project
	proj = 'setup'
	# 1. install db data files
	for f in _glob(_join(proj,"db","*")):
		if f.endswith("~") or _basename(f) == "override": continue
		if os.path.isdir(f): continue
		f = dev_override(f)
		if f.endswith(".in"):
			source = f ; target = f[:-3]
			bld(features='subst', source=f, target=f[:-3])
		else:
			source = f ; target = f
		bld.install_files("${SETUPDATADIR}",target)

	for infile in _glob(_join(proj,"bin","*.in")):
		source = infile ; target = infile[:-3]
		bld(features='subst', source=source, target=target)
		bld.install_files("${BINDIR}",'%s'%(target),chmod=0755)

	# build / install declarations of management server (client) project
	proj = 'client'

	# 4. fedora is just a symlink, ubuntu is actually an initscript.  we do the symlinking here.
	if distro in "fedora centos":
		symlinks = [ ('${SYSCONFDIR}/rc.d/init.d/vmops-management','${SYSCONFDIR}/rc.d/init.d/tomcat6'), ]
		for lnk,dst in symlinks: bld.symlink_as(lnk,Utils.subst_vars(dst,bld.env))

	# 6. install context.xml file
	bld.install_files(_join(webappdir,'META-INF'),'%s/context.xml'%proj)

	# 7. make log and cache dirs (this actually runs first)
	if distro in 'windows mac': pass
	else:
		if Options.is_install:
			x = ("root",bld.env.MSUSER)
			directories = [
				("${MSLOGDIR}",0770,x),
				("${AGENTLOGDIR}",0770,x),
				("${USAGELOGDIR}",0770,x),
				("${LOCALSTATEDIR}/cache/${MSPATH}",0770,x),
				("${LOCALSTATEDIR}/cache/${MSPATH}/temp",0770,x),
				("${LOCALSTATEDIR}/cache/${MSPATH}/work",0770,x),
				("${SHAREDSTATEDIR}/${MSPATH}",0770,x),
				("${MSMNTDIR}",0770,x),
				("${MSCONF}/Catalina",0770,x),
				("${MSCONF}/Catalina/localhost",0770,x),
				("${MSCONF}/Catalina/localhost/client",0770,x),
				("${PIDDIR}",0755,("root","root")),
				("${LOCKDIR}",0755,("root","root")),
			]
			
			for a,mode,owner in directories:
				s = _subst_add_destdir(a,bld)
				Utils.pprint("GREEN","* creating directory %s"%s)
				mkdir_p(s)
				setownership(bld,a,owner[0],owner[1],mode)

		# 8. create environment symlinks
		symlinks = [
			(_join(tomcatenviron,'bin'), '${TOMCATHOME}/bin'),
			(_join(tomcatenviron,'lib'),  '${TOMCATHOME}/lib'),
			(_join(tomcatenviron,'logs'), "${MSLOGDIR}"),
			(_join(tomcatenviron,'temp'), '${LOCALSTATEDIR}/cache/${MSPATH}/temp'),
			(_join(tomcatenviron,'work'),'${LOCALSTATEDIR}/cache/${MSPATH}/work'),
			(_join(tomcatenviron,'conf'), '${SYSCONFDIR}/${MSPATH}'),
			("${AGENTLIBDIR}/css", '${CPLIBDIR}/css'),
			("${AGENTLIBDIR}/images", '${CPLIBDIR}/images'),
			("${AGENTLIBDIR}/js", '${CPLIBDIR}/js'),
			("${AGENTLIBDIR}/ui", '${CPLIBDIR}/ui'),
		]
		for lnk,dst in symlinks: bld.symlink_as(lnk,Utils.subst_vars(dst,bld.env))

@throws_command_errors
def rpm(context):
	buildnumber = _getbuildnumber()
	if buildnumber: buildnumber = ["--define","_build_number %s"%buildnumber]
	else: buildnumber = []

	if Options.options.PRERELEASE:
		if not buildnumber:
			raise Utils.WafError("Please specify a build number to go along with --prerelease")
		prerelease = ["--define","_prerelease %s"%Options.options.PRERELEASE]
	else: prerelease = []
		
	# FIXME wrap the source tarball in POSIX locking!
	if not Options.options.blddir: outputdir = _join(context.curdir,blddir,"rpmbuild")
	else:			   outputdir = _join(_abspath(Options.options.blddir),"rpmbuild")
	Utils.pprint("GREEN","Building RPMs")
	Scripting.dist(appname=APPNAME,version=VERSION)
	
	#if _isdir(outputdir): shutil.rmtree(outputdir)
	for a in ["RPMS/noarch","SRPMS","BUILD","SPECS","SOURCES"]: mkdir_p(_join(outputdir,a))
	tarball = "%s-%s.tar.%s"%(APPNAME,VERSION,Scripting.g_gz)
	checkdeps = lambda: c(["rpmbuild","--define","_topdir %s"%outputdir,"-tp",tarball])
	dorpm = lambda: c(["rpmbuild","--define","_topdir %s"%outputdir,"-ta",tarball]+buildnumber+prerelease)
	try: checkdeps()
	except Exception:
		Utils.pprint("YELLOW","Dependencies might be missing.  Trying to auto-install them...")
		installrpmdeps(context)
	dorpm()

@throws_command_errors
def deb(context):
	buildnumber = _getbuildnumber()
	if buildnumber: buildnumber = ["--set-envvar=BUILDNUMBER=%s"%buildnumber]
	else: buildnumber = []
	
	if Options.options.PRERELEASE:
		if not buildnumber:
			raise Utils.WafError("Please specify a build number to go along with --prerelease")
		# version/release numbers are read by dpkg-buildpackage from line 1 of debian/changelog
		# http://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-Version
		tempchangelog = """%s (%s-~%s%s) unstable; urgency=low

  * Automatic prerelease build

 -- Automated build system <noreply@vmops.com>  %s"""%(
			APPNAME,
			VERSION,
			_getbuildnumber(),
			Options.options.PRERELEASE,
			email.Utils.formatdate(time.time())
		)
	else:
		tempchangelog = None
	
	# FIXME wrap the source tarball in POSIX locking!
	if not Options.options.blddir: outputdir = _join(context.curdir,blddir,"debbuild")
	else:			   outputdir = _join(_abspath(Options.options.blddir),"debbuild")
	Utils.pprint("GREEN","Building DEBs")
	Scripting.dist(appname=APPNAME,version=VERSION)
	
	#if _isdir(outputdir): shutil.rmtree(outputdir)
	mkdir_p(outputdir)
	tarball = "%s-%s.tar.%s"%(APPNAME,VERSION,Scripting.g_gz)
	f = tarfile.open(tarball,'r:bz2')
	f.extractall(path=outputdir)
	srcdir = "%s/%s-%s"%(outputdir,APPNAME,VERSION)
	if tempchangelog:
		f = file(_join(srcdir,"debian","changelog"),"w")
		f.write(tempchangelog)
		f.flush()
		f.close()
	
	checkdeps = _in_srcdir(srcdir,lambda: c(["dpkg-checkbuilddeps"]))
	dodeb = _in_srcdir(srcdir,lambda: c(["debuild"]+buildnumber+["-us","-uc"]))
	try: checkdeps()
	except Exception:
		Utils.pprint("YELLOW","Dependencies might be missing.  Trying to auto-install them...")
		installdebdeps(context)
	dodeb()
	shutil.rmtree(srcdir)

def uninstallrpms(context):
	"""uninstalls any VMOps Cloud Stack RPMs on this system"""
	Utils.pprint("GREEN","Uninstalling any installed RPMs")
	cmd = "rpm -qa | grep vmops- | xargs -r sudo rpm -e"
	Utils.pprint("BLUE",cmd)
	system(cmd)

def uninstalldebs(context):
	"""uninstalls any VMOps Cloud Stack DEBs on this system"""
	Utils.pprint("GREEN","Uninstalling any installed DEBs")
	cmd = "dpkg -l 'vmops-*' | grep ^i | awk '{ print $2 } ' | xargs aptitude purge -y"
	Utils.pprint("BLUE",cmd)
	system(cmd)

def viewrpmdeps(context):
	"""shows all the necessary dependencies to build the RPM packages of the stack"""
	for dep in getrpmdeps(): print dep

def viewdebdeps(context):
	"""shows all the necessary dependencies to build the DEB packages of the stack"""
	for dep in getdebdeps(): print dep

@throws_command_errors
def installrpmdeps(context):
	"""installs all the necessary dependencies to build the RPM packages of the stack"""
	runnable = ["sudo","yum","install","-y"]+list(getrpmdeps())
	Utils.pprint("GREEN","Installing RPM build dependencies")
	Utils.pprint("BLUE"," ".join(runnable))
	_check_call(runnable)

@throws_command_errors
def installdebdeps(context):
	"""installs all the necessary dependencies to build the DEB packages of the stack"""
	runnable = ["sudo","aptitude","install","-y"]+list( [ x.split()[0] for x in getdebdeps() ] )
	Utils.pprint("GREEN","Installing DEB build dependencies")
	Utils.pprint("BLUE"," ".join(runnable))
	_check_call(runnable)

def dev_override(pathname):
	p,e = _split(pathname)
	overridden = _join(p,"override",e)
	if _exists(overridden): return overridden
	return pathname

@throws_command_errors
def deploydb(ctx,virttech=None):
	if not virttech:
		raise Utils.WafError('deploydb is temporarily out of service -- use deploydb_xenserver or deploydb_kvm until the templates.sql issue is sorted out')
	# FIXME Scripting.build(ctx) ?
	ctx = _get_context()
	
	dbhost = ctx.env.DBHOST
	dbuser = ctx.env.DBUSER
	dbpw   = ctx.env.DBPW
	dbdir  = ctx.env.DBDIR
	classpath = ctx.env.CLASSPATH

	serversetup = _join("setup","db","server-setup.xml")
	serversetup = _abspath(dev_override(serversetup))
	builddir = ctx.path.abspath(ctx.env)
	srcdir = ctx.path.abspath()
	configdir = _join(builddir,"client","tomcatconf")
	testconfdir = _join(srcdir,"test","conf")
	classpath = pathsep.join([testconfdir,classpath])
	if not _exists(configdir):
		raise Utils.WafError("Please build at least once to generate the db.properties configuration file")

	before = ""
	for f in ["create-database","create-schema"]:
		p = _join("setup","db",f+".sql")
		p = dev_override(p)
		before = before + file(p).read()
		Utils.pprint("GREEN","Reading database code from %s"%p)

	cmd = ["mysql","--user=%s"%dbuser,"-h",dbhost,"--password=%s"%dbpw]
	Utils.pprint("GREEN","Deploying database scripts to %s (user %s)"%(dbhost,dbuser))
	Utils.pprint("BLUE"," ".join(cmd))
	p = _Popen(cmd,stdin=PIPE,stdout=None,stderr=None)
	p.communicate(before)
	retcode = p.wait()
	if retcode: raise CalledProcessError(retcode,cmd)

	pwd = _getcwd()
	_chdir(configdir)
	cmd = ["java","-cp",classpath,] + ['-Dlog4j.configuration=log4j-stdout.properties'] + ["com.vmops.test.DatabaseConfig",serversetup]
	Utils.pprint("GREEN","Configuring database with com.vmops.test.DatabaseConfig in folder %s"%configdir)
	Utils.pprint("BLUE"," ".join( [ _trm(x,64) for x in cmd ]))
	_check_call(cmd,stdin=PIPE,stdout=None,stderr=None)
	_chdir(pwd)

	after = ""
	for f in ["templates.%s"%virttech,"create-index-fk"]:
		p = _join("setup","db",f+".sql")
		p = dev_override(p)
		after = after + file(p).read()
		Utils.pprint("GREEN","Reading database code from %s"%p)

	cmd = ["mysql","--user=%s"%dbuser,"-h",dbhost,"--password=%s"%dbpw]
	Utils.pprint("GREEN","Deploying post-configuration database scripts to %s (user %s)"%(dbhost,dbuser))
	Utils.pprint("BLUE"," ".join(cmd))
	p = _Popen(cmd,stdin=PIPE,stdout=None,stderr=None)
	p.communicate(after)
	retcode = p.wait()
	if retcode: raise CalledProcessError(retcode,cmd)
	
def deploydb_xenserver(ctx):
	"""re-deploys the database using the MySQL connection information and the XenServer templates.sql"""
	return deploydb(ctx,"xenserver")
def deploydb_kvm(ctx):
	"""re-deploys the database using the MySQL connection information and the KVM templates.sql"""
	return deploydb(ctx,"kvm")

def bindist(ctx):
	"""creates a binary distribution that, when unzipped in the root directory of a machine, deploys the entire stack"""
	ctx = _get_context()

	tarball = "%s-bindist-%s.tar.%s"%(APPNAME,VERSION,Scripting.g_gz)
	zf = _join(ctx.bldnode.abspath(),tarball)
	Options.options.destdir = _join(ctx.bldnode.abspath(),"bindist-destdir")
	Scripting.install(ctx)
	
	if _exists(zf): _unlink(zf)
	Utils.pprint("GREEN","Creating %s"%(zf))
	z = tarfile.open(zf,"w:bz2")
	_chdir(Options.options.destdir)
	z.add(".")
	z.close()

def run(args):
	"""runs the management server, compiling and installing files as needed"""
	conf = _get_context()

	runverbose = []
	if Options.options.RUNVERBOSE: runverbose = ['-verbose']
	if args == "debug":
		suspend = "n"
		if Options.options.DEBUGSUSPEND: suspend = "y"
		debugargs = [
			"-Xdebug","-Xrunjdwp:transport=dt_socket,address=%s,server=y,suspend=%s"%(
				Options.options.DEBUGPORT,suspend),
			"-ea"]
		Utils.pprint("GREEN","Starting Tomcat in debug mode")
	else:
		Utils.pprint("GREEN","Starting Tomcat in foreground mode")
		debugargs = []

	tomcatenviron	= conf.env.MSENVIRON
	options = runverbose + debugargs + [
		"-Dcatalina.base=" + tomcatenviron,
		"-Dcatalina.home=" + tomcatenviron,
		"-Djava.io.tmpdir="+_join(tomcatenviron,"temp"), ]

	cp = [conf.env.MSCONF]
	cp += _glob(_join(conf.env.MSENVIRON,'bin',"*.jar"))
	cp += _glob(_join(conf.env.MSENVIRON,'lib',"*.jar"))
	cp += [conf.env.PREMIUMCLASSPATH]
	cp += [conf.env.SYSTEMCLASSPATH]
	cp += [conf.env.DEPSCLASSPATH]
	cp += [conf.env.MSCLASSPATH]

	Scripting.install(conf)

	run_java("org.apache.catalina.startup.Bootstrap",cp,options,["start"])

def simulate_agent(args):
	"""runs the agent simulator, compiling and installing files as needed
	     - Any parameter specified after the simulate_agent is appended to
	       the java command line.  To inhibit waf from interpreting the
	       command-line options that you specify to the agent, put a --
	       (double-dash) between the waf simulate_agent and the options, like this:

                    python waf simulate_agent -- -z KY -p KY
"""
	
	# to get this to work smoothly from the configure onwards, you need to
	# create an override directory in java/agent/conf, then add an agent.properties
	# there, with the correct configuration that you desire
	# that is it -- you are now ready to simulate_agent
	conf = _get_context()
	args = sys.argv[sys.argv.index("simulate_agent")+1:]
	if '--' in args: args.remove('--')
	
	cp = [conf.env.AGENTSYSCONFDIR]
	cp += [conf.env.PREMIUMCLASSPATH]
	cp += [conf.env.SYSTEMCLASSPATH]
	cp += [conf.env.DEPSCLASSPATH]
	cp += [conf.env.AGENTSIMULATORCLASSPATH]

	Scripting.install(conf)

	run_java("com.vmops.agent.AgentSimulator",cp,arguments=args)

def debug(ctx):
	"""runs the management server in debug mode"""
	run("debug")
