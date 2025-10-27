package client;

import java.util.concurrent.*;

/**
 * ConcurrentMultiplier usando ForkJoin para multiplicar matrices.
 * Proporciona:
 * - multiply(A,B,threads) -> matriz completa resultado
 * - multiplyBlock(A_block,B,threads) -> multiplica solo A_block (rows x m) contra B y devuelve rows x p result
 */
public class ConcurrentMultiplier {

    // Multiplica matrices completas (como antes)
    public int[][] multiply(int[][] A, int[][] B, int threads) {
        int n = A.length, p = B[0].length, m = B.length;
        int[][] C = new int[n][p];
        int useThreads = (threads <= 0) ? Runtime.getRuntime().availableProcessors() : threads;
        ForkJoinPool pool = new ForkJoinPool(useThreads);
        int threshold = Math.max(1, n / (useThreads * 2));
        pool.invoke(new MatrixMultiplyTask(A, B, C, 0, n, threshold));
        pool.shutdown();
        return C;
    }

    // NUEVO: Multiplica solo el bloque A_block (rows x m) contra B (m x p). Devuelve rows x p.
    public int[][] multiplyBlock(int[][] A_block, int[][] B, int threads) {
        if (A_block == null || A_block.length == 0) return new int[0][0];
        int rows = A_block.length;
        int p = B[0].length;
        int[][] Cseg = new int[rows][p];
        int useThreads = (threads <= 0) ? Runtime.getRuntime().availableProcessors() : threads;
        ForkJoinPool pool = new ForkJoinPool(useThreads);
        int threshold = Math.max(1, rows / (useThreads * 2));
        pool.invoke(new MatrixMultiplyBlockTask(A_block, B, Cseg, 0, rows, threshold));
        pool.shutdown();
        return Cseg;
    }

    // Fork/Join task for full matrix
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
                        for (int k = 0; k < m; k++) s += A[i][k] * B[k][j];
                        C[i][j] += s;
                    }
                }
            } else {
                int mid = (rowStart + rowEnd) / 2;
                invokeAll(new MatrixMultiplyTask(A, B, C, rowStart, mid, threshold),
                          new MatrixMultiplyTask(A, B, C, mid, rowEnd, threshold));
            }
        }
    }

    // Fork/Join task for A_block (rows indexed 0..rows-1)
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
                        for (int k = 0; k < m; k++) s += Ablock[i][k] * B[k][j];
                        Cseg[i][j] += s;
                    }
                }
            } else {
                int mid = (rowStart + rowEnd) / 2;
                invokeAll(new MatrixMultiplyBlockTask(Ablock, B, Cseg, rowStart, mid, threshold),
                          new MatrixMultiplyBlockTask(Ablock, B, Cseg, mid, rowEnd, threshold));
            }
        }
    }
}
