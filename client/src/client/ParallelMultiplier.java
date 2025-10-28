package client;

import shared.MatrixMultiplier;

import java.rmi.Naming;
import java.util.List;
import java.util.concurrent.*;
import java.util.ArrayList;

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

    private final int chunkSize; // filas por sub-llamada (1 = máximo detalle). Default 4.
    public ParallelMultiplier() { this(4); }
    public ParallelMultiplier(int chunkSize) { this.chunkSize = Math.max(1, chunkSize); }

    /**
     * Multiplica A x B de forma distribuida entre servidores y posible procesamiento local.
     *
     * - servers: lista de servidores remotos disponibles (puede ser vacía)
     * - totalWorkers: número total de workers lógicos (barras que verá el usuario)
     * - callback: para actualizaciones de progreso
     * - includeLocal: si true, el cliente toma una porción local además de los servidores remotos
     * - serverThreadCount: número de hilos que solicitamos al servidor; si <=0 cada servidor decide (p.ej. #cores)
     *
     * Retorna la matriz resultante C.
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
            // indicamos con null stub que el último endpoint corresponde al cliente local
            stubs.add(null);
            endpointsInfo.add(null);
        }

        final int endpointCount = stubs.size();
        if (endpointCount == 0) throw new IllegalStateException("No hay endpoints disponibles.");

        // Ajustar totalWorkers: no puede ser > n
        int n = A.length;
        if (totalWorkers <= 0) totalWorkers = Math.min(n, endpointCount);

        int rowsPerWorker = (n + totalWorkers - 1) / totalWorkers; // ceil
        int totalAssignedWorkers = totalWorkers;

        // NEW: limitar concurrencia por endpoint (evita múltiples llamadas RMI simultáneas al mismo servidor)
        final List<Semaphore> endpointSemaphores = new ArrayList<>(endpointCount);
        for (int i = 0; i < endpointCount; i++) {
            // permitimos 1 llamada concurrente por endpoint por defecto (evita sobrecarga)
            endpointSemaphores.add(new Semaphore(1));
        }

        // NEW: si serverThreadCount no fue especificado (<=0) y hay múltiples endpoints,
        // elegir un valor conservador para evitar oversubscription en servidores remotos.
        final int effectiveServerThreadCount;
        if (serverThreadCount <= 0 && endpointCount > 1) {
            // Forzar 1 hilo por servidor por defecto cuando hay varios endpoints.
            effectiveServerThreadCount = 1;
        } else {
            effectiveServerThreadCount = serverThreadCount;
        }

        // NEW: crear un ConcurrentMultiplier reutilizable para procesamiento local (si hay)
        final ConcurrentMultiplier localConcurrent;
        if (hasLocal) {
            // repartir cores entre endpoints para evitar usar todos en el cliente local si hay múltiples endpoints
            int availableCores = Runtime.getRuntime().availableProcessors();
            int coresPerEndpoint = Math.max(1, availableCores / Math.max(1, endpointCount));
            localConcurrent = new ConcurrentMultiplier(coresPerEndpoint);
        } else {
            localConcurrent = null;
        }

        // NEW: limitar tamaño del pool del cliente para no lanzar demasiados workers paralelos
        int maxParallelRequestsPerEndpoint = 2; // factor empírico; evita generar más hilos que endpoints * factor
        int execPoolSize = Math.min(totalAssignedWorkers, Math.max(1, endpointCount * maxParallelRequestsPerEndpoint));
        ExecutorService exec = Executors.newFixedThreadPool(execPoolSize);
        CountDownLatch finishLatch = new CountDownLatch(totalAssignedWorkers);

        final int[][] C = new int[n][B[0].length];

        final Object globalLock = new Object();
        final int[] globalDone = {0};

        // SUGERENCIA: Forzar chunk grande para RMI si hay pocos endpoints
        final int minRemoteChunk = 200; // puedes ajustar a 100, 200, 500 según pruebas

        for (int w = 0; w < totalAssignedWorkers; w++) {
            final int workerIndex = w;
            final int startRow = workerIndex * rowsPerWorker;
            final int endRow = Math.min(n, (workerIndex + 1) * rowsPerWorker);
            final int totalForWorker = Math.max(0, endRow - startRow);

            // Asignar endpoint en round-robin (entre endpoints disponibles)
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

                    // ADAPTIVE CHUNK: para endpoints remotos usar bloques más grandes (menos RMI)
                    int effectiveChunk = chunkSize;
                    if (stub != null) {
                        // Forzar chunk grande para RMI
                        effectiveChunk = Math.max(chunkSize, minRemoteChunk);
                    }

                    if (stub == null) {
                        // Procesamiento LOCAL (cliente) usando ConcurrentMultiplier reutilizable
                        for (int r = startRow; r < endRow; r += effectiveChunk) {
                            int chunkStart = r;
                            int chunkEnd = Math.min(endRow, r + effectiveChunk);
                            int rows = chunkEnd - chunkStart;
                            int[][] A_block = new int[rows][A[0].length];
                            for (int i = 0; i < rows; i++) {
                                System.arraycopy(A[chunkStart + i], 0, A_block[i], 0, A[0].length);
                            }
                            // ya calculamos coresPerEndpoint al crear localConcurrent
                            int localThreads = (effectiveServerThreadCount > 0) ? effectiveServerThreadCount : Runtime.getRuntime().availableProcessors();
                            int[][] blockResult = localConcurrent.multiplyBlock(A_block, B, localThreads);
                            for (int i = 0; i < blockResult.length; i++) {
                                int destRow = chunkStart + i;
                                System.arraycopy(blockResult[i], 0, C[destRow], 0, blockResult[i].length);
                            }

                            int rowsCopied = rows;
                            int doneForThisWorker = Math.min(totalForWorker, (r - startRow) + rowsCopied);
                            int globalNow;
                            synchronized (globalLock) {
                                globalDone[0] += rowsCopied;
                                globalNow = globalDone[0];
                            }
                            if (callback != null) callback.onChunkCompleted(workerIndex, endpointIndex,
                                    doneForThisWorker, totalForWorker, globalNow, n);
                        }
                    } else {
                        // Procesamiento REMOTO: enviamos A_block y pedimos multiplyBlock
                        for (int r = startRow; r < endRow; r += effectiveChunk) {
                            int chunkStart = r;
                            int chunkEnd = Math.min(endRow, r + effectiveChunk);

                            int rows = chunkEnd - chunkStart;
                            int[][] A_block = new int[rows][A[0].length];
                            for (int i = 0; i < rows; i++) {
                                System.arraycopy(A[chunkStart + i], 0, A_block[i], 0, A[0].length);
                            }

                            int[][] chunkResult;
                            // Limitamos concurrencia al endpoint (evita saturarlo con múltiples requests simultáneas)
                            Semaphore sem = endpointSemaphores.get(endpointIndex);
                            sem.acquireUninterruptibly();
                            try {
                                try {
                                    chunkResult = stub.multiplyBlock(A_block, B, chunkStart, effectiveServerThreadCount);
                                } catch (Exception e) {
                                    try {
                                        chunkResult = stub.multiplyBlock(A_block, B, chunkStart, effectiveServerThreadCount);
                                    } catch (Exception e2) {
                                        throw new RuntimeException("Error RMI con servidor " + endpointIndex + " : " + e2.getMessage(), e2);
                                    }
                                }
                            } finally {
                                sem.release();
                            }

                            int rowsCopied = chunkEnd - chunkStart;
                            for (int i = 0; i < rowsCopied; i++) {
                                int destRow = chunkStart + i;
                                System.arraycopy(chunkResult[i], 0, C[destRow], 0, chunkResult[i].length);
                            }

                            int doneForThisWorker = Math.min(totalForWorker, (r - startRow) + rowsCopied);
                            int globalNow;
                            synchronized (globalLock) {
                                globalDone[0] += rowsCopied;
                                globalNow = globalDone[0];
                            }

                            if (callback != null) {
                                callback.onChunkCompleted(workerIndex, endpointIndex,
                                        doneForThisWorker, totalForWorker,
                                        globalNow, n);
                            }
                        }
                    }

                    if (callback != null) callback.onWorkerFinished(workerIndex, endpointIndex);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (callback != null) callback.onWorkerFinished(workerIndex, endpointIndex);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // esperar finalización
        finishLatch.await(1, TimeUnit.HOURS);
        exec.shutdownNow();

        return C;
    }
}
