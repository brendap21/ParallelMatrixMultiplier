/* 
n es el número de filas de A
p es el número de columnas de B
m es el número de columnas de A (que debe coincidir con el número de filas de B para que la multiplicación sea válida).
Luego, crea una nueva matriz C de tamaño n por p para almacenar el resultado. 

Utiliza tres ciclos anidados: 

    El primero recorre las filas de A
    El segundo recorre las columnas de B
    El tercero realiza la suma de los productos correspondientes entre la fila de A y la columna de B.

 Así, cada elemento C[i][j] se calcula sumando A[i][k] * B[k][j] para todos los valores de k. 
*/

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

