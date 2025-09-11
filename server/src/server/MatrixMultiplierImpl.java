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
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < n; i++) {
            final int row = i;
            exec.submit(() -> {
                for (int j = 0; j < p; j++)
                    for (int k = 0; k < m; k++)
                        C[row][j] += A[row][k] * B[k][j];
            });
        }

        exec.shutdown();
        try {
            exec.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return C;
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
