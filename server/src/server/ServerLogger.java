package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerLogger {
    private final ConcurrentHashMap<Long, ThreadInfo> threadInfo = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> threadLocalId = new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> localId = ThreadLocal.withInitial(() -> -1);
    private final AtomicInteger nextLocalId = new AtomicInteger(1);

    // Llamar a esto al inicio de cada petición para reiniciar la numeración
    public void resetLocalIds() {
        nextLocalId.set(1);
        threadLocalId.clear();
    }

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
        int assignedId = nextLocalId.getAndIncrement();
        threadLocalId.put(javaThreadId, assignedId);
        localId.set(assignedId);
        System.out.printf("[Paralelo][Servidor] Hilo #%d INICIA [Filas: %d-%d]%n", assignedId, startRow+1, endRow);
    }

    // Log de progreso de hilo (por fila)
    public void threadProgress(int threadId, int currentRow) {
        long javaThreadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(javaThreadId);
        Integer id = threadLocalId.get(javaThreadId);
        if (info != null && id != null) {
            System.out.printf("[Paralelo][Servidor] Hilo #%d fila %d procesando...%n", id, currentRow+1);
        }
    }

    // Log de finalización de hilo
    public void threadComplete(int threadId) {
        long javaThreadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(javaThreadId);
        Integer id = threadLocalId.get(javaThreadId);
        if (info != null && id != null) {
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            System.out.printf("[ÉXITO] [Paralelo][Servidor] Hilo #%d TERMINA [Filas: %d-%d] - Tiempo: %.3fs%n", id, info.startRow+1, info.endRow, elapsed.toMillis()/1000.0);
            threadInfo.remove(javaThreadId);
            threadLocalId.remove(javaThreadId);
        }
    }
}

