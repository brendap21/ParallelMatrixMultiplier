package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import client.ClientLogger;

import client.ParallelMultiplier.ServerInfo;
import client.ParallelMultiplier.ProgressCallback;

/**
 * GUI principal para probar la multiplicación de matrices
 * en modo secuencial, concurrente y paralelo (distribuido).
 *
 * Mejora la visualización de hilos: barras horizontales etiquetadas,
 * tiempos por hilo, consola con colores (JTextPane) y barra global con porcentaje.
 *
 * Ahora con control de chunkSize (JSpinner) para ajustar filas por bloque.
 */
public class AppGUI extends JFrame {
    private static volatile AppGUI instance = null;

    public static AppGUI getInstanceIfExists() {
        return instance;
    }
    private final ClientLogger clientLogger = new ClientLogger("Cliente");
    private int[][] A, B, C;
    private JTable tblA, tblB, tblC;
    private JTextPane statusPane; // ahora JTextPane para colores
    private StyledDocument statusDoc;
    private JProgressBar progressBar;
    private JTextField txtSize, txtThreads;
    private JLabel lblTimeSeq, lblTimeConc, lblTimePar;
    // NUEVO: campo para hilos del servidor
    private JTextField txtServerThreads;

    // Panel para estado de hilos: contendrá sub-paneles por hilo
    private JPanel threadStatusPanel;
    private List<JProgressBar> threadBars = new ArrayList<>();
    private List<JLabel> threadTimeLabels = new ArrayList<>();
    private List<Long> threadStartTimes = new ArrayList<>();
    private List<Integer> threadTotalRows = new ArrayList<>();

    private enum LogType { INFO, PROGRESS, SUCCESS, ERROR, WARNING }
    private JComboBox<String> filterCombo;
    private JButton btnClearLog; // btnExportLog eliminado
    private List<LogEntry> logEntries = new ArrayList<>();
    private boolean autoScroll = true;

    private static class LogEntry {
        String msg;
        LogType type;
        public LogEntry(String msg, LogType type) { this.msg = msg; this.type = type; }
    }

    private long startTimeSeq;

    public AppGUI() {
        super("Multiplicador de Matrices");
        instance = this;

        setLayout(new BorderLayout(6, 6));

        // --------- TOP: controles principales ----------
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        pnlTop.add(new JLabel("Tamaño (n):"));
        txtSize = new JTextField("10", 5);
        pnlTop.add(txtSize);

        pnlTop.add(new JLabel("Hilos:"));
        txtThreads = new JTextField("4", 5);
        pnlTop.add(txtThreads);

        pnlTop.add(new JLabel("Hilos servidor:"));
        txtServerThreads = new JTextField("0", 4); // 0 = auto
        pnlTop.add(txtServerThreads);

        JButton btnGen = new JButton("Generar Matrices");
        JButton btnRunSeq = new JButton("Multiplicar Secuencial");
        JButton btnRunConc = new JButton("Multiplicar Concurrente");
        JButton btnRunPar = new JButton("Multiplicar Paralelo");
        pnlTop.add(btnGen);
        pnlTop.add(btnRunSeq);
        pnlTop.add(btnRunConc);
        pnlTop.add(btnRunPar);

        add(pnlTop, BorderLayout.NORTH);

        // --------- CENTER: previsualización de matrices ----------
        tblA = new JTable();
        tblB = new JTable();
        tblC = new JTable();

        JScrollPane spA = new JScrollPane(tblA);
        JScrollPane spB = new JScrollPane(tblB);
        JScrollPane spC = new JScrollPane(tblC);

        JButton btnViewA = new JButton("Ver Completa A");
        JButton btnViewB = new JButton("Ver Completa B");
        JButton btnViewC = new JButton("Ver Completa C");

        JPanel pnlCenter = new JPanel();
        pnlCenter.setLayout(new BoxLayout(pnlCenter, BoxLayout.X_AXIS));
        pnlCenter.add(wrapTitled("Matriz A", spA, btnViewA));
        pnlCenter.add(wrapTitled("Matriz B", spB, btnViewB));
        pnlCenter.add(wrapTitled("Matriz C", spC, btnViewC));
        add(pnlCenter, BorderLayout.CENTER);

        // --------- PANEL INFERIOR: progreso, tiempos y logs ----------
        JPanel pnlBottom = new JPanel(new BorderLayout(6,6));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(0, 22));
        pnlBottom.add(progressBar, BorderLayout.NORTH);

