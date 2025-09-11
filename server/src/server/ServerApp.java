package server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerApp {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java server.ServerApp <mi-ip>");
            System.exit(1);
        }
        String myIp = args[0];
        System.setProperty("java.rmi.server.hostname", myIp);

        try {
            MatrixMultiplierImpl impl = new MatrixMultiplierImpl();
            Registry reg = LocateRegistry.createRegistry(1099);
            reg.rebind("MatrixService", impl);
            System.out.printf("Servidor RMI listo en %s:1099%n", myIp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
