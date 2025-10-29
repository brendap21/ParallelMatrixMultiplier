package client;

import shared.MatrixMultiplier;

import java.rmi.Naming;
import java.util.List;
import java.util.concurrent.*;
import java.util.ArrayList;
import javax.swing.SwingUtilities;

/**
 * ParallelMultiplier (distribuido + procesamiento concurrente local)
 *
 * - Reparte filas entre workers (workers = número total de hilos que el usuario introduzca)
 * - Cada worker está asignado a un endpoint: uno de los servidores remotos o el "local"
 * - Si el endpoint es remoto, llama a multiplyBlock(A_block,B,rowOffset, serverThreadCount)
 * - Si el endpoint es local, usa ConcurrentMultiplier.multiplyBlock para procesar su segmento localmente
 *
 * - El callback ProgressCallback se llama por cada chunk completado para actualizar la UI.
 */
public class ParallelMultiplier {
    private final ClientLogger logger;

    public ParallelMultiplier(String clientId) {
        this.logger = new ClientLogger(clientId);
    }

    public static class ServerInfo {
        public final String host;
        public final int port;
        public final String serviceName;
        public ServerInfo(String host, int port, String serviceName) {
            this.host = host; this.port = port; this.serviceName = serviceName;
        }
        public String lookupUrl() {
            return String.format("//%s:%d/%s", host, port, serviceName);
        }
    }

    public interface ProgressCallback {
        void onChunkCompleted(int workerIndex, int endpointIndex, int rowsCompletedForWorker,
                              int rowsTotalForWorker, int globalCompleted, int globalTotal);
        void onWorkerStarted(int workerIndex, int endpointIndex);
        void onWorkerFinished(int workerIndex, int endpointIndex);
    }

    // Elimina chunkSize
    public ParallelMultiplier() {
        this.logger = null;
    }

