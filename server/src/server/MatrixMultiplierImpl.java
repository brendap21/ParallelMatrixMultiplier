package server;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import shared.MatrixMultiplier;

/**
 * Implementación RMI que incluye:
 * - multiply (secuencial)
 * - multiplyConcurrent (con ForkJoin, para todo A x B)
 * - multiplySegment (devuelve subsegmento calculado secuencialmente)
 * - multiplyConcurrentSegment (el servidor calcula su segmento usando ForkJoinPool)
 * - multiplyBlock (NUEVO): recibe A_block (solo las filas necesarias) y lo procesa en paralelo internamente
 */
public class MatrixMultiplierImpl extends UnicastRemoteObject implements MatrixMultiplier {
    private static final String SERVER_ID = System.getProperty("server.id", "Server");
    private final ServerLogger logger;
    private final AtomicInteger processedRows = new AtomicInteger(0);
    private int totalRows;
    
    protected MatrixMultiplierImpl() throws RemoteException { 
        super(); 
        this.logger = new ServerLogger(SERVER_ID);
        logger.info("Servidor iniciado y listo para procesar multiplicaciones de matrices");
    }
    
    private void resetProgress(int totalRows) {
        this.totalRows = totalRows;
        this.processedRows.set(0);
    }
    
    private void updateProgress(int rowsProcessed) {
        int currentProgress = processedRows.addAndGet(rowsProcessed);
        logProgress("Multiplicación de matriz", currentProgress, totalRows);
    }
    
    private void logProgress(String operation, int current, int total) {
        logger.progress(operation, current, total);
    }

    // NEW: pool compartido para evitar creación/destrucción por cada llamada multiplyBlock/multiplyConcurrent
    private final ForkJoinPool sharedPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    // Optional cached B uploaded by client to avoid re-sending large B each block
    private volatile int[][] preparedB = null;

    @Override
    public synchronized void prepareB(int[][] B) throws RemoteException {
        // store reference (RMI delivers a copy), replacement is atomic due to synchronized
        this.preparedB = B;
    }

    @Override
    public synchronized void clearPreparedB() throws RemoteException {
        this.preparedB = null;
    }

    @Override
    public int[][] multiply(int[][] A, int[][] B)
            throws RemoteException {
        int n = A.length, p = B[0].length, m = B.length;
        int[][] C = new int[n][p];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < p; j++)
                for (int k = 0; k < m; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }

    @Override
    public int[][] multiplyConcurrent(int[][] A, int[][] B, int threadCount)
            throws RemoteException {
        int n = A.length, p = B[0].length, m = B.length;
        int[][] C = new int[n][p];
        int useThreads = (threadCount <= 0) ? Runtime.getRuntime().availableProcessors() : threadCount;

        if (threadCount <= 0) {
            // usar pool compartido
            int threshold = Math.max(1, n / (useThreads * 2));
            sharedPool.invoke(new MatrixMultiplyTask(A, B, C, 0, n, threshold));
        } else {
            // pool temporal con tamaño solicitado
            ForkJoinPool pool = new ForkJoinPool(useThreads);
            int threshold = Math.max(1, n / (useThreads * 2));
            pool.invoke(new MatrixMultiplyTask(A, B, C, 0, n, threshold));
            pool.shutdown();
        }
        return C;
    }

    // Clase interna para Fork/Join sobre matrices completas
    private class MatrixMultiplyTask extends RecursiveAction {
        private final int[][] A, B, C;
        private final int rowStart, rowEnd, threshold;
        private final String taskId;
        
        MatrixMultiplyTask(int[][] A, int[][] B, int[][] C, int rowStart, int rowEnd, int threshold) {
            this.A = A; this.B = B; this.C = C;
            this.rowStart = rowStart; this.rowEnd = rowEnd; this.threshold = threshold;
            this.taskId = String.format("MT-%s-%d-%d", Thread.currentThread().getName(), rowStart, rowEnd);
        }
        
