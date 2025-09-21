package client;

import java.util.concurrent.*;

public class ConcurrentMultiplier {
    public int[][] multiply(int[][] A, int[][] B, int threads)
            throws InterruptedException {
        int n = A.length, p = B[0].length, m = B.length;
        int[][] C = new int[n][p];
        ForkJoinPool pool = new ForkJoinPool(threads);
        int threshold = Math.max(1, n / (threads * 2));
        pool.invoke(new MatrixMultiplyTask(A, B, C, 0, n, threshold));
        pool.shutdown();
        return C;
    }

    // Clase interna para Fork/Join
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
                        for (int k = 0; k < m; k++) {
                            C[i][j] += A[i][k] * B[k][j];
                        }
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
}
