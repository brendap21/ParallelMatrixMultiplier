@echo off
REM Batch to compile and run the test harness (Windows cmd)
pushd "%~dp0"
echo Compiling client sources...
javac -d client/bin -cp "client/lib/shared.jar" client/src/client\*.java
echo Compiling server sources...
javac -d server/bin -cp "server/lib/shared.jar" server/src/server\*.java

echo Starting server in background (RMI registry created by server)...
start "matrix-server" /B cmd /c "java -cp "server/bin;server/lib/shared.jar" server.ServerApp 127.0.0.1"

echo Waiting 1s for server to start...
timeout /t 1 /nobreak >nul

echo Running test harness (client)...
java -cp "client/bin;client/lib/shared.jar" client.TestHarness

echo Done.
popd
