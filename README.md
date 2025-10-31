PARA COMPILAR Y USAR SECUENCIAL Y CONCURRENTE

1. Ingresar a la carpeta del cliente
2. Compilar todos los archivos

		javac -d bin -cp lib\shared.jar src\client\*.java
 
3. Ejecutar la interfaz grafica

		java -cp bin;lib\shared.jar client.AppGUI

PARA COMPILAR Y USAR MODO PARALELO:

1. En el cliente:

		javac -d shared/bin shared/src/shared/*.java
		jar cf shared/lib/shared.jar -C shared/bin .
		copy "shared\lib\shared.jar" "server\lib\shared.jar"
		copy "shared\lib\shared.jar" "client\lib\shared.jar"

2. En el Servidor: 

		javac -d shared\bin shared\src\shared\*.java
		javac -cp "shared\bin;server\lib\shared.jar" -d server\bin server\src\server\*.java
		javac -cp "shared\bin;client\lib\shared.jar" -d client\bin client\src\client\*.java
		jar cf shared\lib\shared.jar -C shared\bin .
		javac -cp "shared\lib\shared.jar" -d server\bin server\src\server\*.java
	
3. En el Cliente:
	
		javac -cp "client/lib/shared.jar" -d client/bin client/src/client/*.java client/src/client/*.java

4. Ejecutar servidores:

		java -cp "server/bin;server/lib/shared.jar" server.ServerApp 192.168.100.217

*OPCIONAL* Testear la conexion desde el cliente:
		powershell
		Test-NetConnection -ComputerName 192.168.100.217 -Port 1099

Ejecutar la GUI del cliente: 
		java -cp "client/bin;client/lib/shared.jar" client.AppGUI




