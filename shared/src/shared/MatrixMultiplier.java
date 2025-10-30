package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MatrixMultiplier extends Remote {
    // Secuencial completa
    int[][] multiply(int[][] A, int[][] B) throws RemoteException;

    // Concurrente local (usa threads) - sobre matrices completas
    int[][] multiplyConcurrent(int[][] A, int[][] B, int threadCount)
            throws RemoteException;

    // Porción de filas para paralelismo distribuido (devuelve filas [rowStart,rowEnd) del resultado)
    int[][] multiplySegment(int[][] A, int[][] B, int rowStart, int rowEnd)
            throws RemoteException;

    // Porción que el servidor procesa internamente de forma concurrente (usa Fork/Join con threadCount)
    // A y B son matrices completas; devuelve filas [rowStart,rowEnd)
    int[][] multiplyConcurrentSegment(int[][] A, int[][] B, int rowStart, int rowEnd, int threadCount)
            throws RemoteException;

    // NUEVO: Multiplica un bloque A_block (filas contiguas) contra B.
    // blockIndex: número de bloque global (para logs)
    // rowOffset indica la fila global de A correspondiente a A_block[0].
    // Devuelve las filas calculadas (tamaño A_block.length x B[0].length).
    // threadCount <= 0 => servidor decide (#cores).
    int[][] multiplyBlock(int[][] A_block, int[][] B, int blockIndex, int rowOffset, int threadCount)
            throws RemoteException;

    // PREPARE / cached B API: allow the client to upload B once to the server
    // so subsequent block calls don't need to resend B every time.
    void prepareB(int[][] B) throws RemoteException;

    // Clear any prepared B stored on the server (optional)
    void clearPreparedB() throws RemoteException;

    // Multiply using a previously prepared B. A_block are the contiguous rows
    // corresponding to global rowOffset. Server must have B prepared first.
    // blockIndex: número de bloque global (para logs)
    int[][] multiplyBlockPrepared(int[][] A_block, int blockIndex, int rowOffset, int threadCount)
            throws RemoteException;
}
