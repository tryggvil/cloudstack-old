@java -Xmx512M -cp "%~dp0\src;%~dp0\bin;C:/vmops/tools/gwt-windows-1.5.2/gwt-user.jar;C:/vmops/tools/gwt-windows-1.5.2/gwt-dev-windows.jar;C:/vmops/tools/gwt-windows-1.5.2/gwt-servlet.jar;C:/vmops/main/java/thirdparty/gxt.jar;C:/vmops/main/java/thirdparty/ehcache-1.5.0.jar;C:/vmops/main/java/thirdparty/backport-util-concurrent-3.0.jar;C:/vmops/main/java/build/deploy/developer/server/conf" com.google.gwt.dev.GWTShell -out "%~dp0\www" %* com.vmops.StartClient/StartClient.html