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
    
    // Obtiene el nombre del hilo real del pool (ej. "ForkJoinPool-1-worker-3" o "pool-1-thread-2")
    private String poolThreadName() {
        return Thread.currentThread().getName();
    }

    // Extrae el índice numérico al final del nombre del hilo (ej. 3 o 2)
    private int poolThreadIndex() {
        String name = poolThreadName();
        int i = name.length() - 1;
        while (i >= 0 && Character.isDigit(name.charAt(i))) i--;
        if (i < name.length() - 1) {
            try {
                return Integer.parseInt(name.substring(i + 1));
            } catch (NumberFormatException ignored) {}
        }
        // fallback: usar id local asignado si existe
        int id = localId.get();
        return id > 0 ? id : -1;
    }

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
        int poolIdx = poolThreadIndex();
        String name = poolThreadName();
        // Mostrar el nombre real del hilo del pool y su índice si se pudo extraer
        if (poolIdx > 0)
            System.out.printf("[Paralelo][Servidor] %s (Hilo #%d) INICIA [Filas: %d-%d]%n", name, poolIdx, startRow+1, endRow);
        else
            System.out.printf("[Paralelo][Servidor] %s INICIA [Filas: %d-%d]%n", name, startRow+1, endRow);
    }

    // Log de progreso de hilo (por fila)
    public void threadProgress(int threadId, int currentRow) {
        long javaThreadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(javaThreadId);
        Integer id = threadLocalId.get(javaThreadId);
        if (info != null && id != null) {
            int poolIdx = poolThreadIndex();
            String name = poolThreadName();
            if (poolIdx > 0)
                System.out.printf("[Paralelo][Servidor] %s (Hilo #%d) fila %d procesando...%n", name, poolIdx, currentRow+1);
            else
                System.out.printf("[Paralelo][Servidor] %s fila %d procesando...%n", name, currentRow+1);
        }
    }

    // Log de finalización de hilo
    public void threadComplete(int threadId) {
        long javaThreadId = Thread.currentThread().getId();
        ThreadInfo info = threadInfo.get(javaThreadId);
        Integer id = threadLocalId.get(javaThreadId);
        if (info != null && id != null) {
            Duration elapsed = Duration.between(info.startTime, LocalDateTime.now());
            int poolIdx = poolThreadIndex();
            String name = poolThreadName();
            if (poolIdx > 0)
                System.out.printf("[ÉXITO] [Paralelo][Servidor] %s (Hilo #%d) TERMINA [Filas: %d-%d] - Tiempo: %.3fs%n", name, poolIdx, info.startRow+1, info.endRow, elapsed.toMillis()/1000.0);
            else
                System.out.printf("[ÉXITO] [Paralelo][Servidor] %s TERMINA [Filas: %d-%d] - Tiempo: %.3fs%n", name, info.startRow+1, info.endRow, elapsed.toMillis()/1000.0);
            threadInfo.remove(javaThreadId);
            threadLocalId.remove(javaThreadId);
        }
    }
}

