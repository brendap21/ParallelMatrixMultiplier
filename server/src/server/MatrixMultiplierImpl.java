package server;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.concurrent.*;
import shared.MatrixMultiplier;

public class MatrixMultiplierImpl extends UnicastRemoteObject
                                  implements MatrixMultiplier {
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
        // Fork/Join implementation
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        int threshold = Math.max(1, n / (threadCount * 2));
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
}
