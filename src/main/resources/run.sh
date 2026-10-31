#!/bin/bash

# Keep RSS low on hosts that bill by memory (e.g. Railway). Override with JAVA_OPTS / JAVA_TOOL_OPTIONS.
: "${JAVA_OPTS:=-Xms32m -Xmx192m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:+ExitOnOutOfMemoryError}"
: "${JAVA_TOOL_OPTIONS:=}"

exec java \
  $JAVA_TOOL_OPTIONS \
  $JAVA_OPTS \
  -Dfile.encoding=UTF-8 \
  -Dio.netty.allocator.numHeapArenas=1 \
  -Dio.netty.allocator.numDirectArenas=1 \
  -Dlogback.configurationFile=logback.xml \
  -jar wowchat.jar "${1:-wowchat.conf}"
