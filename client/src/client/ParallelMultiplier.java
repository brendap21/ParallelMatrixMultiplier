package client;

import shared.MatrixMultiplier;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.concurrent.*;

/**
 * ParallelMultiplier distribuye A×B entre múltiples servidores RMI usando multiplySegment(...).
 * 
 * Cada servidor procesa un bloque de filas de A y devuelve ese trozo de C.
 */
public class ParallelMultiplier {
    private final List<MatrixMultiplier> services;

    /**
     * Construye el distribuidor apuntando a los hosts RMI dados.
     * @param hosts lista de IPs o nombres de host donde corre ServerApp
     * @throws Exception si no se puede conectar a alguno
     */
    public ParallelMultiplier(List<String> hosts) throws Exception {
        // usamos CopyOnWriteArrayList por seguridad en concurrencia
        services = new CopyOnWriteArrayList<>();
        for (String host : hosts) {
            Registry reg = LocateRegistry.getRegistry(host, 1099);
            MatrixMultiplier stub = (MatrixMultiplier) reg.lookup("MatrixService");
            services.add(stub);
        }
    }

    /**
     * Multiplica A×B pidiendo a cada servidor un segmento de filas.
     * @param A matriz de tamaño n×p
     * @param B matriz de tamaño p×m
     * @return C = A×B (n×m)
     * @throws Exception si falla alguna llamada RMI
     */
    public int[][] multiplyRemote(int[][] A, int[][] B) throws Exception {
        int n = A.length;
        int m = B[0].length;
        int[][] C = new int[n][m];

        int servers = services.size();
        // cuántas filas procesa cada servidor (último cubre el sobrante)
        int chunk = (n + servers - 1) / servers;

        // pool local para coordinar llamadas
        ExecutorService exec = Executors.newFixedThreadPool(servers);
        CountDownLatch latch = new CountDownLatch(servers);

        for (int s = 0; s < servers; s++) {
            final int startRow = s * chunk;
            final int endRow   = Math.min(n, (s + 1) * chunk);
            final MatrixMultiplier stub = services.get(s);

            exec.submit(() -> {
                try {
                    // dispara la traza en el servidor
                    int[][] part = stub.multiplySegment(A, B, startRow, endRow);
                    // copia el bloque de vuelta a C
                    for (int i = startRow; i < endRow; i++) {
                        System.arraycopy(part[i - startRow], 0, C[i], 0, m);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        exec.shutdown();
        // esperamos a todos los bloques (o 1 hora máximo)
        if (!latch.await(1, TimeUnit.HOURS)) {
            throw new RuntimeException("Timeout esperando servidores remotos");
        }

        return C;
    }
}
