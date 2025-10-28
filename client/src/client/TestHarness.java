package client;

import java.util.ArrayList;
import java.util.List;

public class TestHarness {
    public static void main(String[] args) throws Exception {
        final int n = 1000;
        final int threads = 8;
        final int serverThreads = 8;

        System.out.println("Preparing matrices " + n + "x" + n + "...");
        int[][] A = new int[n][n];
        int[][] B = new int[n][n];
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] = rnd.nextInt(10);
                B[i][j] = rnd.nextInt(10);
            }
        }

        // Sequential
        SequentialMultiplier seq = new SequentialMultiplier();
        long t0 = System.nanoTime();
        int[][] Cseq = seq.multiply(A, B);
        long t1 = System.nanoTime();
        System.out.printf("Secuencial: %d ms\n", (t1 - t0) / 1_000_000);

        // Concurrent
        ConcurrentMultiplier conc = new ConcurrentMultiplier(threads);
        t0 = System.nanoTime();
        int[][] Cconc = conc.multiply(A, B, threads);
        t1 = System.nanoTime();
        System.out.printf("Concurrente: %d ms\n", (t1 - t0) / 1_000_000);

    // Distributed: try to contact local server (two variants)
    String serverIp = "127.0.0.1";
    if (args.length > 0 && args[0] != null && !args[0].isEmpty()) serverIp = args[0];

    ParallelMultiplier pm = new ParallelMultiplier();
    List<ParallelMultiplier.ServerInfo> servers = new ArrayList<>();
    servers.add(new ParallelMultiplier.ServerInfo(serverIp, 1099, "MatrixService"));

    System.out.println("Waiting 1s for server (if starting now)...");
    Thread.sleep(1000);

    // 1) with explicit serverThreads (may cause server to create per-call pools)
    t0 = System.nanoTime();
    int[][] Cpar = pm.multiplyDistributed(A, B, servers, threads, null, true, serverThreads);
    t1 = System.nanoTime();
    System.out.printf("Paralelo distribuido (serverThreads=%d): %d ms\n", serverThreads, (t1 - t0) / 1_000_000);

    // 2) with serverThreads=0 => server uses its shared pool (recommended)
    t0 = System.nanoTime();
    int[][] CparAuto = pm.multiplyDistributed(A, B, servers, threads, null, true, 0);
    t1 = System.nanoTime();
    System.out.printf("Paralelo distribuido (serverThreads=0): %d ms\n", (t1 - t0) / 1_000_000);

        // Basic correctness check
        boolean ok = true;
        outer: for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (Cseq[i][j] != Cpar[i][j]) { ok = false; break outer; }
            }
        }
        System.out.println("Resultado correcto vs paralelo: " + ok);
    }
}
