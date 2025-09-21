package client;

import javax.swing.*;
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
 * en modo secuencial, concurrente y paralelo (Fork/Join).
 */
public class AppGUI extends JFrame {
    private int[][] A, B, C;
    private JTable tblA, tblB, tblC;
    private JTextPane statusArea;
    private JProgressBar progressBar;
    private JTextField txtSize, txtThreads;
    private JLabel lblTimeSeq, lblTimeConc, lblTimePar;
    private JPanel threadStatusPanel;
    private List<JProgressBar> threadBars;
    private List<JLabel> threadTimes;
    private long[] startTimes;

    public AppGUI() {
        super("Multiplicador de Matrices");

        setLayout(new BorderLayout(5, 5));

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
        JPanel pnlBottom = new JPanel(new BorderLayout(5,5));
        progressBar = new JProgressBar();
        pnlBottom.add(progressBar, BorderLayout.NORTH);

        // Panel de hilos, tiempos y consola en la misma fila
        JPanel pnlStatusRow = new JPanel();
        pnlStatusRow.setLayout(new BoxLayout(pnlStatusRow, BoxLayout.X_AXIS));

        // PANEL 1: estado de hilos
        threadStatusPanel = new JPanel();
        threadStatusPanel.setLayout(new BoxLayout(threadStatusPanel, BoxLayout.Y_AXIS));
        JScrollPane threadScroll = new JScrollPane(threadStatusPanel);
        threadScroll.setPreferredSize(new Dimension(150, 300));
        pnlStatusRow.add(threadScroll);

        // PANEL 2: tiempos de ejecución
        JPanel pnlTimes = new JPanel(new GridLayout(3,1,5,5));
        lblTimeSeq = new JLabel("Secuencial: - ms", JLabel.CENTER);
        lblTimeConc = new JLabel("Concurrente: - ms", JLabel.CENTER);
        lblTimePar = new JLabel("Paralelo: - ms", JLabel.CENTER);
        pnlTimes.add(lblTimeSeq);
        pnlTimes.add(lblTimeConc);
        pnlTimes.add(lblTimePar);
        pnlTimes.setMaximumSize(new Dimension(200, 300));
        pnlStatusRow.add(Box.createRigidArea(new Dimension(10,0)));
        pnlStatusRow.add(pnlTimes);

        // PANEL 3: consola/logs
        statusArea = new JTextPane();
        statusArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(statusArea);
        logScroll.setPreferredSize(new Dimension(400, 300));
        pnlStatusRow.add(Box.createRigidArea(new Dimension(10,0)));
        pnlStatusRow.add(logScroll);

        pnlBottom.add(pnlStatusRow, BorderLayout.CENTER);
        pnlBottom.setPreferredSize(new Dimension(0, 300));

        add(pnlBottom, BorderLayout.SOUTH);

        // --------- ACCIONES ----------
        btnGen.addActionListener(e -> generateMatrices());
        btnRunSeq.addActionListener(e -> runSequential());
        btnRunConc.addActionListener(e -> runConcurrent());
        btnRunPar.addActionListener(e -> runParallel());

        btnViewA.addActionListener(e -> showFullMatrix(A, "Matriz A"));
        btnViewB.addActionListener(e -> showFullMatrix(B, "Matriz B"));
        btnViewC.addActionListener(e -> showFullMatrix(C, "Matriz C"));

        setSize(1200, 700);
        setMinimumSize(new Dimension(1000, 600));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    /** Genera matrices A y B con números aleatorios */
    private void generateMatrices() {
        int n = Integer.parseInt(txtSize.getText().trim());
        Random rnd = new Random();
        A = new int[n][n];
        B = new int[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                A[i][j] = rnd.nextInt(10);
                B[i][j] = rnd.nextInt(10);
            }
        appendLog("Matrices generadas de " + n + "x" + n, Color.BLUE);
        display(tblA, A);
        display(tblB, B);
        display(tblC, null);
    }

    /** Muestra una tabla con previsualización 10x10, zebra y resaltado */
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

    /** Ejecuta multiplicaciones secuencial */
    private void runSequential() {
        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }
        int n = A.length;
        C = new int[n][n];
        progressBar.setMaximum(n);
        progressBar.setValue(0);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                long start = System.currentTimeMillis();
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        int sum = 0;
                        for (int k = 0; k < n; k++) sum += A[i][k] * B[k][j];
                        C[i][j] = sum;
                    }
                    publish(i);
                }
                long end = System.currentTimeMillis();
                lblTimeSeq.setText("Secuencial: " + (end - start) + " ms");
                appendLog("Secuencial completada en " + (end - start) + " ms", new Color(0,128,0));
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                for (int row : chunks) {
                    progressBar.setValue(progressBar.getValue() + 1);
                    appendLog("Secuencial: fila " + (row + 1) + " completada", Color.DARK_GRAY);
                }
                display(tblC, C);
            }
        };
        worker.execute();
    }

    /** Ejecuta multiplicaciones concurrente con hilos */
    private void runConcurrent() {
        int threads = Integer.parseInt(txtThreads.getText().trim());
        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }
        int n = A.length;
        C = new int[n][n];
        progressBar.setMaximum(n);
        progressBar.setValue(0);

        threadStatusPanel.removeAll();
        threadBars = new ArrayList<>();
        threadTimes = new ArrayList<>();
        startTimes = new long[threads];

        int rowsPerThread = (threads > n) ? 1 : n / threads;

        for (int t = 0; t < threads; t++) {
            JPanel pnlThread = new JPanel(new BorderLayout(5,2));
            JLabel lbl = new JLabel("Hilo #" + (t + 1));
            JProgressBar bar = new JProgressBar(0, (t == threads - 1) ? n - t*rowsPerThread : rowsPerThread);
            bar.setStringPainted(true);
            JLabel lblTime = new JLabel("0 ms", JLabel.RIGHT);

            pnlThread.add(lbl, BorderLayout.WEST);
            pnlThread.add(bar, BorderLayout.CENTER);
            pnlThread.add(lblTime, BorderLayout.EAST);

            threadBars.add(bar);
            threadTimes.add(lblTime);
            startTimes[t] = System.currentTimeMillis();

            threadStatusPanel.add(pnlThread);
        }

        threadStatusPanel.revalidate();
        threadStatusPanel.repaint();

        Thread[] workers = new Thread[threads];
        long startGlobal = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            final int from = t * rowsPerThread;
            final int to = (t == threads - 1) ? n : (t + 1) * rowsPerThread;

            workers[t] = new Thread(() -> {
                for (int i = from; i < to; i++) {
                    for (int j = 0; j < n; j++) {
                        int sum = 0;
                        for (int k = 0; k < n; k++) sum += A[i][k] * B[k][j];
                        C[i][j] = sum;
                    }
                    final int fi = i;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progressBar.getValue() + 1);
                        threadBars.get(threadIndex).setValue(threadBars.get(threadIndex).getValue() + 1);
                        long elapsed = System.currentTimeMillis() - startTimes[threadIndex];
                        threadTimes.get(threadIndex).setText(elapsed + " ms");
                        appendLog("[Hilo #" + (threadIndex+1) + "] fila " + (fi+1) + " completada", Color.ORANGE);
                    });
                }
            });
            workers[t].start();
        }

        new Thread(() -> {
            for (Thread w : workers) {
                try { w.join(); } catch (InterruptedException ignored) {}
            }
            long endGlobal = System.currentTimeMillis();
            lblTimeConc.setText("Concurrente: " + (endGlobal - startGlobal) + " ms");
            appendLog("Concurrente completada en " + (endGlobal - startGlobal) + " ms", new Color(0,128,0));
            display(tblC, C);
        }).start();
    }

    /** Ejecuta multiplicaciones paralelo con ForkJoinPool real */
   private void runParallel() {
    int threads = Integer.parseInt(txtThreads.getText().trim());
        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }
        int n = A.length;
        C = new int[n][n];
        progressBar.setMaximum(n);
        progressBar.setValue(0);

        // Inicializa panel de hilos
        threadStatusPanel.removeAll();
        threadBars = new ArrayList<>();
        threadTimes = new ArrayList<>();
        startTimes = new long[threads];
        for (int t = 0; t < threads; t++) {
            JPanel pnlThread = new JPanel(new BorderLayout(5,2));
            JLabel lbl = new JLabel("Hilo #" + (t + 1));
            JProgressBar bar = new JProgressBar(0, n / threads + (t == threads - 1 ? n % threads : 0));
            bar.setStringPainted(true);
            JLabel lblTime = new JLabel("0 ms", JLabel.RIGHT);

            pnlThread.add(lbl, BorderLayout.WEST);
            pnlThread.add(bar, BorderLayout.CENTER);
            pnlThread.add(lblTime, BorderLayout.EAST);

            threadBars.add(bar);
            threadTimes.add(lblTime);
            startTimes[t] = System.currentTimeMillis();

            threadStatusPanel.add(pnlThread);
        }
        threadStatusPanel.revalidate();
        threadStatusPanel.repaint();

        ForkJoinPool pool = new ForkJoinPool(threads);

        class MultiplyTask extends RecursiveAction {
            int from, to, threadIndex;
            final int THRESHOLD = 10; // filas por subtarea

            MultiplyTask(int from, int to, int threadIndex) {
                this.from = from;
                this.to = to;
                this.threadIndex = threadIndex;
            }

            @Override
            protected void compute() {
                if (to - from <= THRESHOLD) {
                    // Ejecuta directamente
                    for (int i = from; i < to; i++) {
                        for (int j = 0; j < n; j++) {
                            int sum = 0;
                            for (int k = 0; k < n; k++) sum += A[i][k] * B[k][j];
                            C[i][j] = sum;
                        }
                        final int fi = i;
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(progressBar.getValue() + 1);
                            threadBars.get(threadIndex).setValue(threadBars.get(threadIndex).getValue() + 1);
                            long elapsed = System.currentTimeMillis() - startTimes[threadIndex];
                            threadTimes.get(threadIndex).setText(elapsed + " ms");
                            appendLog("[Hilo #" + (threadIndex + 1) + "] fila " + (fi + 1) + " completada", Color.ORANGE);
                        });
                    }
                } else {
                    // Divide en dos subtareas
                    int mid = (from + to) / 2;
                    invokeAll(new MultiplyTask(from, mid, threadIndex),
                            new MultiplyTask(mid, to, threadIndex));
                }
            }
        }

        long startGlobal = System.currentTimeMillis();
        MultiplyTask rootTask = new MultiplyTask(0, n, 0); // threadIndex=0 solo para barra global
        pool.invoke(rootTask);
        long endGlobal = System.currentTimeMillis();
        lblTimePar.setText("Paralelo (ForkJoin): " + (endGlobal - startGlobal) + " ms");
        appendLog("Paralelo completado en " + (endGlobal - startGlobal) + " ms", new Color(0,128,0));
        display(tblC, C);
    }

    /** Muestra matriz completa en ventana independiente */
    private void showFullMatrix(int[][] M, String title) {
        if (M == null) return;
        JFrame f = new JFrame(title);
        JTable tbl = new JTable(M.length, M.length);
        display(tbl, M);
        f.add(new JScrollPane(tbl));
        f.setSize(500, 500);
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    /** Panel con título y botón */
    private JPanel wrapTitled(String title, JComponent comp, JButton btn) {
        JPanel pnl = new JPanel(new BorderLayout(5,5));
        pnl.setBorder(BorderFactory.createTitledBorder(title));
        pnl.add(comp, BorderLayout.CENTER);
        pnl.add(btn, BorderLayout.SOUTH);
        pnl.setPreferredSize(new Dimension(350,350));
        return pnl;
    }

    /** Añade mensaje al log con color */
    private void appendLog(String msg, Color c) {
        StyledDocument doc = statusArea.getStyledDocument();
        Style style = statusArea.addStyle("ColorStyle", null);
        StyleConstants.setForeground(style, c);
        try { doc.insertString(doc.getLength(), msg + "\n", style); } catch(Exception e){}
        statusArea.setCaretPosition(doc.getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppGUI().setVisible(true));
    }
}