    /**
     * Multiplica A x B de forma distribuida entre servidores y posible procesamiento local.
     * Ahora cada worker procesa su bloque completo de filas en una sola llamada.
     */
    public int[][] multiplyDistributed(int[][] A, int[][] B,
                                       List<ServerInfo> servers,
                                       int totalWorkers,
                                       ProgressCallback callback,
                                       boolean includeLocal,
                                       int serverThreadCount) throws Exception {
        if ((servers == null || servers.isEmpty()) && !includeLocal) {
            throw new IllegalArgumentException("Se requiere al menos 1 servidor remoto o incluir procesamiento local.");
        }
        // preparar la lista de endpoints: todos los servidores y, si includeLocal, reservamos el último endpoint para local
        final List<MatrixMultiplier> stubs = new ArrayList<>();
        final List<ServerInfo> endpointsInfo = new ArrayList<>();
        if (servers != null) {
            for (ServerInfo si : servers) {
                MatrixMultiplier stub = (MatrixMultiplier) Naming.lookup(si.lookupUrl());
                stubs.add(stub);
                endpointsInfo.add(si);
            }
        }

        final boolean hasLocal = includeLocal;
        if (hasLocal) {
            stubs.add(null);
            endpointsInfo.add(null);
        }

        final int endpointCount = stubs.size();
        if (endpointCount == 0) throw new IllegalStateException("No hay endpoints disponibles.");

        int n = A.length;
        if (totalWorkers <= 0) totalWorkers = Math.min(n, endpointCount);

        // Compute effective server thread count and a sensible parallelism per endpoint.
        final int effectiveServerThreadCount = (serverThreadCount <= 0) ? 0 : serverThreadCount;
        // default parallel requests per endpoint (controls how many simultaneous RMI calls we allow)
        int maxParallelRequestsPerEndpoint = 2;
        if (effectiveServerThreadCount > 0) {
            // allow a few concurrent requests without wildly oversubscribing the server
            maxParallelRequestsPerEndpoint = Math.max(1, Math.min(4, effectiveServerThreadCount / 2));
        }

        // Limit the total number of worker tasks so we don't cause excessive repeated serialization of B.
        int totalAssignedWorkers = Math.min(totalWorkers, endpointCount * maxParallelRequestsPerEndpoint);
        if (totalAssignedWorkers <= 0) totalAssignedWorkers = 1;

        int rowsPerWorker = (n + totalAssignedWorkers - 1) / totalAssignedWorkers; // ceil

        final List<Semaphore> endpointSemaphores = new ArrayList<>(endpointCount);
        for (int i = 0; i < endpointCount; i++) {
            // allow up to maxParallelRequestsPerEndpoint simultaneous requests to each endpoint
            endpointSemaphores.add(new Semaphore(maxParallelRequestsPerEndpoint));
        }

        final ConcurrentMultiplier localConcurrent;
        if (hasLocal) {
            int availableCores = Runtime.getRuntime().availableProcessors();
            int coresPerEndpoint = Math.max(1, availableCores / Math.max(1, endpointCount));
            localConcurrent = new ConcurrentMultiplier(coresPerEndpoint);
        } else {
            localConcurrent = null;
        }

        // Try to prepare B once on each remote endpoint to avoid re-sending B for every block.
        final boolean[] endpointPrepared = new boolean[endpointCount];
        for (int i = 0; i < endpointCount; i++) {
            MatrixMultiplier s = stubs.get(i);
            if (s == null) { endpointPrepared[i] = false; continue; }
            try {
                s.prepareB(B);
                endpointPrepared[i] = true;
            } catch (Exception ex) {
                // If prepare fails, we'll fall back to sending B with each block
                endpointPrepared[i] = false;
            }
        }

    int execPoolSize = Math.min(totalAssignedWorkers, Math.max(1, endpointCount * maxParallelRequestsPerEndpoint));
        ExecutorService exec = Executors.newFixedThreadPool(execPoolSize);
        CountDownLatch finishLatch = new CountDownLatch(totalAssignedWorkers);

        final int[][] C = new int[n][B[0].length];

        final Object globalLock = new Object();
        final int[] globalDone = {0};

        for (int w = 0; w < totalAssignedWorkers; w++) {
            final int workerIndex = w;
            final int startRow = workerIndex * rowsPerWorker;
            final int endRow = Math.min(n, (workerIndex + 1) * rowsPerWorker);
            final int totalForWorker = Math.max(0, endRow - startRow);

            final int endpointIndex = workerIndex % endpointCount;
            final MatrixMultiplier stub = stubs.get(endpointIndex); // null => local
            final ServerInfo endpointInfo = endpointsInfo.get(endpointIndex);

            exec.submit(() -> {
                try {
                    if (totalForWorker <= 0) {
                        if (callback != null) callback.onWorkerStarted(workerIndex, endpointIndex);
                        if (callback != null) callback.onWorkerFinished(workerIndex, endpointIndex);
                        return;
                    }

                    if (callback != null) callback.onWorkerStarted(workerIndex, endpointIndex);

                    int[][] A_block = new int[totalForWorker][A[0].length];
                    for (int i = 0; i < totalForWorker; i++) {
                        System.arraycopy(A[startRow + i], 0, A_block[i], 0, A[0].length);
                    }

                    int[][] blockResult;
                    if (stub == null) {
                        int localThreads = (effectiveServerThreadCount > 0) ? effectiveServerThreadCount : Runtime.getRuntime().availableProcessors();
                        // LOGS EN LA GUI DEL CLIENTE PARA PROCESAMIENTO LOCAL
                        long hiloStart = System.currentTimeMillis();
                        if (callback != null) {
                            SwingUtilities.invokeLater(() -> {
                                // Log de inicio
                                client.AppGUI gui = client.AppGUI.getInstanceIfExists();
                                if (gui != null) gui.appendProgress(String.format("Hilo #%d INICIA [Filas: %d-%d]\n", workerIndex+1, startRow+1, endRow));
                            });
                        }
                        blockResult = new int[totalForWorker][B[0].length];
                        for (int i = 0; i < totalForWorker; i++) {
                            if (i % 10 == 0 && callback != null) {
                                final int fila = i;
                                SwingUtilities.invokeLater(() -> {
                                    client.AppGUI gui = client.AppGUI.getInstanceIfExists();
                                    if (gui != null) gui.appendProgress(String.format("Hilo #%d fila %d procesando...\n", workerIndex+1, startRow+fila+1));
                                });
                            }
                            for (int j = 0; j < B[0].length; j++) {
                                int s = 0;
                                for (int k = 0; k < B.length; k++) s += A_block[i][k] * B[k][j];
                                blockResult[i][j] = s;
                            }
                        }
                        if (callback != null) {
                            long hiloEnd = System.currentTimeMillis();
                            SwingUtilities.invokeLater(() -> {
                                client.AppGUI gui = client.AppGUI.getInstanceIfExists();
                                if (gui != null) gui.appendSuccess(String.format("Hilo #%d TERMINA [Filas: %d-%d] - Tiempo: %.3fs\n", workerIndex+1, startRow+1, endRow, (hiloEnd-hiloStart)/1000.0));
                            });
                        }
                    } else {
                        Semaphore sem = endpointSemaphores.get(endpointIndex);
                        sem.acquireUninterruptibly();
                        try {
                            if (endpointPrepared[endpointIndex]) {
                                // use prepared B on server to avoid re-sending B
                                blockResult = stub.multiplyBlockPrepared(A_block, startRow, effectiveServerThreadCount);
                            } else {
                                // fallback: send B with each call
                                blockResult = stub.multiplyBlock(A_block, B, startRow, effectiveServerThreadCount);
                            }
                        } finally {
                            sem.release();
                        }
                    }

                    for (int i = 0; i < blockResult.length; i++) {
                        int destRow = startRow + i;
                        System.arraycopy(blockResult[i], 0, C[destRow], 0, blockResult[i].length);
                    }

                    int globalNow;
                    synchronized (globalLock) {
                        globalDone[0] += totalForWorker;
                        globalNow = globalDone[0];
                    }
                    if (callback != null) callback.onChunkCompleted(workerIndex, endpointIndex,
                            totalForWorker, totalForWorker, globalNow, n);

                    if (callback != null) callback.onWorkerFinished(workerIndex, endpointIndex);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (callback != null) callback.onWorkerFinished(workerIndex, endpointIndex);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

    finishLatch.await(1, TimeUnit.HOURS);
    exec.shutdown();

        return C;
    }
}
