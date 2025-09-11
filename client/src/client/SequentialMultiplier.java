package client;

public class SequentialMultiplier {
    public int[][] multiply(int[][] A, int[][] B) {
        int n = A.length, p = B[0].length, m = B.length;
        int[][] C = new int[n][p];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < p; j++)
                for (int k = 0; k < m; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }
}
