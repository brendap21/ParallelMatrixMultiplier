package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MatrixMultiplier extends Remote {
    // Secuencial completa
    int[][] multiply(int[][] A, int[][] B) throws RemoteException;

    // Concurrente local (usa threads)
    int[][] multiplyConcurrent(int[][] A, int[][] B, int threadCount)
            throws RemoteException;

    // Porción de filas para paralelismo distribuido
    int[][] multiplySegment(int[][] A, int[][] B, int rowStart, int rowEnd)
            throws RemoteException;
}
