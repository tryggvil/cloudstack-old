%define __os_install_post %{nil}
%global debug_package %{nil}

# DISABLE the post-percentinstall java repacking and line number stripping
# we need to find a way to just disable the java repacking and line number stripping, but not the autodeps

%define _ver 1.9.10
%define _rel 1

Name:      vmops
Summary:   VMOps
Version:   %{_ver}
#http://fedoraproject.org/wiki/PackageNamingGuidelines#Pre-Release_packages
%if "%{?_prerelease}" != ""
Release:   0.%{_build_number}%{_prerelease}
%else
Release:   %{_rel}
%endif
License:   GPLv3+ with exceptions or CSL 1.1
Vendor:    VMOps Inc. <sqa@vmops.com>
Packager:  Manuel Amador (Rudd-O) <manuel@vmops.com>
Group:     System Environment/Libraries
# FIXME do groups for every single one of the subpackages
Source0:   %{name}-%{_ver}.tar.bz2
BuildRoot: %{_tmppath}/%{name}-%{_ver}-%{release}-build

BuildRequires: java-1.6.0-openjdk-devel
BuildRequires: tomcat6
BuildRequires: ws-commons-util
#BuildRequires: commons-codec
BuildRequires: commons-dbcp
BuildRequires: commons-collections
BuildRequires: commons-httpclient
BuildRequires: jpackage-utils
BuildRequires: gcc
BuildRequires: glibc-devel

%description
This is the VMOps Cloud Stack, a highly-scalable elastic, open source,
intelligent cloud implementation.

%package utils
Summary:   VMOps utility library
Requires: java >= 1.6.0
Group:     System Environment/Libraries
%description utils
The VMOps utility libraries provide a set of Java classes used
in the VMOps Cloud Stack.

%package client-ui
Summary:   VMOps management server UI
Requires: %{name}-client
Group:     System Environment/Libraries
%description client-ui
The VMOps management server is the central point of coordination,
management, and intelligence in the VMOps Cloud Stack.  This package
is a requirement of the %{name}-client package, which installs the
VMOps management server.

%package server
Summary:   VMOps server library
Requires: java >= 1.6.0
Requires: %{name}-utils = %{version}-%{release}, %{name}-core = %{version}-%{release}, %{name}-deps = %{version}-%{release}, tomcat6-servlet-2.5-api
Group:     System Environment/Libraries
%description server
The VMOps server libraries provide a set of Java classes used
in the VMOps Cloud Stack.

%package vnet
Summary:   VMOps-specific virtual network daemon
Requires: python
Requires: %{name}-daemonize = %{version}-%{release}
Requires: net-tools
Requires: bridge-utils
Group:     System Environment/Daemons
%description vnet
The VMOps virtual network daemon manages virtual networks used in the
VMOps Cloud Stack.

%package agent-scripts
Summary:   VMOps agent scripts
# FIXME nuke the archdependency
Requires: python
Requires: bash
Requires: bzip2
Requires: gzip
Requires: unzip
Group:     System Environment/Libraries
%description agent-scripts
The VMOps agent is in charge of managing shared computing resources in
a VMOps Cloud Stack-powered cloud.  Install this package if this computer
will participate in your cloud -- this is a requirement for the VMOps
agent.

%package deps
Summary:   VMOps library dependencies
Requires: java >= 1.6.0
Group:     System Environment/Libraries
%description deps
This package contains a number of third-party dependencies
not shipped by distributions, required to run the VMOps
Cloud Stack.

%package daemonize
Summary:   VMOps daemonization utility
Group:     System Environment/Libraries
%description daemonize
This package contains a program that daemonizes the specified
process.  The VMOps Cloud Stack uses this to start the agent
as a service.

%package premium-deps
Summary:   VMOps premium library dependencies
Requires: java >= 1.6.0
Provides: vmops-deps = %{version}-%{release}
Group:     System Environment/Libraries
%description premium-deps
This package contains the certified software components required to run
the premium edition of the VMOps Cloud Stack.

%package core
Summary:   VMOps core library
Requires: java >= 1.6.0
Requires: %{name}-utils = %{version}-%{release}, %{name}-deps = %{version}-%{release}
Group:     System Environment/Libraries
%description core
The VMOps core libraries provide a set of Java classes used
in the VMOps Cloud Stack.

