package shared;

import java.io.Serializable;

/**
 * Wrapper para retornar el resultado de un bloque junto con el tiempo de procesamiento
 */
public class BlockResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public final int[][] result;
    public final long processingTimeMillis;
    
    public BlockResult(int[][] result, long processingTimeMillis) {
        this.result = result;
        this.processingTimeMillis = processingTimeMillis;
    }
}
