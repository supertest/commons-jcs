@echo off

call prep.bat
  
:run
rem set DBUGPARM=-classic -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,address=5000,suspend=n
%JAVA_HOME%\bin\java %DBUGPARM% -ms10m -mx200m -classpath %CLASSPATH% "-Djava.security.policy=%CURDIR%/conf/cache.policy" com.mortbay.Jetty.Server %CURDIR%/conf/myjetty%1.xml