        @Override
        protected void compute() {
            if (rowEnd - rowStart <= threshold) {
                logger.info(String.format("Tarea %s: Iniciando multiplicación de filas %d-%d", taskId, rowStart, rowEnd));
                int p = B[0].length, m = B.length;
                int lastProgress = 0;
                
                for (int i = rowStart; i < rowEnd; i++) {
                    for (int j = 0; j < p; j++) {
                        int s = 0;
                        for (int k = 0; k < m; k++) {
                            s += A[i][k] * B[k][j];
                        }
                        C[i][j] += s;
                    }
                    
                    // Reportar progreso cada 20%
                    int progress = (i - rowStart + 1) * 100 / (rowEnd - rowStart);
                    if (progress >= lastProgress + 20) {
                        logger.progress(String.format("Tarea %s", taskId),
                            i - rowStart + 1, rowEnd - rowStart);
                        lastProgress = progress;
                    }
                }
                
                logger.success(String.format("Tarea %s: Completó multiplicación de filas %d-%d", 
                    taskId, rowStart, rowEnd));
            } else {
                int mid = (rowStart + rowEnd) / 2;
                invokeAll(
                    new MatrixMultiplyTask(A, B, C, rowStart, mid, threshold),
                    new MatrixMultiplyTask(A, B, C, mid, rowEnd, threshold)
                );
            }
        }
    }

    @Override
    public int[][] multiplySegment(int[][] A, int[][] B,
                                   int rowStart, int rowEnd)
            throws RemoteException {
        int p = B[0].length, m = B.length;
        int[][] Cseg = new int[rowEnd - rowStart][p];
        for (int i = rowStart; i < rowEnd; i++) {
            for (int j = 0; j < p; j++) {
                for (int k = 0; k < m; k++) {
                    Cseg[i - rowStart][j] += A[i][k] * B[k][j];
                }
            }
        }
        return Cseg;
    }

    @Override
    public int[][] multiplyConcurrentSegment(int[][] A, int[][] B, int rowStart, int rowEnd, int threadCount)
            throws RemoteException {
        // Valida límites
        if (rowStart < 0) rowStart = 0;
        if (rowEnd > A.length) rowEnd = A.length;
        if (rowStart >= rowEnd) return new int[0][0];

        int p = B[0].length;
        int rows = rowEnd - rowStart;
        int[][] Cseg = new int[rows][p];

        // Crear una matriz temporal Clocal que representa las filas globales[rowStart..rowEnd)
        int[][] Clocal = new int[A.length][p]; // grande, pero solo se llenarán las filas necesarias
        int useThreads = (threadCount <= 0) ? Runtime.getRuntime().availableProcessors() : threadCount;

        if (threadCount <= 0) {
            int threshold = Math.max(1, Math.max(1, rows / (useThreads * 2)));
            // usar pool compartido
            sharedPool.invoke(new MatrixMultiplyTask(A, B, Clocal, rowStart, rowEnd, threshold));
        } else {
            ForkJoinPool pool = new ForkJoinPool(useThreads);
            int threshold = Math.max(1, Math.max(1, rows / (useThreads * 2)));
            pool.invoke(new MatrixMultiplyTask(A, B, Clocal, rowStart, rowEnd, threshold));
            pool.shutdown();
        }

        // Copiar solo el segmento requerido a Cseg
        for (int i = 0; i < rows; i++) {
            System.arraycopy(Clocal[rowStart + i], 0, Cseg[i], 0, p);
        }
        return Cseg;
    }

    @Override
    public int[][] multiplyBlock(int[][] A_block, int[][] B, int rowOffset, int threadCount)
            throws RemoteException {
        resetProgress(A_block.length);
        logger.info(String.format("Iniciando multiplicación de bloque: filas %d-%d (total: %d filas)", 
            rowOffset, rowOffset + A_block.length - 1, A_block.length));
        // A_block: rows x m (rows contiguas de A a partir de rowOffset)
        if (A_block == null || A_block.length == 0) return new int[0][0];
        int rows = A_block.length;
        int p = B[0].length;

        int[][] Cseg = new int[rows][p];

        int useThreads = (threadCount <= 0) ? Runtime.getRuntime().availableProcessors() : threadCount;

        if (threadCount <= 0) {
            int threshold = Math.max(1, rows / (useThreads * 2));
            sharedPool.invoke(new MatrixMultiplyBlockTask(A_block, B, Cseg, 0, rows, threshold));
        } else {
            ForkJoinPool pool = new ForkJoinPool(useThreads);
            int threshold = Math.max(1, rows / (useThreads * 2));
            pool.invoke(new MatrixMultiplyBlockTask(A_block, B, Cseg, 0, rows, threshold));
            pool.shutdown();
        }

        return Cseg;
    }