%package console
Summary:   VMOps console
Requires: java >= 1.6.0
Group:     System Environment/Libraries
%description console
The VMOps console package contains a set of shared classes
used by the remote management console system in the VMOps
Cloud Stack.

%package test
Summary:   VMOps test suite
Requires: java >= 1.6.0
Requires: %{name}-utils = %{version}-%{release}, %{name}-deps = %{version}-%{release}, wget
Group:     System Environment/Libraries
%description test
The VMOps test package contains a suite of automated tests
that the very much appreciated QA team at VMOps constantly
uses to help increase the quality of the Cloud Stack.

%package console-proxy
Summary:   VMOps console proxy
Requires: java >= 1.6.0
Requires: %{name}-deps = %{version}-%{release}, %{name}-console = %{version}-%{release}
Group:     System Environment/Libraries
%description console-proxy
The VMOps console proxy contains an implementation of an
AJAX-based remote console viewer for virtual machines created
within a VMOps Cloud Stack-powered cloud.

%package client
Summary:   VMOps client
# If GCJ is present, a setPerformanceSomething method fails to load Catalina
Conflicts: java-1.5.0-gcj-devel
Requires: java >= 1.6.0
Requires: %{name}-deps = %{version}-%{release}, %{name}-utils = %{version}-%{release}, %{name}-server = %{version}-%{release}
Requires: %{name}-client-ui = %{version}-%{release}
Requires: %{name}-setup = %{version}-%{release}
# reqs the agent-scripts package because of xenserver within the management server
Requires: %{name}-agent-scripts = %{version}-%{release}
# for consoleproxy
# Requires: %{name}-agent
Requires: tomcat6
Requires: ws-commons-util
#Requires: commons-codec
Requires: commons-dbcp
Requires: commons-collections
Requires: commons-httpclient
Requires: jpackage-utils
Requires: sudo
Requires: /sbin/service
Requires: /sbin/chkconfig
Requires: /usr/bin/ssh-keygen
Group:     System Environment/Libraries
%description client
The VMOps management server is the central point of coordination,
management, and intelligence in the VMOps Cloud Stack.  This package
is required for the management server to work.

%package setup
Summary:   VMOps setup tools
Requires: java >= 1.6.0
Requires: python
Requires: mysql
Requires: %{name}-utils = %{version}-%{release}
Requires: %{name}-server = %{version}-%{release}
Requires: %{name}-deps = %{version}-%{release}
Group:     System Environment/Libraries
%description setup
The VMOps setup tools let you set up your Management Server and Usage Server.

%package client-api
Summary:   VMOps client API library
Requires: java >= 1.6.0
Requires: %{name}-utils = %{version}-%{release}
Group:     System Environment/Libraries
%description client-api
The VMOps client API provides a generic client API for the
VMOps Cloud Stack components and third parties to use.

%package agent
Summary:   VMOps agent
Requires: java >= 1.6.0
Requires: %{name}-utils = %{version}-%{release}, %{name}-core = %{version}-%{release}, %{name}-deps = %{version}-%{release}
Requires: %{name}-agent-scripts = %{version}-%{release}
# Requires: %{name}-console = %{version}-%{release}
# Requires: %{name}-console-proxy = %{version}-%{release}
Requires: %{name}-vnet = %{version}-%{release}
Requires: commons-httpclient
#Requires: commons-codec
Requires: commons-collections
Requires: commons-pool
Requires: commons-dbcp
Requires: jakarta-commons-logging
Requires: libvirt
Requires: /usr/sbin/libvirtd
Requires: jpackage-utils
Requires: %{name}-daemonize
Requires: /sbin/service
Requires: /sbin/chkconfig
Requires: /usr/bin/qemu-nbd
Requires: /usr/bin/qemu-kvm
Requires: /bin/cgcreate
Requires: /sbin/mount.nfs
Requires: /usr/bin/uuidgen
Group:     System Environment/Libraries
%description agent
The VMOps agent is in charge of managing shared computing resources in
a VMOps Cloud Stack-powered cloud.  Install this package if this computer
will participate in your cloud.

