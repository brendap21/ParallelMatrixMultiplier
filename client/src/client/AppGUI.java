package client;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.Random;
import java.util.concurrent.*;

/**
 * GUI principal para probar la multiplicación de matrices
 * en modo secuencial, concurrente y paralelo.
 */
public class AppGUI extends JFrame {
    private int[][] A, B, C;
    private JTable tblA, tblB, tblC;
    private JTextArea statusArea;
    private JProgressBar progressBar;
    private JTextField txtSize, txtThreads;
    private JLabel lblTimeSeq, lblTimeConc, lblTimePar;

    public AppGUI() {
        super("Multiplicador de Matrices");

        setLayout(new BorderLayout(5, 5));

        // --------- TOP: controles principales ----------
        JPanel pnlTop = new JPanel(new FlowLayout());
        pnlTop.add(new JLabel("Tamaño (n):"));
        txtSize = new JTextField("10", 5);
        pnlTop.add(txtSize);

        pnlTop.add(new JLabel("Hilos:"));
        txtThreads = new JTextField("4", 5);
        pnlTop.add(txtThreads);

        JButton btnGen = new JButton("Generar Matrices");
        JButton btnSeq = new JButton("Secuencial");
        JButton btnConc = new JButton("Concurrente");
        JButton btnPar = new JButton("Paralelo");
        pnlTop.add(btnGen);
        pnlTop.add(btnSeq);
        pnlTop.add(btnConc);
        pnlTop.add(btnPar);

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

        JPanel pnlCenter = new JPanel(new GridLayout(1, 3));
        pnlCenter.add(wrapTitled("Matriz A", spA, btnViewA));
        pnlCenter.add(wrapTitled("Matriz B", spB, btnViewB));
        pnlCenter.add(wrapTitled("Matriz C", spC, btnViewC));
        add(pnlCenter, BorderLayout.CENTER);

        // --------- SOUTH: barra de progreso, tiempos y logs ----------
        JPanel pnlBottom = new JPanel(new BorderLayout(5, 5));

        progressBar = new JProgressBar();
        pnlBottom.add(progressBar, BorderLayout.NORTH);

        JPanel pnlTimes = new JPanel(new GridLayout(1, 3));
        lblTimeSeq = new JLabel("Secuencial: - ms", JLabel.CENTER);
        lblTimeConc = new JLabel("Concurrente: - ms", JLabel.CENTER);
        lblTimePar = new JLabel("Paralelo: - ms", JLabel.CENTER);
        pnlTimes.add(lblTimeSeq);
        pnlTimes.add(lblTimeConc);
        pnlTimes.add(lblTimePar);
        pnlBottom.add(pnlTimes, BorderLayout.CENTER);

        statusArea = new JTextArea(8, 40);
        statusArea.setEditable(false);
        pnlBottom.add(new JScrollPane(statusArea), BorderLayout.SOUTH);

        add(pnlBottom, BorderLayout.SOUTH);

        // --------- ACCIONES ----------
        btnGen.addActionListener(e -> generateMatrices());
        btnSeq.addActionListener(e -> runSequential());
        btnConc.addActionListener(e -> runConcurrent());
        btnPar.addActionListener(e -> runParallel());

        btnViewA.addActionListener(e -> showFullMatrix(A, "Matriz A"));
        btnViewB.addActionListener(e -> showFullMatrix(B, "Matriz B"));
        btnViewC.addActionListener(e -> showFullMatrix(C, "Matriz C"));

        setSize(1100, 650);
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
        statusArea.append("Matrices generadas de " + n + "x" + n + "\n");
        display(tblA, A);
        display(tblB, B);
        display(tblC, null);
    }

