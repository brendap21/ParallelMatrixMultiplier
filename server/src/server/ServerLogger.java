package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final String serverId;
    
    public ServerLogger(String serverId) {
        this.serverId = serverId;
    }

    public void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.printf("[%s][%s][%s] %s%n", timestamp, serverId, level, message);
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

    public void error(String message) {
        log("ERROR", message);
    }
}
        threadInfo.put(threadId, info);
        log("THREAD", String.format("Hilo #%d INICIA [Filas: %d-%d]", 
            threadId, startRow, endRow));
    }

    public void threadProgress(int currentRow) {
        long threadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            info.rowsProcessed++;
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = info.rowsProcessed / (elapsed.toMillis() / 1000.0);
            
            log("PROGRESS", String.format("Hilo #%d fila %d procesando... (%.2f filas/seg)", 
                threadId, currentRow, rowsPerSecond));
        }
    }

    public void threadComplete() {
        long threadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            double rowsPerSecond = info.rowsProcessed / (Math.max(0.001, elapsed.toSeconds()));
            
            log("SUCCESS", String.format("Hilo #%d TERMINA [Filas: %d-%d] - Tiempo: %d.%03ds, Velocidad: %.2f filas/seg", 
                threadId, info.startRow, info.endRow, 
                elapsed.toSeconds(), elapsed.toMillisPart(),
                rowsPerSecond));
                
            threadInfo.remove(threadId);
        }
    }

    public void info(String message) {
        log("INFO", message);
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

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.printf("[%s][%s][%s] %s%n", timestamp, serverId, level, message);
    }
}
