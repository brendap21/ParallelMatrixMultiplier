package client;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

public class ClientLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final String clientId;
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

    public ClientLogger(String clientId) {
        this.clientId = clientId;
    }

    public void threadStart(int workerIndex, int startRow, int endRow) {
        ThreadInfo info = new ThreadInfo(startRow, endRow);
        threadInfo.put(workerIndex, info);
        log("THREAD", String.format("Worker #%d INICIA [Filas: %d-%d]", workerIndex+1, startRow+1, endRow));
    }

    public void threadProgress(int workerIndex, int currentRow) {
        ThreadInfo info = threadInfo.get(workerIndex);
        if (info != null) {
            info.rowsProcessed++;
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = info.rowsProcessed / (elapsed.toMillis() / 1000.0);
            log("PROGRESS", String.format("Worker #%d fila %d procesando... (%.2f filas/seg)", workerIndex+1, currentRow+1, rowsPerSecond));
        }
    }

    public void threadComplete(int workerIndex) {
        ThreadInfo info = threadInfo.get(workerIndex);
        if (info != null) {
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = info.rowsProcessed / (Math.max(0.001, elapsed.toSeconds()));
            log("SUCCESS", String.format("Worker #%d TERMINA [Filas: %d-%d] - Tiempo: %d.%03ds, Velocidad: %.2f filas/seg", workerIndex+1, info.startRow+1, info.endRow, elapsed.toSeconds(), elapsed.toMillisPart(), rowsPerSecond));
            threadInfo.remove(workerIndex);
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
        System.out.printf("[%s][%s][%s] %s%n", timestamp, clientId, level, message);
    }
}