    @Override
    public int[][] multiplyBlockPrepared(int[][] A_block, int rowOffset, int threadCount)
            throws RemoteException {
        if (preparedB == null) throw new RemoteException("No B prepared on server. Call prepareB(B) first.");
        // Delegate to existing logic but use preparedB
        int rows = (A_block == null) ? 0 : A_block.length;
        if (rows == 0) return new int[0][0];
        int p = preparedB[0].length;

        int[][] Cseg = new int[rows][p];
        int useThreads = (threadCount <= 0) ? Runtime.getRuntime().availableProcessors() : threadCount;

        if (threadCount <= 0) {
            int threshold = Math.max(1, rows / (useThreads * 2));
            sharedPool.invoke(new MatrixMultiplyBlockTask(A_block, preparedB, Cseg, 0, rows, threshold));
        } else {
            ForkJoinPool pool = new ForkJoinPool(useThreads);
            int threshold = Math.max(1, rows / (useThreads * 2));
            pool.invoke(new MatrixMultiplyBlockTask(A_block, preparedB, Cseg, 0, rows, threshold));
            pool.shutdown();
        }

        return Cseg;
    }

    // Tarea ForkJoin para bloques A_block que comienzan en índice 0..rows-1
    private class MatrixMultiplyBlockTask extends RecursiveAction {
        private final int[][] Ablock, B, Cseg;
        private final int rowStart, rowEnd, threshold;
        private final String taskId;

        MatrixMultiplyBlockTask(int[][] Ablock, int[][] B, int[][] Cseg, int rowStart, int rowEnd, int threshold) {
            this.Ablock = Ablock; this.B = B; this.Cseg = Cseg;
            this.rowStart = rowStart; this.rowEnd = rowEnd; this.threshold = threshold;
            this.taskId = Thread.currentThread().getName() + "-" + rowStart + "-" + rowEnd;
        }

        @Override
        protected void compute() {
            if (rowEnd - rowStart <= threshold) {
                logger.info(String.format("Hilo %s iniciando procesamiento de filas %d-%d", 
                    taskId, rowStart, rowEnd));
                
                int p = B[0].length, m = B.length;
                int lastProgress = 0;
                
                for (int i = rowStart; i < rowEnd; i++) {
                    for (int j = 0; j < p; j++) {
                        int s = 0;
                        for (int k = 0; k < m; k++) {
                            s += Ablock[i][k] * B[k][j];
                        }
                        Cseg[i][j] += s;
                    }
                    
                    // Reportar progreso cada 10%
                    int progress = (i - rowStart + 1) * 100 / (rowEnd - rowStart);
                    if (progress >= lastProgress + 10) {
                        logger.progress(String.format("Hilo %s - Filas %d-%d", taskId, rowStart, rowEnd), 
                            i - rowStart + 1, rowEnd - rowStart);
                        lastProgress = progress;
                    }
                }
                
                logger.success(String.format("Hilo %s completó el procesamiento de filas %d-%d", 
                    taskId, rowStart, rowEnd));
                    
                // Actualizar progreso después de procesar este bloque
                updateProgress(rowEnd - rowStart);
            } else {
                int mid = (rowStart + rowEnd) / 2;
                invokeAll(
                    new MatrixMultiplyBlockTask(Ablock, B, Cseg, rowStart, mid, threshold),
                    new MatrixMultiplyBlockTask(Ablock, B, Cseg, mid, rowEnd, threshold)
                );
            }
        }
    }
}
