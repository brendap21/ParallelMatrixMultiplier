package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

public class ServerLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final String serverId;
    private final ConcurrentHashMap<Integer, ThreadInfo> threadInfo = new ConcurrentHashMap<>();

    private static class ThreadInfo {
        LocalDateTime startTime;
        int rowsProcessed;
        int totalRows;
        int startRow;
        int endRow;

        ThreadInfo(int startRow, int endRow) {
            this.startTime = LocalDateTime.now();
            this.rowsProcessed = 0;
            this.totalRows = endRow - startRow;
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
        log("THREAD", String.format("Worker #%d INICIA [Filas: %d-%d]", threadId+1, startRow+1, endRow));
    }

    public void threadProgress(int threadId, int currentRow) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            info.rowsProcessed++;
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = info.rowsProcessed / (elapsed.toMillis() / 1000.0);
            log("PROGRESS", String.format("Worker #%d fila %d procesando... (%.2f filas/seg)", threadId+1, currentRow+1, rowsPerSecond));
        }
    }

    public void threadComplete(int threadId) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = info.rowsProcessed / (Math.max(0.001, elapsed.toSeconds()));
            log("SUCCESS", String.format("Worker #%d TERMINA [Filas: %d-%d] - Tiempo: %d.%03ds, Velocidad: %.2f filas/seg", threadId+1, info.startRow+1, info.endRow, elapsed.toSeconds(), elapsed.toMillisPart(), rowsPerSecond));
            threadInfo.remove(threadId);
        }
    }

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.printf("[%s][%s][%s] %s%n", timestamp, serverId, level, message);
    }
}
    public void progress(String operation, int current, int total) {
        double percentage = (double) current / total * 100;
        log("PROGRESS", String.format("%s: %.2f%% completado (%d/%d)", 
            operation, percentage, current, total));
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
