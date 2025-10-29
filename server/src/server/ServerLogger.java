package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final String serverId;

    public ServerLogger(String serverId) {
        this.serverId = serverId;
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

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.printf("[%s][%s][%s] %s%n", timestamp, serverId, level, message);
    }
}