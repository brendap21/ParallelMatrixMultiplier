package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

public class ServerLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final String serverId;
    private final ConcurrentHashMap<Integer, ThreadInfo> threadInfo = new ConcurrentHashMap<>();

    private static class ThreadInfo {
        int rowsProcessed;
        int startRow;
        int endRow;

        ThreadInfo(int startRow, int endRow) {
            this.rowsProcessed = 0;
            this.startRow = startRow;
            this.endRow = endRow;
        }
    }

    public ServerLogger(String serverId) {
        this.serverId = serverId;
    }

    public void threadStart(int threadId, int startRow, int endRow) {
        ThreadInfo info = new ThreadInfo(startRow, endRow);
        threadInfo.put(threadId, info);
        log("THREAD", String.format("Hilo #%d INICIA [Filas: %d-%d]", threadId+1, startRow+1, endRow));
    }

    public void threadProgress(int threadId, int currentRow) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            info.rowsProcessed++;
            log("PROGRESS", String.format("Hilo #%d fila %d procesando...", threadId+1, currentRow+1));
        }
    }

    public void threadComplete(int threadId) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            log("SUCCESS", String.format("Hilo #%d TERMINA [Filas: %d-%d] - Procesadas: %d", threadId+1, info.startRow+1, info.endRow, info.rowsProcessed));
            threadInfo.remove(threadId);
        }
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void progress(String operation, int current, int total) {
        double percentage = (double) current / total * 100;
        log("PROGRESS", String.format("%s: %.2f%% completado (%d/%d)", operation, percentage, current, total));
    }

    public void success(String message) {
        log("SUCCESS", message);
    }

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.printf("[%s][%s][%s] %s%n", timestamp, serverId, level, message);
    }
}
    }

    public void success(String message) {
        log("SUCCESS", message);
    }

    public void performance(String message) {
        log("PERFORMANCE", message);
    }

    public void logMatrixOperation(int rows, int cols) {
        log("INFO", String.format("Operación de matriz: %d filas, %d columnas", rows, cols));
    }
}
