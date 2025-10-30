package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerLogger {
    private final ConcurrentHashMap<Long, ThreadInfo> threadInfo = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> threadLocalId = new ConcurrentHashMap<>();
    private final AtomicInteger nextLocalId = new AtomicInteger(1);

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
        long javaThreadId = Thread.currentThread().getId();
        threadInfo.put(javaThreadId, info);
        // Asignar un número de hilo local por servidor
        int localId = nextLocalId.getAndIncrement();
        threadLocalId.put(javaThreadId, localId);
        System.out.printf("[Paralelo] Hilo #%d INICIA [Filas: %d-%d]%n", localId, startRow+1, endRow);
    }

    // Log de progreso de hilo (por fila)
    public void threadProgress(int threadId, int currentRow) {
        long javaThreadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(javaThreadId);
        Integer localId = threadLocalId.get(javaThreadId);
        if (info != null && localId != null) {
            System.out.printf("[Paralelo] Hilo #%d fila %d procesando...%n", localId, currentRow+1);
        }
    }

    // Log de finalización de hilo
    public void threadComplete(int threadId) {
        long javaThreadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(javaThreadId);
        Integer localId = threadLocalId.get(javaThreadId);
        if (info != null && localId != null) {
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            System.out.printf("[ÉXITO] [Paralelo] Hilo #%d TERMINA [Filas: %d-%d] - Tiempo: %.3fs%n", localId, info.startRow+1, info.endRow, elapsed.toMillis()/1000.0);
            threadInfo.remove(javaThreadId);
            threadLocalId.remove(javaThreadId);
        }
    }
}

