@echo off
REM Batch to compile and run the test harness (Windows cmd)
pushd "%~dp0"
set SERVER_IP=%1
if "%SERVER_IP%"=="" set SERVER_IP=127.0.0.1

echo Compiling client sources...
javac -d client/bin -cp "client/lib/shared.jar" client/src/client\*.java
echo Compiling server sources...
javac -d server/bin -cp "server/lib/shared.jar" server/src/server\*.java

echo Starting server in background (RMI registry created by server) binding to %SERVER_IP%...
start "matrix-server" /B cmd /c "java -cp "server/bin;server/lib/shared.jar" server.ServerApp %SERVER_IP%"

echo Waiting 1s for server to start...
timeout /t 1 /nobreak >nul

echo Running test harness (client)...
REM Client harness connects to 127.0.0.1 by default; if you want it to point to the server IP, edit TestHarness or pass argument.
java -cp "client/bin;client/lib/shared.jar" client.TestHarness

echo Done.
popd
