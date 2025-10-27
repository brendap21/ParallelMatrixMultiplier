package server;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.concurrent.*;
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
    protected MatrixMultiplierImpl() throws RemoteException { super(); }

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
        ForkJoinPool pool = new ForkJoinPool(useThreads);
        int threshold = Math.max(1, n / (useThreads * 2));
        pool.invoke(new MatrixMultiplyTask(A, B, C, 0, n, threshold));
        pool.shutdown();
        return C;
    }

    // Clase interna para Fork/Join sobre matrices completas
    private static class MatrixMultiplyTask extends RecursiveAction {
        private final int[][] A, B, C;
        private final int rowStart, rowEnd, threshold;
        MatrixMultiplyTask(int[][] A, int[][] B, int[][] C, int rowStart, int rowEnd, int threshold) {
            this.A = A; this.B = B; this.C = C;
            this.rowStart = rowStart; this.rowEnd = rowEnd; this.threshold = threshold;
        }
        @Override
        protected void compute() {
            if (rowEnd - rowStart <= threshold) {
                int p = B[0].length, m = B.length;
                for (int i = rowStart; i < rowEnd; i++) {
                    for (int j = 0; j < p; j++) {
                        int s = 0;
                        for (int k = 0; k < m; k++) {
                            s += A[i][k] * B[k][j];
                        }
                        C[i][j] += s;
                    }
                }
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
        ForkJoinPool pool = new ForkJoinPool(useThreads);

        // threshold basado en filas del segmento
        int threshold = Math.max(1, Math.max(1, rows / (useThreads * 2)));

        // Invocar ForkJoin para el subconjunto de filas solicitado (el task llenará Clocal en el rango)
        pool.invoke(new MatrixMultiplyTask(A, B, Clocal, rowStart, rowEnd, threshold));
        pool.shutdown();

        // Copiar solo el segmento requerido a Cseg
        for (int i = 0; i < rows; i++) {
            System.arraycopy(Clocal[rowStart + i], 0, Cseg[i], 0, p);
        }
        return Cseg;
    }

    @Override
    public int[][] multiplyBlock(int[][] A_block, int[][] B, int rowOffset, int threadCount)
            throws RemoteException {
        // A_block: rows x m (rows contiguas de A a partir de rowOffset)
        if (A_block == null || A_block.length == 0) return new int[0][0];
        int rows = A_block.length;
        int m = A_block[0].length;
        int p = B[0].length;

        int[][] Cseg = new int[rows][p];

        int useThreads = (threadCount <= 0) ? Runtime.getRuntime().availableProcessors() : threadCount;
        ForkJoinPool pool = new ForkJoinPool(useThreads);

        // Usaremos una tarea que conoce A_block con índices 0..rows
        int threshold = Math.max(1, rows / (useThreads * 2));

        pool.invoke(new MatrixMultiplyBlockTask(A_block, B, Cseg, 0, rows, threshold));
        pool.shutdown();

        // Cseg ya contiene las filas calculadas (correspondientes a rowOffset..rowOffset+rows-1)
        return Cseg;
    }

    // Tarea ForkJoin para bloques A_block que comienzan en índice 0..rows-1
    private static class MatrixMultiplyBlockTask extends RecursiveAction {
        private final int[][] Ablock, B, Cseg;
        private final int rowStart, rowEnd, threshold;
        MatrixMultiplyBlockTask(int[][] Ablock, int[][] B, int[][] Cseg, int rowStart, int rowEnd, int threshold) {
            this.Ablock = Ablock; this.B = B; this.Cseg = Cseg;
            this.rowStart = rowStart; this.rowEnd = rowEnd; this.threshold = threshold;
        }
        @Override
        protected void compute() {
            if (rowEnd - rowStart <= threshold) {
                int p = B[0].length, m = B.length;
                for (int i = rowStart; i < rowEnd; i++) {
                    for (int j = 0; j < p; j++) {
                        int s = 0;
                        for (int k = 0; k < m; k++) {
                            s += Ablock[i][k] * B[k][j];
                        }
                        Cseg[i][j] += s;
                    }
                }
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
