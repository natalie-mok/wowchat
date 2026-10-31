chcp 65001
if "%JAVA_OPTS%"=="" set JAVA_OPTS=-Xms32m -Xmx192m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:+ExitOnOutOfMemoryError
java %JAVA_TOOL_OPTIONS% %JAVA_OPTS% -Dfile.encoding=UTF-8 -Dio.netty.allocator.numHeapArenas=1 -Dio.netty.allocator.numDirectArenas=1 -Dlogback.configurationFile=logback.xml -jar wowchat.jar wowchat.conf
pause