%package premium
Summary:   VMOps premium components
Requires: java >= 1.6.0
Requires: %{name}-utils = %{version}-%{release}, %{name}-core = %{version}-%{release}, %{name}-deps = %{version}-%{release}, %{name}-server = %{version}-%{release}
# , %{name}-console-proxy
Requires: %{name}-premium-deps
License:   CSL 1.1
Group:     System Environment/Libraries
%description premium
The VMOps premium components expand the range of features on your cloud stack.

%package usage
Summary:   VMOps usage monitor
Requires: java >= 1.6.0
Requires: %{name}-utils = %{version}-%{release}, %{name}-core = %{version}-%{release}, %{name}-deps = %{version}-%{release}, %{name}-server = %{version}-%{release}, %{name}-premium = %{version}-%{release}, %{name}-daemonize = %{version}-%{release}
Requires: %{name}-setup = %{version}-%{release}
License:   CSL 1.1
Group:     System Environment/Libraries
%description usage
The VMOps usage monitor provides usage accounting across the entire cloud for
cloud operators to charge based on usage parameters.

%prep

%setup -q -n %{name}-%{_ver}

%build

# this fixes the /usr/com bug on centos5
%define _localstatedir /var
%define _sharedstatedir /var/lib
./waf configure --prefix=%{_prefix} --libdir=%{_libdir} --bindir=%{_bindir} --javadir=%{_javadir} --sharedstatedir=%{_sharedstatedir} --localstatedir=%{_localstatedir} --sysconfdir=%{_sysconfdir} --mandir=%{_mandir} --docdir=%{_docdir}/%{name}-%{version} --with-tomcat=%{_datadir}/tomcat6 --tomcat-user=%{name} --build-number=%{?_build_number}
./waf showconfig
./waf build

%install

[ ${RPM_BUILD_ROOT} != "/" ] && rm -rf ${RPM_BUILD_ROOT}
./waf install --destdir=$RPM_BUILD_ROOT --nochown

%clean

[ ${RPM_BUILD_ROOT} != "/" ] && rm -rf ${RPM_BUILD_ROOT}


%preun client
if [ "$1" == "0" ] ; then
    /sbin/chkconfig --del vmops-management  > /dev/null 2>&1 || true
    /sbin/service vmops-management stop > /dev/null 2>&1 || true
fi

%pre client
id vmops > /dev/null 2>&1 || /usr/sbin/useradd -M -c "VMOps management server" \
     -r -s /bin/sh -d %{_sharedstatedir}/%{name}/management %{name}|| true
# user harcoded here, also hardcoded on wscript

%post client
if [ "$1" == "1" ] ; then
    /sbin/chkconfig --add vmops-management > /dev/null 2>&1 || true
    /sbin/chkconfig --level 345 vmops-management on > /dev/null 2>&1 || true
else
    /sbin/service vmops-management condrestart >/dev/null 2>&1 || true
fi
test -f %{_sharedstatedir}/%{name}/management/.ssh/id_rsa || su - %{name} -c 'yes "" 2>/dev/null | ssh-keygen -t rsa -q -N ""' < /dev/null



%preun usage
if [ "$1" == "0" ] ; then
    /sbin/chkconfig --del vmops-usage  > /dev/null 2>&1 || true
    /sbin/service vmops-usage stop > /dev/null 2>&1 || true
fi

%pre usage
id vmops > /dev/null 2>&1 || /usr/sbin/useradd -M -c "VMOps management server" \
     -r -s /bin/sh -d %{_sharedstatedir}/%{name}/management %{name}|| true
# user harcoded here, also hardcoded on wscript

%post usage
if [ "$1" == "1" ] ; then
    /sbin/chkconfig --add vmops-usage > /dev/null 2>&1 || true
    /sbin/chkconfig --level 345 vmops-usage on > /dev/null 2>&1 || true
else
    /sbin/service vmops-usage condrestart >/dev/null 2>&1 || true
fi


%pre agent-scripts
id vmops > /dev/null 2>&1 || /usr/sbin/useradd -M -c "VMOps management server" \
     -r -s /bin/sh -d %{_sharedstatedir}/%{name}/management %{name}|| true


