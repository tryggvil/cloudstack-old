#! /usr/bin/env python
# -*- coding: utf-8 -*-

# the following two variables are used by the target "waf dist"
# if you change 'em here, you need to change it also in cloud.spec, add a %changelog entry there, and add an entry in debian/changelog
VERSION = '2.0.0'
APPNAME = 'cloud'

import new
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
# 2. add the RPM package that contains the jarfile into the cloud.spec file
# 3. (the jars specified here will be looked up at runtime with build-classpath, on linux installs)
systemjars = {
	'common':
	(
		"commons-collections.jar",
		# "commons-daemon.jar",
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
#- in the cloud-deps package
#- in the cloud-premium package

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
	('javax.servlet.http.Cookie',"tomcat6-servlet-2.5-api","libservlet2.5-java","(if on Windows,did you set TOMCAT_HOME to point to your Tomcat setup?)"),
	('org.apache.naming.resources.Constants',"tomcat6-lib","libtomcat6-java","(if on Windows,did you set TOMCAT_HOME to point to your Tomcat setup?)"),
]

# things not to include in the source tarball
# exclude by file name or by _glob (wildcard matching)
for _globber in [
	["dist",    # does not belong in the source tarball
	"system",  # for windows
	#"tools",  # not needed FIXME we need ant!
	"override",  # not needed
	"eclipse", # only there to please eclipse
	"repomanagement",  # internal management stuff
	"client-api",  # obsolete
	"cloud-gate",  # not compiled and packaged yet
	"target",],  # eclipse workdir
	_glob("./*.disabledblahxml"),
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

def c(cmdlist,cwd=None):
	# Run a command with _check_call, pretty-printing the cmd list
	Utils.pprint("BLUE"," ".join(cmdlist))
	return _check_call(cmdlist,cwd=cwd)

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
Build.BuildContext.subst_add_destdir = staticmethod(_subst_add_destdir)

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

Build.BuildContext.process_after = staticmethod(process_after)

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

def _install_files_filtered(self,destdir,listoffiles,**kwargs):
	if "cwd" in kwargs: cwd = kwargs["cwd"]
	else: cwd = self.path
	if isinstance(listoffiles,str) and '**' in listoffiles:
		listoffiles = cwd.ant_glob(listoffiles,flat=True)
	elif isinstance(listoffiles,str) and '*' in listoffiles:
		gl=cwd.abspath() + os.sep + listoffiles
		listoffiles=_glob(gl)
	listoffiles = Utils.to_list(listoffiles)[:]
	listoffiles = [ x for x in listoffiles if not ( x.endswith("~") or x == "override" or "%soverride"%os.sep in x ) ]
	for n,f in enumerate(listoffiles):
		f = os.path.abspath(f)
		f = dev_override(f)
		if f.endswith(".in"):
			source = f ; target = f[:-3]
			self(features='subst', source=source[len(self.path.abspath())+1:], target=target[len(self.path.abspath())+1:])
		else:
			source = f ; target = f
		listoffiles[n] = target[len(cwd.abspath())+1:]
	ret = self.install_files(destdir,listoffiles,**kwargs)
	return ret
Build.BuildContext.install_files_filtered = _install_files_filtered

def _setownership(ctx,path,owner,group,mode=None):
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
		except KeyError,e: raise Utils.WafError("If installing as root, please either create a %s user or use the --nochown parameter of waf install to install the files as root"%owner)
		try: gid = grp.getgrnam(group).gr_gid
		except KeyError,e: raise Utils.WafError("If installing as root, please either create a %s group or use the --nochown parameter of waf install to install the files as root"%group)
		
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
Build.BuildContext.setownership = _setownership


# This is where the real deal starts

def dist_hook():
	# Clean the GARBAGE that clogs our repo to the tune of 300 MB
	# so downloaders won't have to cry every time they download a "source"
	# package over 90 MB in size
	[ shutil.rmtree(f) for f in _glob(_join("thirdparty","*")) if _isdir(f) ]
	[ shutil.rmtree(_join("tools",f)) for f in [
		"bin",
		"meld",
		"misc",
		"tomcat",
		"pdfdoclet",
		_join("ant","apache-ant-1.8.0","docs"),
	] if _exists(_join("tools",f)) ]
	
	if Options.options.OSS:
		[ shutil.rmtree(f) for f in "premium usage thirdparty console-proxy-premium".split() if _exists(f) ]
		[ shutil.rmtree(f) for f in [ _join("build","premium"), "repomanagement" ] if _exists(f) ]
		
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
	
	distopts = optparse.OptionGroup(opt.parser,'dist options')
	opt.add_option_group(distopts)
	distopts.add_option('--oss', # add javadir to the group that contains bindir
		help = 'Only include open source components',
		action = 'store_true',
		default = False,
		dest = 'OSS')
	

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
			conf.env.MSUSER = APPNAME
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
		conf.env.PREMIUMJAVADIR = _join(conf.env.JAVADIR,"%s-premium"%APPNAME)
		conf.env.SYSTEMJAVADIR = "/usr/share/java"
	
	in_javadir = lambda name: _join(conf.env.JAVADIR,_basename(name)) # $PREFIX/share/java
	in_system_javadir = lambda name: _join(conf.env.SYSTEMJAVADIR,name) # /usr/share/java
	in_premiumjavadir = lambda name: _join(conf.env.PREMIUMJAVADIR,name) # $PREFIX/share/java/cloud-premium

	conf.check_message_1('Building classpaths')
	
	# == Here we build the run-time classpaths ==
	
	# The system classpath points to JARs we expect the user has installed using distro packages
	# not used for Windows and Mac (except for servlet-api.jar) because we install them from the thirdparty/ directory
	sysjars = list(systemjars['common'])
	if conf.env.DISTRO in systemjars.keys(): sysjars = sysjars + list(systemjars[conf.env.DISTRO])
	conf.env.SYSTEMJARS = " ".join(sysjars) # used by build-classpath in the initscripts
	conf.env.SYSTEMCLASSPATH = pathsep.join([ in_system_javadir(x) for x in sysjars ]) # used for compile, waf run and simulate_agent
	
	# the deps classpath points to JARs that are installed in the cloud-deps package
	# these will install on Tomcat6's lib/ directory on Windows and Mac
	depsclasspath = [ in_javadir(_basename(x)) for x in _glob(_join(conf.srcdir,"deps","*.jar")) ]
	conf.env.DEPSCLASSPATH = pathsep.join(depsclasspath)

	# the MS classpath points to JARs required to run the management server
	msclasspath = [ in_javadir("%s-%s.jar"%(APPNAME,x)) for x in "utils core server premium core-extras".split() ]
	conf.env.MSCLASSPATH = pathsep.join(msclasspath)

	# the agent and simulator classpaths point to JARs required to run these two applications
	agentclasspath = [ in_javadir("%s-%s.jar"%(APPNAME,x)) for x in "utils core server premium agent console console-proxy core-extras".split() ]
	conf.env.AGENTCLASSPATH = pathsep.join(agentclasspath)
	conf.env.AGENTSIMULATORCLASSPATH = pathsep.join(agentclasspath+[in_javadir("%s-agent-simulator.jar"%APPNAME)])

	usageclasspath = [ in_javadir("%s-%s.jar"%(APPNAME,x)) for x in "utils core server premium usage core-extras".split() ]
	conf.env.USAGECLASSPATH = pathsep.join(usageclasspath)

	# the premium classpath points to JARs that are installed in the cloud-premium-deps package
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

def list_targets(ctx):
        """return the amount of targets"""

        bld = Build.BuildContext()
        proj = Environment.Environment(Options.lockfile)
        bld.load_dirs(proj['srcdir'], proj['blddir'])
        bld.load_envs()

        bld.add_subdirs([os.path.split(Utils.g_module.root_path)[0]])

        names = set([])
        for x in bld.all_task_gen:
                try:
                        names.add(x.name or x.target)
                except AttributeError:
                        pass

        lst = list(names)
        lst.sort()
        for name in lst:
                print(name)


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
	Scripting.dist()
	
	#if _isdir(outputdir): shutil.rmtree(outputdir)
	for a in ["RPMS/noarch","SRPMS","BUILD","SPECS","SOURCES"]: mkdir_p(_join(outputdir,a))
	tarball = "%s-%s.tar.%s"%(APPNAME,VERSION,Scripting.g_gz)
	checkdeps = lambda: c(["rpmbuild","--define","_topdir %s"%outputdir,"-tp",tarball])
	dorpm = lambda: c(["rpmbuild","--define","_topdir %s"%outputdir,"-ta",tarball]+buildnumber+prerelease)
	try: checkdeps()
	except (CalledProcessError,OSError),e:
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

 -- Automated build system <noreply@cloud.com>  %s"""%(
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
	Scripting.dist()

	
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
	
	checkdeps = lambda: c(["dpkg-checkbuilddeps"],srcdir)
	dodeb = lambda: c(["debuild"]+buildnumber+["-us","-uc"],srcdir)
	try: checkdeps()
	except (CalledProcessError,OSError),e:
		Utils.pprint("YELLOW","Dependencies might be missing.  Trying to auto-install them...")
		installdebdeps(context)
	dodeb()
	shutil.rmtree(srcdir)

def uninstallrpms(context):
	"""uninstalls any Cloud Stack RPMs on this system"""
	Utils.pprint("GREEN","Uninstalling any installed RPMs")
	cmd = "rpm -qa | grep %s- | xargs -r sudo rpm -e"%APPNAME
	Utils.pprint("BLUE",cmd)
	system(cmd)

def uninstalldebs(context):
	"""uninstalls any Cloud Stack DEBs on this system"""
	Utils.pprint("GREEN","Uninstalling any installed DEBs")
	cmd = "dpkg -l '%s-*' | grep ^i | awk '{ print $2 } ' | xargs aptitude purge -y"%APPNAME
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
	jars = _glob(_join(ctx.path.abspath(ctx.env), "*.jar"))
	classpath = ctx.env.CLASSPATH + pathsep.join(jars)

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

	cmd = ["java","-cp",classpath,] + ['-Dlog4j.configuration=log4j-stdout.properties'] + ["com.cloud.test.DatabaseConfig",serversetup]
	Utils.pprint("GREEN","Configuring database with com.cloud.test.DatabaseConfig in folder %s"%configdir)
	Utils.pprint("BLUE"," ".join( [ _trm(x,64) for x in cmd ]))
	_check_call(cmd,stdin=PIPE,stdout=None,stderr=None,cwd=configdir)

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
	cwd = _getcwd()
	_chdir(Options.options.destdir)
	z.add(".")
	z.close()
	_chdir(cwd)

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

	options = runverbose + debugargs + [
		"-Dcatalina.base=" + conf.env.MSENVIRON,
		"-Dcatalina.home=" + conf.env.MSENVIRON,
		"-Djava.io.tmpdir="+_join(conf.env.MSENVIRON,"temp"), ]

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

	run_java("com.cloud.agent.AgentSimulator",cp,arguments=args)

def debug(ctx):
	"""runs the management server in debug mode"""
	run("debug")