    /** Muestra una tabla con numeración y previsualización de 10x10 */
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
            for (int j = 0; j < preview; j++)
                data[i][j + 1] = M[i][j];
        }

        DefaultTableModel model = new DefaultTableModel(data, cols);
        tbl.setModel(model);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < tbl.getColumnCount(); i++)
            tbl.getColumnModel().getColumn(i).setCellRenderer(center);
        tbl.setRowHeight(22);
    }

    /** Ejecuta multiplicación secuencial con progreso en tiempo real */
    private void runSequential() {
        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            long startTime;
            @Override
            protected Void doInBackground() {
                int n = A.length;
                C = new int[n][n];
                progressBar.setMaximum(n);
                progressBar.setValue(0);
                startTime = System.currentTimeMillis();

                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        int sum = 0;
                        for (int k = 0; k < n; k++)
                            sum += A[i][k] * B[k][j];
                        C[i][j] = sum;
                    }
                    publish(i + 1); // fila completada
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last);
                statusArea.append("Secuencial: fila " + last + " completada\n");
            }

            @Override
            protected void done() {
                long t = System.currentTimeMillis() - startTime;
                lblTimeSeq.setText("Secuencial: " + t + " ms");
                display(tblC, C);
                statusArea.append("Secuencial completado en " + t + " ms\n");
            }
        };
        worker.execute();
    }

    /** Ejecuta multiplicación concurrente con progreso en tiempo real */
    private void runConcurrent() {
        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }
        int threads = Integer.parseInt(txtThreads.getText().trim());
        long startTime = System.currentTimeMillis();
        C = multiplyConcurrent(A, B, threads, "Concurrente");
        long t = System.currentTimeMillis() - startTime;
        lblTimeConc.setText("Concurrente: " + t + " ms");
        display(tblC, C);
    }

    /** Ejecuta multiplicación paralela con progreso en tiempo real */
    private void runParallel() {
        if (A == null || B == null) {
            JOptionPane.showMessageDialog(this, "Primero genera las matrices.");
            return;
        }
        int threads = Integer.parseInt(txtThreads.getText().trim());
        long startTime = System.currentTimeMillis();
        C = multiplyParallel(A, B, threads, "Paralelo");
        long t = System.currentTimeMillis() - startTime;
        lblTimePar.setText("Paralelo: " + t + " ms");
        display(tblC, C);
    }

    /** Multiplicación concurrente con hilos manuales */
    private int[][] multiplyConcurrent(int[][] A, int[][] B, int threads, String tag) {
        int n = A.length;
        int[][] C = new int[n][n];
        Thread[] workers = new Thread[threads];
        int rowsPerThread = n / threads;

        progressBar.setMaximum(n);
        progressBar.setValue(0);

        for (int t = 0; t < threads; t++) {
            final int from = t * rowsPerThread;
            final int to = (t == threads - 1) ? n : (t + 1) * rowsPerThread;
            workers[t] = new Thread(() -> {
                for (int i = from; i < to; i++) {
                    for (int j = 0; j < n; j++) {
                        int sum = 0;
                        for (int k = 0; k < n; k++) sum += A[i][k] * B[k][j];
                        C[i][j] = sum;
                    }
                    int fi = i;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progressBar.getValue() + 1);
                        statusArea.append(tag + ": fila " + (fi + 1) + " completada\n");
                    });
                }
            });
            workers[t].start();
        }
        for (Thread w : workers) {
            try { w.join(); } catch (InterruptedException ignored) {}
        }
        return C;
    }

    /** Multiplicación paralela con ExecutorService */
    private int[][] multiplyParallel(int[][] A, int[][] B, int threads, String tag) {
        int n = A.length;
        int[][] C = new int[n][n];
        ExecutorService exec = Executors.newFixedThreadPool(threads);

        progressBar.setMaximum(n);
        progressBar.setValue(0);

        for (int i = 0; i < n; i++) {
            final int row = i;
            exec.submit(() -> {
                for (int j = 0; j < n; j++) {
                    int sum = 0;
                    for (int k = 0; k < n; k++) sum += A[row][k] * B[k][j];
                    C[row][j] = sum;
                }
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(progressBar.getValue() + 1);
                    statusArea.append(tag + ": fila " + (row + 1) + " completada\n");
                });
            });
        }
        exec.shutdown();
        try { exec.awaitTermination(1, TimeUnit.HOURS); } catch (InterruptedException ignored) {}
        return C;
    }

    /** Muestra la matriz completa en una nueva ventana */
    private void showFullMatrix(int[][] M, String title) {
        if (M == null) return;
        int n = M.length;

        String[] cols = new String[n + 1];
        cols[0] = "#";
        for (int j = 1; j <= n; j++) cols[j] = String.valueOf(j);

        Object[][] data = new Object[n][n + 1];
        for (int i = 0; i < n; i++) {
            data[i][0] = (i + 1);
            for (int j = 0; j < n; j++)
                data[i][j + 1] = M[i][j];
        }

        JTable table = new JTable(new DefaultTableModel(data, cols));
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setCellRenderer(center);
        table.setRowHeight(22);

        JFrame frame = new JFrame(title);
        frame.add(new JScrollPane(table));
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
    }

    private JPanel wrapTitled(String title, JScrollPane scroll, JButton btn) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(scroll, BorderLayout.CENTER);
        if (btn != null) {
            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            south.add(btn);
            panel.add(south, BorderLayout.SOUTH);
        }
        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppGUI().setVisible(true));
    }
}