%preun agent
if [ "$1" == "0" ] ; then
    /sbin/chkconfig --del vmops-agent  > /dev/null 2>&1 || true
    /sbin/service vmops-agent stop > /dev/null 2>&1 || true
fi

%post agent
if [ "$1" == "1" ] ; then
    /sbin/chkconfig --add vmops-agent > /dev/null 2>&1 || true
    /sbin/chkconfig --level 345 vmops-agent on > /dev/null 2>&1 || true
else
    /sbin/service vmops-agent condrestart >/dev/null 2>&1 || true
fi

%preun vnet
if [ "$1" == "0" ] ; then
    /sbin/chkconfig --del vmops-vnetd > /dev/null 2>&1 || true
    /sbin/service vmops-vnetd stop > /dev/null 2>&1 || true
fi

%post vnet
if [ "$1" == "1" ] ; then
    /sbin/chkconfig --add vmops-vnetd > /dev/null 2>&1 || true
    /sbin/chkconfig --level 345 vmops-vnetd on > /dev/null 2>&1 || true
else
    /sbin/service vmops-vnetd condrestart >/dev/null 2>&1 || true
fi


%files utils
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-utils.jar
%doc %{_docdir}/%{name}-%{version}/sccs-info

%files client-ui
%defattr(0644,root,root,0755)
%{_datadir}/%{name}/management/webapps/client/*

%files server
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-server.jar

%files agent-scripts
%defattr(-,root,root,-)
%{_libdir}/%{name}/agent/scripts/*
%attr(0600,vmops,root) %{_libdir}/%{name}/agent/scripts/network/domr/id_rsa

%files daemonize
%defattr(-,root,root,-)
%{_bindir}/%{name}-daemonize

%files deps
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-commons-codec-1.4.jar
%{_javadir}/%{name}-apache-log4j-extras-1.0.jar
%{_javadir}/%{name}-backport-util-concurrent-3.0.jar
%{_javadir}/%{name}-ehcache.jar
%{_javadir}/%{name}-email.jar
%{_javadir}/%{name}-gson-1.3.jar
%{_javadir}/%{name}-httpcore-4.0.jar
%{_javadir}/%{name}-jna.jar
%{_javadir}/%{name}-junit-4.8.1.jar
%{_javadir}/%{name}-libvirt-0.4.0.jar
%{_javadir}/%{name}-log4j.jar
%{_javadir}/%{name}-trilead-ssh2-build213.jar
%{_javadir}/%{name}-cglib.jar
%{_javadir}/%{name}-mysql-connector-java-5.1.7-bin.jar
%{_javadir}/%{name}-xenserver-5.5.0-1.jar
%{_javadir}/%{name}-xmlrpc-common-3.*.jar
%{_javadir}/%{name}-xmlrpc-client-3.*.jar

%files premium-deps
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-premium/*.jar

%files core
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-core.jar

%files console
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-console.jar

%files test
%defattr(0644,root,root,0755)
%attr(755,root,root) %{_bindir}/%{name}-run-test
%{_javadir}/%{name}-test.jar
%{_sharedstatedir}/%{name}/test/*
%{_libdir}/%{name}/test/*
%{_sysconfdir}/%{name}/test/*

%files console-proxy
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-console-proxy.jar
%{_libdir}/%{name}/console-proxy/*

%files vnet
%defattr(0755,root,root,0755)
%{_sbindir}/%{name}-vnetd
%{_sbindir}/%{name}-vn
%{_initrddir}/%{name}-vnetd
%{_prefix}/lib*/python*/site-packages/vmops*

%files setup
%attr(0755,root,root) %{_bindir}/%{name}-setup-databases
%{_datadir}/%{name}/setup
# FIXME need to go in its own setup dir!

