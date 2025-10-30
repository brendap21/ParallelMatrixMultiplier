package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class ServerLogger {
    private final ConcurrentHashMap<Integer, ThreadInfo> threadInfo = new ConcurrentHashMap<>();

    private static class ThreadInfo {
        LocalDateTime startTime;
        int startRow;
        int endRow;
        ThreadInfo(int startRow, int endRow) {
            this.startTime = LocalDateTime.now();
            this.startRow = startRow;
            this.endRow = endRow;
        }
    }

    public ServerLogger(String serverId) {
        // serverId ignorado, ya no se usa
    }

    // Log de inicio de hilo
    public void threadStart(int threadId, int startRow, int endRow) {
    ThreadInfo info = new ThreadInfo(startRow, endRow);
    threadInfo.put(threadId, info);
    long javaThreadId = Thread.currentThread().getId();
    System.out.printf("[Concurrente] Bloque #%d (Hilo Java: %d) INICIA [Filas: %d-%d]%n", threadId+1, javaThreadId, startRow+1, endRow);
    }

    // Log de progreso de hilo (por fila)
    public void threadProgress(int threadId, int currentRow) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            long javaThreadId = Thread.currentThread().getId();
            System.out.printf("[Concurrente] Bloque #%d (Hilo Java: %d) fila %d procesando...%n", threadId+1, javaThreadId, currentRow+1);
        }
    }

    // Log de finalización de hilo
    public void threadComplete(int threadId) {
        ThreadInfo info = threadInfo.get(threadId);
        if (info != null) {
            long javaThreadId = Thread.currentThread().getId();
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            System.out.printf("[ÉXITO] [Concurrente] Bloque #%d (Hilo Java: %d) TERMINA [Filas: %d-%d] - Tiempo: %.3fs%n", threadId+1, javaThreadId, info.startRow+1, info.endRow, elapsed.toMillis()/1000.0);
            threadInfo.remove(threadId);
        }
    }
}

