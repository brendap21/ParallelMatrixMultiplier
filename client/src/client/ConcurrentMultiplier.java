package client;

import java.util.concurrent.*;

public class ConcurrentMultiplier {
    public int[][] multiply(int[][] A, int[][] B, int threads)
            throws InterruptedException {
        int n = A.length, p = B[0].length, m = B.length;
        int[][] C = new int[n][p];
        ExecutorService exec = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < n; i++) {
            final int row = i;
            exec.submit(() -> {
                for (int j = 0; j < p; j++)
                    for (int k = 0; k < m; k++)
                        C[row][j] += A[row][k] * B[k][j];
            });
        }

        exec.shutdown();
        exec.awaitTermination(1, TimeUnit.HOURS);
        return C;
    }
}