%files client
%defattr(0644,root,root,0755)
%{_sysconfdir}/%{name}/management/catalina.policy
%{_sysconfdir}/%{name}/management/catalina.properties
%{_sysconfdir}/%{name}/management/commands.properties
%{_sysconfdir}/%{name}/management/components.xml
%{_sysconfdir}/%{name}/management/context.xml
%config(noreplace) %attr(640,root,%{name}) %{_sysconfdir}/%{name}/management/db.properties
%{_sysconfdir}/%{name}/management/environment.properties
%{_sysconfdir}/%{name}/management/ehcache.xml
%config(noreplace) %{_sysconfdir}/%{name}/management/log4j-vmops.xml
%{_sysconfdir}/%{name}/management/logging.properties
%{_sysconfdir}/%{name}/management/server.xml
%config(noreplace) %{_sysconfdir}/%{name}/management/tomcat6.conf
%{_sysconfdir}/%{name}/management/classpath.conf
%{_sysconfdir}/%{name}/management/tomcat-users.xml
%{_sysconfdir}/%{name}/management/web.xml
%dir %attr(770,root,%{name}) %{_sysconfdir}/%{name}/management/Catalina
%dir %attr(770,root,%{name}) %{_sysconfdir}/%{name}/management/Catalina/localhost
%dir %attr(770,root,%{name}) %{_sysconfdir}/%{name}/management/Catalina/localhost/client
%config %{_sysconfdir}/sysconfig/%{name}-management
%{_initrddir}/%{name}-management
%dir %{_datadir}/%{name}/management
%{_datadir}/%{name}/management/bin
%{_datadir}/%{name}/management/conf
%{_datadir}/%{name}/management/lib
%{_datadir}/%{name}/management/logs
%{_datadir}/%{name}/management/temp
%{_datadir}/%{name}/management/work
%dir %attr(770,root,%{name}) %{_sharedstatedir}/%{name}/mnt
%dir %attr(770,%{name},%{name}) %{_sharedstatedir}/%{name}/management
%dir %attr(770,root,%{name}) %{_localstatedir}/cache/%{name}/management
%dir %attr(770,root,%{name}) %{_localstatedir}/cache/%{name}/management/work
%dir %attr(770,root,%{name}) %{_localstatedir}/cache/%{name}/management/temp
%dir %attr(770,root,%{name}) %{_localstatedir}/log/%{name}/management
%dir %attr(770,root,%{name}) %{_localstatedir}/log/%{name}/agent

%files client-api
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-client-api.jar

%files agent
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-agent.jar
%{_javadir}/%{name}-agent-simulator.jar
%config(noreplace) %{_sysconfdir}/%{name}/agent/agent.properties
%config %{_sysconfdir}/%{name}/agent/developer.properties.template
%config %{_sysconfdir}/%{name}/agent/environment.properties
%config(noreplace) %{_sysconfdir}/%{name}/agent/log4j-vmops.xml
%attr(0755,root,root) %{_initrddir}/%{name}-agent
%attr(0755,root,root) %{_libexecdir}/agent-runner
%{_libdir}/%{name}/agent/css
%{_libdir}/%{name}/agent/ui
%{_libdir}/%{name}/agent/js
%{_libdir}/%{name}/agent/images
%{_bindir}/%{name}-setup-agent
%dir %attr(770,root,root) %{_localstatedir}/log/%{name}/agent

%files premium
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-premium.jar
%{_sysconfdir}/%{name}/management/commands-ext.properties
%{_sysconfdir}/%{name}/management/components-premium.xml

%files usage
%defattr(0644,root,root,0755)
%{_javadir}/%{name}-usage.jar
%attr(0755,root,root) %{_initrddir}/%{name}-usage
%attr(0755,root,root) %{_libexecdir}/usage-runner
%dir %attr(770,root,%{name}) %{_localstatedir}/log/%{name}/usage
%{_sysconfdir}/%{name}/usage/usage-components.xml
%config(noreplace) %{_sysconfdir}/%{name}/usage/log4j-vmops_usage.xml
%config(noreplace) %attr(640,root,%{name}) %{_sysconfdir}/%{name}/usage/db.properties



%changelog
* Wed Apr 28 2010 Manuel Amador (Rudd-O) <manuel@vmops.com> 1.9.10
- FOSS release

%changelog
* Mon Apr 05 2010 Manuel Amador (Rudd-O) <manuel@vmops.com> 1.9.8
- RC3 branched

* Wed Feb 17 2010 Manuel Amador (Rudd-O) <manuel@vmops.com> 1.9.7
- First initial broken-up release