        // Panel de hilos, tiempos y consola en la misma fila
        JPanel pnlStatusRow = new JPanel();
        pnlStatusRow.setLayout(new BoxLayout(pnlStatusRow, BoxLayout.X_AXIS));
        pnlStatusRow.setBorder(new EmptyBorder(6,6,6,6));

        // PANEL 1: estado de hilos (con barras horizontales)
        threadStatusPanel = new JPanel();
        threadStatusPanel.setLayout(new BoxLayout(threadStatusPanel, BoxLayout.Y_AXIS));
        JScrollPane threadScroll = new JScrollPane(threadStatusPanel);
    threadScroll.setPreferredSize(new Dimension(400, 260));
        threadScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pnlStatusRow.add(threadScroll);

        // PANEL 2: tiempos de ejecución
        JPanel pnlTimes = new JPanel();
        pnlTimes.setLayout(new BoxLayout(pnlTimes, BoxLayout.Y_AXIS));
        lblTimeSeq = new JLabel("Secuencial: - ms", JLabel.CENTER);
        lblTimeConc = new JLabel("Concurrente: - ms", JLabel.CENTER);
        lblTimePar = new JLabel("Paralelo: - ms", JLabel.CENTER);
        lblTimeSeq.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTimeConc.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTimePar.setAlignmentX(Component.CENTER_ALIGNMENT);
        pnlTimes.add(lblTimeSeq);
        pnlTimes.add(Box.createRigidArea(new Dimension(0,8)));
        pnlTimes.add(lblTimeConc);
        pnlTimes.add(Box.createRigidArea(new Dimension(0,8)));
        pnlTimes.add(lblTimePar);
        pnlTimes.setMaximumSize(new Dimension(200, 260));
        pnlStatusRow.add(Box.createRigidArea(new Dimension(10,0)));
        pnlStatusRow.add(pnlTimes);

