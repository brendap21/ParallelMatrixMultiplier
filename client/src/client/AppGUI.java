package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Random;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;

/**
 * GUI principal para probar la multiplicación de matrices
 * en modo secuencial, concurrente y paralelo.
 *
 * Mejora la visualización de hilos: barras horizontales etiquetadas,
 * tiempos por hilo, consola con colores (JTextPane) y barra global con porcentaje.
 */
public class AppGUI extends JFrame {
    private int[][] A, B, C;
    private JTable tblA, tblB, tblC;
    private JTextPane statusPane; // ahora JTextPane para colores
    private StyledDocument statusDoc;
    private JProgressBar progressBar;
    private JTextField txtSize, txtThreads;
    private JLabel lblTimeSeq, lblTimeConc, lblTimePar;

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

        setLayout(new BorderLayout(6, 6));

        // --------- TOP: controles principales ----------
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        pnlTop.add(new JLabel("Tamaño (n):"));
        txtSize = new JTextField("10", 5);
        pnlTop.add(txtSize);

        pnlTop.add(new JLabel("Hilos:"));
        txtThreads = new JTextField("4", 5);
        pnlTop.add(txtThreads);

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
        threadScroll.setPreferredSize(new Dimension(320, 260));
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
        statusPane = new JTextPane();
        statusPane.setEditable(true); // copiable
        statusPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        statusDoc = statusPane.getStyledDocument();
        statusPane.addCaretListener(e -> {
            JScrollBar vbar = ((JScrollPane)statusPane.getParent().getParent()).getVerticalScrollBar();
            autoScroll = vbar.getValue() + vbar.getVisibleAmount() >= vbar.getMaximum();
        });
        JScrollPane logScroll = new JScrollPane(statusPane);
        logScroll.setPreferredSize(new Dimension(520, 260));

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
        logPanel.setPreferredSize(new Dimension(520, 260));
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
    private void appendProgress(String msg) { appendStyled("[PROGRESO] " + msg, LogType.PROGRESS); }
    private void appendSuccess(String msg) { appendStyled("[ÉXITO] ✔ " + msg, LogType.SUCCESS); }
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
            appendInfo("Advertencia: hilos > filas. Ajustando hilos a " + n + " para evitar hilos ociosos.\n");
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
                try {
                    for (int i = from; i < to; i++) {
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

                            appendProgress("[Hilo #" + (threadIndex + 1) + "] fila " + (fi + 1) + " completada\n");
                        });
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> appendError("[Hilo #" + (threadIndex + 1) + "] ERROR: " + ex.getMessage() + "\n"));
                } finally {
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
                    SwingUtilities.invokeLater(() -> appendError("Timeout esperando hilos concurrentes\n"));
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

    /** Ejecuta multiplicaciones paralelo con ExecutorService (local o distribuido) */
    private void runParallel() {
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
            appendInfo("Advertencia: hilos > filas. Ajustando hilos a " + n + " para evitar hilos ociosos.\n");
            threads = n;
        }

        C = new int[n][n];
        progressBar.setMaximum(n);
        progressBar.setValue(0);
        progressBar.setString("0%");

        int rowsPerThread = (n + threads - 1) / threads; // ceil
        createThreadEntries(threads, rowsPerThread, n);

        appendInfo("Iniciando ejecución paralelo (ExecutorService local) con " + threads + " hilos...\n");

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        long globalStart = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            final int row = t; // cada tarea toma una fila a la vez en esta estrategia local
            final int startRow = t * rowsPerThread;
            final int endRow = Math.min(n, (t + 1) * rowsPerThread);
            final int threadIndex = t;

            exec.submit(() -> {
                threadStartTimes.set(threadIndex, System.currentTimeMillis());
                try {
                    for (int i = startRow; i < endRow; i++) {
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

                            appendProgress("[Paralelo Hilo #" + (threadIndex + 1) + "] fila " + (fi + 1) + " completada\n");
                        });
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> appendError("[Paralelo Hilo #" + (threadIndex + 1) + "] ERROR: " + ex.getMessage() + "\n"));
                } finally {
                    latch.countDown();
                    SwingUtilities.invokeLater(() -> {
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
        new Thread(() -> {
            try {
                if (!latch.await(1, TimeUnit.HOURS)) {
                    SwingUtilities.invokeLater(() -> appendError("Timeout esperando hilos paralelos\n"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long globalEnd = System.currentTimeMillis();
            SwingUtilities.invokeLater(() -> {
                lblTimePar.setText("Paralelo: " + (globalEnd - globalStart) + " ms");
                display(tblC, C);
                appendSuccess("Ejecución paralelo completada en " + (globalEnd - globalStart) + " ms\n");
            });
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
