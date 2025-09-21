package client;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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
        btnRunSeq.addActionListener(e -> runSequential());
        btnRunConc.addActionListener(e -> runConcurrent());
        btnRunPar.addActionListener(e -> runParallel());

        btnViewA.addActionListener(e -> showFullMatrix(A, "Matriz A"));
        btnViewB.addActionListener(e -> showFullMatrix(B, "Matriz B"));
        btnViewC.addActionListener(e -> showFullMatrix(C, "Matriz C"));

        setSize(1200, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    /** Genera matrices A y B con números aleatorios */
    private void generateMatrices() {
        int n = Integer.parseInt(txtSize.getText().trim());
        Random rnd = new Random();
        A = new int[n][n];
        B = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] = rnd.nextInt(10);
                B[i][j] = rnd.nextInt(10);
            }
        }
        statusArea.append("Matrices generadas de " + n + "x" + n + "\n");
        display(tblA, A);
        display(tblB, B);
        display(tblC, null);
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

        // Ajustar ancho de columnas según contenido
        for (int i = 0; i < tbl.getColumnCount(); i++) {
            TableColumn col = tbl.getColumnModel().getColumn(i);
            col.setPreferredWidth(40);
        }

        // Render personalizado
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

                // Zebra
                lbl.setBackground(row % 2 == 0 ? new Color(230, 230, 250) : Color.WHITE);

                // Diagonal principal
                if (row == column - 1) lbl.setBackground(new Color(144, 238, 144));

                // Valores altos o mínimos
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
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                for (int row : chunks) {
                    progressBar.setValue(progressBar.getValue() + 1);
                    statusArea.append("Secuencial: fila " + (row + 1) + " completada\n");
                }
                display(tblC, C);
            }
        };
        worker.execute();
    }

    /** Ejecuta multiplicaciones concurrente con hilos manuales */
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

        Thread[] workers = new Thread[threads];
        int rowsPerThread = n / threads;

        long start = System.currentTimeMillis();
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
                        statusArea.append("Concurrente: fila " + (fi + 1) + " completada\n");
                    });
                }
            });
            workers[t].start();
        }
        for (Thread w : workers) {
            try { w.join(); } catch (InterruptedException ignored) {}
        }
        long end = System.currentTimeMillis();
        lblTimeConc.setText("Concurrente: " + (end - start) + " ms");
        display(tblC, C);
    }

    /** Ejecuta multiplicaciones paralelo con ExecutorService */
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

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        long start = System.currentTimeMillis();
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
                    statusArea.append("Paralelo: fila " + (row + 1) + " completada\n");
                });
            });
        }
        exec.shutdown();
        try { exec.awaitTermination(1, TimeUnit.HOURS); } catch (InterruptedException ignored) {}
        long end = System.currentTimeMillis();
        lblTimePar.setText("Paralelo: " + (end - start) + " ms");
        display(tblC, C);
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

        // Ajuste ancho de columnas
        for (int i = 0; i < table.getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(40);

        // Render personalizado
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
                if (value instanceof Integer) {
                    int val = (Integer) value;
                    if (val >= 8) lbl.setForeground(Color.RED);
                    else if (val == 0) lbl.setForeground(Color.GRAY);
                    else lbl.setForeground(Color.BLACK);
                }
                return lbl;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JFrame frame = new JFrame(title);
        frame.add(scrollPane);
        frame.setSize(800, 600);
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
