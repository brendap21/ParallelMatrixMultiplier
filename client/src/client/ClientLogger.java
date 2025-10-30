package client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

public class ClientLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final String clientId;
    private final ConcurrentHashMap<Integer, ThreadInfo> threadInfo = new ConcurrentHashMap<>();

    private static class ThreadInfo {
        LocalDateTime startTime;
        int rowsProcessed;
        int startRow;
        int endRow;
        ThreadInfo(int startRow, int endRow) {
            this.startTime = LocalDateTime.now();
            this.rowsProcessed = 0;
            this.startRow = startRow;
            this.endRow = endRow;
        }
    }

    public ClientLogger(String clientId) {
        this.clientId = clientId;
    }

    // Log de inicio de hilo
    public void threadStart(int threadId, int startRow, int endRow) {
        ThreadInfo info = new ThreadInfo(startRow, endRow);
        threadInfo.put(threadId, info);
        log("THREAD", String.format("Hilo #%d INICIA [Filas: %d-%d]", threadId+1, startRow+1, endRow));
    }

    // Log de progreso de hilo (por fila)
    public void threadProgress(int threadId, int currentRow) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            info.rowsProcessed++;
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = (elapsed.toMillis() > 0) ? (info.rowsProcessed / (elapsed.toMillis() / 1000.0)) : 0.0;
            log("PROGRESS", String.format("Hilo #%d fila %d procesando... (%.2f filas/seg)", threadId+1, currentRow+1, rowsPerSecond));
        }
    }

    // Log de finalización de hilo
    public void threadComplete(int threadId) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = (elapsed.toSeconds() > 0) ? (info.rowsProcessed / (double)elapsed.toSeconds()) : 0.0;
            log("SUCCESS", String.format("Hilo #%d TERMINA [Filas: %d-%d] - Tiempo: %d.%03ds, Velocidad: %.2f filas/seg", threadId+1, info.startRow+1, info.endRow, elapsed.toSeconds(), elapsed.toMillisPart(), rowsPerSecond));
            threadInfo.remove(threadId);
        }
    }

    // Solo logs de hilos, no otros logs generales
    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.printf("[%s][%s][%s] %s%n", timestamp, clientId, level, message);
    }
}