        // PANEL 3: consola/logs (JTextPane para colores)
        statusPane = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() { return false; }
        };
        statusPane.setEditable(true); // copiable
        statusPane.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusDoc = statusPane.getStyledDocument();
        statusPane.addCaretListener(e -> {
            JScrollBar vbar = ((JScrollPane)statusPane.getParent().getParent()).getVerticalScrollBar();
            autoScroll = vbar.getValue() + vbar.getVisibleAmount() >= vbar.getMaximum();
        });
    JScrollPane logScroll = new JScrollPane(statusPane);
    logScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    logScroll.setPreferredSize(new Dimension(460, 260));

        // Filtros y botones
        JPanel logControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        filterCombo = new JComboBox<>(new String[]{"Todos", "Progreso", "Errores"});
        filterCombo.addActionListener(e -> refreshLogView());
        btnClearLog = new JButton("Limpiar consola");
        btnClearLog.addActionListener(e -> clearLog());
        // btnExportLog eliminado
        logControlPanel.add(new JLabel("Filtro:"));
        logControlPanel.add(filterCombo);
        logControlPanel.add(btnClearLog);
        // logControlPanel.add(btnExportLog); // eliminado

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(logControlPanel, BorderLayout.NORTH);
        logPanel.add(logScroll, BorderLayout.CENTER);
    logPanel.setPreferredSize(new Dimension(460, 260));
        pnlStatusRow.add(Box.createRigidArea(new Dimension(10,0)));
        pnlStatusRow.add(logPanel);

        pnlBottom.add(pnlStatusRow, BorderLayout.CENTER);
        pnlBottom.setPreferredSize(new Dimension(0, 320));

        add(pnlBottom, BorderLayout.SOUTH);

        // --------- ACCIONES ----------
        btnGen.addActionListener(e -> generateMatrices());
        btnRunSeq.addActionListener(e -> runSequential());
        btnRunConc.addActionListener(e -> runConcurrent());
        btnRunPar.addActionListener(e -> runParallel());

        btnViewA.addActionListener(e -> showFullMatrix(A, "Matriz A"));
        btnViewB.addActionListener(e -> showFullMatrix(B, "Matriz B"));
        btnViewC.addActionListener(e -> showFullMatrix(C, "Matriz C"));

        setSize(1200, 720);
        setMinimumSize(new Dimension(1000, 650));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    /** Genera matrices A y B con números aleatorios */
    private void generateMatrices() {
        int n;
        try {
            n = Integer.parseInt(txtSize.getText().trim());
            if (n <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Introduce un tamaño válido (entero > 0).");
            return;
        }

        Random rnd = new Random();
        A = new int[n][n];
        B = new int[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                A[i][j] = rnd.nextInt(10);
                B[i][j] = rnd.nextInt(10);
            }
        appendInfo("Matrices generadas de " + n + "x" + n + "\n");
        display(tblA, A);
        display(tblB, B);
        display(tblC, null);

        // reset UI for potential runs
        resetThreadPanel();
        progressBar.setValue(0);
        progressBar.setString("0%");
    }

    /** Muestra una tabla con numeración y previsualización de 10x10, mejoras visuales incluidas */
    private void display(JTable tbl, int[][] M) {
        if (M == null) {
            tbl.setModel(new DefaultTableModel());
            return;
        }

        int n = M.length;
        int preview = Math.min(10, n);

        String[] cols = new String[preview + 1];
        cols[0] = "#";
        for (int j = 1; j <= preview; j++) cols[j] = String.valueOf(j);

        Object[][] data = new Object[preview][preview + 1];
        for (int i = 0; i < preview; i++) {
            data[i][0] = (i + 1);
            for (int j = 0; j < preview; j++) data[i][j + 1] = M[i][j];
        }

        DefaultTableModel model = new DefaultTableModel(data, cols);
        tbl.setModel(model);

        tbl.setFont(new Font("Monospaced", Font.PLAIN, 14));
        tbl.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tbl.setFillsViewportHeight(true);

        for (int i = 0; i < tbl.getColumnCount(); i++)
            tbl.getColumnModel().getColumn(i).setPreferredWidth(40);

        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lbl.setHorizontalAlignment(JLabel.CENTER);
                lbl.setBackground(row % 2 == 0 ? new Color(230, 230, 250) : Color.WHITE);
                if (row == column - 1) lbl.setBackground(new Color(144, 238, 144));
                if (value instanceof Integer) {
                    int val = (Integer) value;
                    if (val >= 8) lbl.setForeground(Color.RED);
                    else if (val == 0) lbl.setForeground(Color.GRAY);
                    else lbl.setForeground(Color.BLACK);
                }
                return lbl;
            }
        });

        tbl.setRowHeight(22);
    }

    // --------- UTIL: consola con colores y estilos
    private final Object logLock = new Object();

    private void appendStyled(String msg, LogType type) {
        logEntries.add(new LogEntry(msg, type));
        if (!shouldShow(type)) return;
        try {
            SimpleAttributeSet aset = new SimpleAttributeSet();
            switch (type) {
                case INFO:
                    StyleConstants.setForeground(aset, Color.BLUE);
                    break;
                case PROGRESS:
                    StyleConstants.setForeground(aset, new Color(30, 144, 255));
                    break;
                case SUCCESS:
                    StyleConstants.setForeground(aset, new Color(0,128,0));
                    StyleConstants.setBold(aset, true);
                    break;
                case ERROR:
                    StyleConstants.setForeground(aset, Color.RED);
                    StyleConstants.setBold(aset, true);
                    break;
                case WARNING:
                    StyleConstants.setForeground(aset, new Color(255, 140, 0));
                    StyleConstants.setItalic(aset, true);
                    break;
            }
            statusDoc.insertString(statusDoc.getLength(), msg, aset);
            if (autoScroll) statusPane.setCaretPosition(statusDoc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    private boolean shouldShow(LogType type) {
        String sel = (String)filterCombo.getSelectedItem();
        if (sel == null || sel.equals("Todos")) return true;
        if (sel.equals("Progreso")) return type == LogType.PROGRESS;
        if (sel.equals("Errores")) return type == LogType.ERROR;
        return true;
    }
    private void refreshLogView() {
        statusPane.setText("");
        List<LogEntry> entriesCopy = new ArrayList<>(logEntries); // evitar ConcurrentModificationException
        for (LogEntry entry : entriesCopy) {
            if (shouldShow(entry.type)) {
                appendStyled(entry.msg, entry.type);
            }
        }
    }
    private void clearLog() {
        logEntries.clear();
        statusPane.setText("");
    }
    // exportLog eliminado
    private void appendInfo(String msg) { appendStyled("[INFO] ➡ " + msg, LogType.INFO); }
    public void appendProgress(String msg) {
        synchronized (logLock) {
            appendStyled(msg, LogType.PROGRESS);
        }
    }
    public void appendSuccess(String msg) { appendStyled("[ÉXITO] ✔ " + msg, LogType.SUCCESS); }
    private void appendError(String msg) { appendStyled("[ERROR] ✖ " + msg, LogType.ERROR); }
    private void appendWarning(String msg) { appendStyled("[ADVERTENCIA] ⚠ " + msg, LogType.WARNING); }

    // --------- RESET panel de hilos
    private void resetThreadPanel() {
        threadStatusPanel.removeAll();
        threadBars.clear();
        threadTimeLabels.clear();
        threadStartTimes.clear();
        threadTotalRows.clear();
        threadStatusPanel.revalidate();
        threadStatusPanel.repaint();
    }

    // Crea n entradas visuales para hilos en el panel
    private void createThreadEntries(int threads, int rowsPerThread, int n) {
        resetThreadPanel();
        for (int t = 0; t < threads; t++) {
            JPanel p = new JPanel();
            p.setLayout(new BorderLayout(6,6));
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY), new EmptyBorder(6,6,6,6)));

            JLabel lbl = new JLabel("Hilo #" + (t+1));
            lbl.setPreferredSize(new Dimension(80, 18));
            p.add(lbl, BorderLayout.WEST);

            JProgressBar pb = new JProgressBar(0, rowsPerThread);
            pb.setStringPainted(true);
            pb.setValue(0);
            threadBars.add(pb);
            p.add(pb, BorderLayout.CENTER);

            JLabel timeLbl = new JLabel("0 ms");
            timeLbl.setPreferredSize(new Dimension(70, 18));
            p.add(timeLbl, BorderLayout.EAST);
            threadTimeLabels.add(timeLbl);

            // compute actual rows for this thread (last thread may have more)
            int from = t * rowsPerThread;
            int to = Math.min(n, (t+1) * rowsPerThread);
            threadTotalRows.add(Math.max(0, to - from));
            threadStartTimes.add(0L);

            threadStatusPanel.add(p);
            threadStatusPanel.add(Box.createRigidArea(new Dimension(0,6)));
        }
        threadStatusPanel.revalidate();
        threadStatusPanel.repaint();
    }

    private String formatMillis(long ms) {
        if (ms < 1000) return ms + " ms";
        return String.format("%d.%03d s", ms / 1000, ms % 1000);
    }

    /** Ejecuta multiplicaciones secuencial con progreso en tiempo real */
    private void runSequential() {
        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }
        int n = A.length;
        C = new int[n][n];
        progressBar.setMaximum(n);
        progressBar.setValue(0);

        appendInfo("Ejecutando secuencial...\n");

        startTimeSeq = System.currentTimeMillis();

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        int sum = 0;
                        for (int k = 0; k < n; k++) sum += A[i][k] * B[k][j];
                        C[i][j] = sum;
                    }
                    publish(i);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                for (int row : chunks) {
                    progressBar.setValue(progressBar.getValue() + 1);
                    int percent = (int) (100.0 * progressBar.getValue() / progressBar.getMaximum());
                    progressBar.setString(percent + "%");
                    appendProgress("[Secuencial] fila " + (row + 1) + " completada\n");
                }
                display(tblC, C);
                // Mostrar mensaje de éxito solo al finalizar la última fila
                if (progressBar.getValue() == progressBar.getMaximum()) {
                    long end = System.currentTimeMillis();
                    lblTimeSeq.setText("Secuencial: " + (end - startTimeSeq) + " ms");
                    appendSuccess("Ejecución secuencial completada en " + (end - startTimeSeq) + " ms\n");
                }
            }
        };
        worker.execute();
    }

    /** Ejecuta multiplicaciones concurrente con ExecutorService y visualización mejorada */
    private void runConcurrent() {
        int threads;
        try {
            threads = Integer.parseInt(txtThreads.getText().trim());
            if (threads <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Introduce un número válido de hilos (entero > 0).");
            return;
        }

        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }

        int n = A.length;
        if (threads > n) {
            appendInfo("Advertencia: hilos > filas. Ajustando hilos a " + n + " para evitar hilos ociosos.");
            threads = n;
        }

        C = new int[n][n];
        progressBar.setMaximum(n);
        progressBar.setValue(0);
        progressBar.setString("0%");

        int rowsPerThread = (n + threads - 1) / threads; // ceil
        createThreadEntries(threads, rowsPerThread, n);

        appendInfo("Iniciando ejecución concurrente con " + threads + " hilos...\n");

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        long globalStart = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            final int from = t * rowsPerThread;
            final int to = Math.min(n, (t + 1) * rowsPerThread);
            final int threadIndex = t;

            exec.submit(() -> {
                threadStartTimes.set(threadIndex, System.currentTimeMillis());
                appendProgress(String.format("[Concurrente] Hilo #%d INICIA [Filas: %d-%d]\n", threadIndex+1, from+1, to));
                long hiloStart = System.currentTimeMillis();
                try {
                    for (int i = from; i < to; i++) {
                        if ((i - from) % 10 == 0) {
                            appendProgress(String.format("[Concurrente] Hilo #%d fila %d procesando...\n", threadIndex+1, i+1));
                        }
                        for (int j = 0; j < n; j++) {
                            int sum = 0;
                            for (int k = 0; k < n; k++) sum += A[i][k] * B[k][j];
                            C[i][j] = sum;
                        }

                        final int fi = i;
                        SwingUtilities.invokeLater(() -> {
                            // actualizar barra global
                            progressBar.setValue(progressBar.getValue() + 1);
                            int percent = (int) (100.0 * progressBar.getValue() / progressBar.getMaximum());
                            progressBar.setString(percent + "%");

                            // actualizar barra del hilo
                            JProgressBar pb = threadBars.get(threadIndex);
                            int newVal = pb.getValue() + 1;
                            pb.setValue(newVal);
                            int totalForThread = threadTotalRows.get(threadIndex);
                            pb.setString(newVal + "/" + totalForThread);

                            // actualizar tiempo transcurrido del hilo
                            long start = threadStartTimes.get(threadIndex);
                            long elapsed = System.currentTimeMillis() - start;
                            threadTimeLabels.get(threadIndex).setText(formatMillis(elapsed));
                        });
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> appendError("❌ [Concurrente] Hilo #" + (threadIndex + 1) + " ERROR: " + ex.getMessage()));
                } finally {
                    long hiloEnd = System.currentTimeMillis();
                    appendSuccess(String.format("[Concurrente] Hilo #%d TERMINA [Filas: %d-%d] - Tiempo: %.3fs\n", threadIndex+1, from+1, to, (hiloEnd-hiloStart)/1000.0));
                    latch.countDown();
                    SwingUtilities.invokeLater(() -> {
                        // marcar hilo completado si terminó todas sus filas
                        JProgressBar pb = threadBars.get(threadIndex);
                        int totalForThread = threadTotalRows.get(threadIndex);
                        if (pb.getValue() >= totalForThread) {
                            pb.setString("Completado");
                        }
                    });
                }
            });
        }

        exec.shutdown();
        // esperar en background para no bloquear EDT
        new Thread(() -> {
            try {
                if (!latch.await(1, TimeUnit.HOURS)) {
                    SwingUtilities.invokeLater(() -> appendError("Timeout esperando hilos concurrentes"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long globalEnd = System.currentTimeMillis();
            SwingUtilities.invokeLater(() -> {
                lblTimeConc.setText("Concurrente: " + (globalEnd - globalStart) + " ms");
                display(tblC, C);
                appendSuccess("Ejecución concurrente completada en " + (globalEnd - globalStart) + " ms\n");
            });
        }).start();
    }

    /** Ejecuta multiplicaciones paralelo distribuido entre servidores RMI.
     *  Ahora cada servidor puede procesar su segmento internamente con ForkJoin (multiplyBlock).
     *  Además el cliente puede participar como worker local (includeLocal = true).
     */
    private void runParallel() {
        int totalWorkers;
        try {
            totalWorkers = Integer.parseInt(txtThreads.getText().trim());
            if (totalWorkers <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Introduce un número válido de hilos (entero > 0).");
            return;
        }

        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }

        int n = A.length;

        // Pedir lista de servidores al usuario
        String serversStr = JOptionPane.showInputDialog(this,
                "Introduce lista de servidores (IPs o hostnames) separados por comas.\nEj: 192.168.1.10,192.168.1.11\n(Se usará el servicio RMI 'MatrixService' en puerto 1099)\nDejar vacío y pulsar OK para usar solo procesamiento local.\n\nSUGERENCIA: Para matrices grandes, usa chunk >= 100 para mejor rendimiento distribuido.",
                "192.168.100.217");
        if (serversStr == null) {
            appendInfo("Ejecución paralelo cancelada por el usuario.\n");
            return;
        }

        // parsear servidores
        String[] parts = serversStr.split(",");
        final List<ServerInfo> servers = new ArrayList<>();
        for (String p : parts) {
            String host = p.trim();
            if (host.isEmpty()) continue;
            // asumimos puerto 1099 y servicio MatrixService
            servers.add(new ServerInfo(host, 1099, "MatrixService"));
        }

        // Preguntar al usuario si el cliente también procesará localmente una porción
        int resp = JOptionPane.showConfirmDialog(this,
                "¿Deseas que esta máquina cliente procese también una porción local además de los servidores?",
                "Cliente procesando localmente", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        final boolean includeLocal = (resp == JOptionPane.YES_OPTION);

        // Ajustar totalWorkers máximo a n
        if (totalWorkers > n) {
            appendInfo("Advertencia: hilos > filas. Ajustando hilos a " + n + " para evitar hilos ociosos.");
            totalWorkers = n;
        }

        if (servers.isEmpty() && !includeLocal) {
            appendError("No hay servidores especificados y se rechazó procesamiento local. Cancelado.");
            return;
        }

        // NUEVO: obtener hilos del servidor
        final int serverThreadCount;
        int tmpServerThreadCount = 0;
        try {
            tmpServerThreadCount = Integer.parseInt(txtServerThreads.getText().trim());
        } catch (Exception ex) {
            tmpServerThreadCount = 0;
        }
        serverThreadCount = tmpServerThreadCount;

        // Advertencia si solo hay un servidor y el cliente es la misma IP
        if (servers.size() == 1 && includeLocal) {
            String localIp = "127.0.0.1";
            try {
                localIp = java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {}
            if (servers.get(0).host.equals("localhost") || servers.get(0).host.equals(localIp)) {
                appendWarning("Estás usando solo un servidor y el cliente en la misma máquina. No habrá ganancia real de paralelismo distribuido.\n");
            }
        }

        C = new int[n][n];
        progressBar.setMaximum(n);
        progressBar.setValue(0);
        progressBar.setString("0%");

    // Calcular número real de workers asignados en modo 'hilos por endpoint'
    int endpointCount = servers.size() + (includeLocal ? 1 : 0);
    if (endpointCount <= 0) endpointCount = 1;
    final int perEndpointWorkers = Math.max(1, totalWorkers);
    int totalAssignedWorkers = endpointCount * perEndpointWorkers;

    int rowsPerWorker = (n + totalAssignedWorkers - 1) / totalAssignedWorkers; // ceil
        // Crear barras agrupadas por endpoint para cada worker real asignado
        resetThreadPanel();
        for (int e = 0; e < endpointCount; e++) {
            String serverLabel;
            if (e < servers.size()) serverLabel = servers.get(e).host; else serverLabel = "Local";
            JLabel header = new JLabel("Endpoint: " + serverLabel);
            header.setFont(header.getFont().deriveFont(Font.BOLD));
            header.setBorder(new EmptyBorder(4,4,4,4));
            threadStatusPanel.add(header);
            threadStatusPanel.add(Box.createRigidArea(new Dimension(0,4)));

            for (int w = 0; w < perEndpointWorkers; w++) {
                int t = e * perEndpointWorkers + w;
                if (t >= totalAssignedWorkers) break;

                JPanel p = new JPanel();
                p.setLayout(new BorderLayout(6,6));
                p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY), new EmptyBorder(6,6,6,6)));

                JLabel lbl = new JLabel("Bloque #" + (t+1) + " [" + serverLabel + "]");
                lbl.setPreferredSize(new Dimension(160, 18));
                p.add(lbl, BorderLayout.WEST);

                int from = t * rowsPerWorker;
                int to = Math.min(n, (t+1) * rowsPerWorker);
                int actualRows = Math.max(0, to - from);

                JProgressBar pb = new JProgressBar(0, actualRows);
                pb.setStringPainted(true);
                pb.setValue(0);
                threadBars.add(pb);
                p.add(pb, BorderLayout.CENTER);

                JLabel timeLbl = new JLabel("0 ms");
                timeLbl.setPreferredSize(new Dimension(70, 18));
                p.add(timeLbl, BorderLayout.EAST);
                threadTimeLabels.add(timeLbl);

                threadTotalRows.add(actualRows);
                threadStartTimes.add(0L);

                threadStatusPanel.add(p);
                threadStatusPanel.add(Box.createRigidArea(new Dimension(0,6)));
            }
        }
        threadStatusPanel.revalidate();
        threadStatusPanel.repaint();

    // Usar el número real de workers para sincronizar con el callback
    final int finalTotalWorkers = totalAssignedWorkers;

        appendInfo("Iniciando ejecución paralelo distribuido con " + finalTotalWorkers + " hilos locales y remotos...\n");

        ParallelMultiplier pm = new ParallelMultiplier();
        long startTime = System.currentTimeMillis();

        ProgressCallback cb = new ProgressCallback() {

            @Override
            public void onChunkCompleted(int workerIndex, int endpointIndex, int rowsCompletedForWorker,
                                         int rowsTotalForWorker, int globalCompleted, int globalTotal) {
                SwingUtilities.invokeLater(() -> {
                    // Actualizar barra global
                    progressBar.setValue(globalCompleted);
                    int percent = (int) (100.0 * progressBar.getValue() / progressBar.getMaximum());
                    progressBar.setString(percent + "%");

                    // Etiqueta de servidor
                    String serverLabel;
                    if (endpointIndex < servers.size()) {
                        serverLabel = servers.get(endpointIndex).host;
                    } else {
                        serverLabel = "Local";
                    }

                    // Actualizar barra del bloque correspondiente
                    if (workerIndex < threadBars.size()) {
                        JProgressBar pb = threadBars.get(workerIndex);
                        int newVal = Math.min(pb.getMaximum(), rowsCompletedForWorker);
                        pb.setValue(newVal);
                        pb.setString(newVal + "/" + threadTotalRows.get(workerIndex) + " [" + serverLabel + "]");
                    }

                    // Actualizar tiempo estimado del bloque
                    if (workerIndex < threadTimeLabels.size()) {
                        if (threadStartTimes.get(workerIndex) == 0L) threadStartTimes.set(workerIndex, System.currentTimeMillis());
                        long elapsed = System.currentTimeMillis() - threadStartTimes.get(workerIndex);
                        threadTimeLabels.get(workerIndex).setText(formatMillis(elapsed));
                    }

                    // Prefijo [Paralelo] y servidor en logs
                    appendProgress(String.format("[Paralelo][%s] Bloque #%d filas %d/%d procesando...\n", serverLabel, workerIndex+1, rowsCompletedForWorker, rowsTotalForWorker));
                    display(tblC, C);
                });
            }

            @Override
            public void onWorkerStarted(int workerIndex, int endpointIndex, int startRow, int endRow) {
                SwingUtilities.invokeLater(() -> {
                    // Calcular el número de hilo local para este endpoint (1-N)
                    int localThreadNum = (workerIndex % perEndpointWorkers) + 1;
                    String endpointLabel = (endpointIndex < servers.size()) ? servers.get(endpointIndex).host : "Local";
                    
                    appendProgress(String.format("[Paralelo][%s] Hilo #%d INICIA [Filas: %d-%d]\n", endpointLabel, localThreadNum, startRow+1, endRow));
                    if (workerIndex < threadStartTimes.size()) threadStartTimes.set(workerIndex, System.currentTimeMillis());
                    // Actualizar el máximo y total de filas con el valor real calculado por ParallelMultiplier
                    int actualRows = endRow - startRow;
                    if (workerIndex < threadBars.size()) {
                        JProgressBar pb = threadBars.get(workerIndex);
                        pb.setMaximum(actualRows);
                    }
                    if (workerIndex < threadTotalRows.size()) {
                        threadTotalRows.set(workerIndex, actualRows);
                    }
                });
            }

            @Override
            public void onWorkerFinished(int workerIndex, int endpointIndex, long serverProcessingTimeMillis) {
                SwingUtilities.invokeLater(() -> {
                    // Calcular el número de hilo local para este endpoint (1-N)
                    int localThreadNum = (workerIndex % perEndpointWorkers) + 1;
                    String endpointLabel = (endpointIndex < servers.size()) ? servers.get(endpointIndex).host : "Local";
                    
                    // Mostrar el tiempo de procesamiento del servidor
                    if (workerIndex < threadTimeLabels.size()) {
                        threadTimeLabels.get(workerIndex).setText(formatMillis(serverProcessingTimeMillis));
                    }
                    
                    appendSuccess(String.format("[Paralelo][%s] Hilo #%d TERMINA - Tiempo: %.3fs\n", endpointLabel, localThreadNum, serverProcessingTimeMillis / 1000.0));
                    if (workerIndex < threadBars.size()) {
                        JProgressBar pb = threadBars.get(workerIndex);
                        pb.setString("Completado");
                    }
                });
            }
        };

        // Ejecutar en background
        new Thread(() -> {
            try {
                int[][] result = pm.multiplyDistributed(A, B, servers, finalTotalWorkers, cb, includeLocal, serverThreadCount);
                long endTime = System.currentTimeMillis();
                SwingUtilities.invokeLater(() -> {
                    lblTimePar.setText("Paralelo: " + (endTime - startTime) + " ms");
                    display(tblC, result);
                    appendSuccess("Ejecución paralelo distribuido completada en " + (endTime - startTime) + " ms\n");
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> appendError("Error en ejecución paralelo: " + ex.getMessage() + "\n"));
            }
        }).start();
    }

    /** Muestra la matriz completa en una nueva ventana con scroll, zebra y resaltados */
    private void showFullMatrix(int[][] M, String title) {
        if (M == null) return;
        int n = M.length;

        String[] cols = new String[n + 1];
        cols[0] = "#";
        for (int j = 1; j <= n; j++) cols[j] = String.valueOf(j);

        Object[][] data = new Object[n][n + 1];
        for (int i = 0; i < n; i++) {
            data[i][0] = (i + 1);
            for (int j = 0; j < n; j++) data[i][j + 1] = M[i][j];
        }

        JTable table = new JTable(new DefaultTableModel(data, cols));
        table.setFont(new Font("Monospaced", Font.PLAIN, 14));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(22);

        for (int i = 0; i < table.getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(40);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lbl.setHorizontalAlignment(JLabel.CENTER);
                lbl.setBackground(row % 2 == 0 ? new Color(230, 230, 250) : Color.WHITE);
                if (row == column - 1) lbl.setBackground(new Color(144, 238, 144));
                return lbl;
            }
        });

        JFrame f = new JFrame(title);
        f.add(new JScrollPane(table));
        f.setSize(900, 700);
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    private JPanel wrapTitled(String title, JScrollPane tableScroll, JButton btnView) {
		JPanel pnl = new JPanel();
		pnl.setLayout(new BorderLayout(6,6));
		pnl.setBorder(BorderFactory.createTitledBorder(title));
		pnl.add(tableScroll, BorderLayout.CENTER);
		JPanel pnlBtn = new JPanel();
		pnlBtn.add(btnView);
		pnl.add(pnlBtn, BorderLayout.SOUTH);
		pnl.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));
		return pnl;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new AppGUI().setVisible(true));
	}
}
