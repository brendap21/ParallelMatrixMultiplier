package client;

import shared.MatrixMultiplier;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.table.*;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AppGUI extends JFrame {
    private static final int MAX_PREVIEW = 10;

    // Dimensiones hasta 50 000
    private final JSpinner spRowsA       = new JSpinner(new SpinnerNumberModel(100, 1, 50_000, 1));
    private final JSpinner spColsA       = new JSpinner(new SpinnerNumberModel(100, 1, 50_000, 1));
    private final JSpinner spRowsB       = new JSpinner(new SpinnerNumberModel(100, 1, 50_000, 1));
    private final JSpinner spColsB       = new JSpinner(new SpinnerNumberModel(100, 1, 50_000, 1));
    // Hilos dinámicos para concurrente local y para paralelismo remoto
    private final JSpinner spThreadsConc = new JSpinner(new SpinnerNumberModel(4, 1, Runtime.getRuntime().availableProcessors()*2, 1));
    private final JSpinner spThreadsPar  = new JSpinner(new SpinnerNumberModel(2, 1, Runtime.getRuntime().availableProcessors()*2, 1));

    // Botones y tablas de preview
    private final JButton btnGenerate = new JButton("Generar matrices");
    private final JButton btnViewA    = new JButton("Ver completa A");
    private final JButton btnViewB    = new JButton("Ver completa B");
    private final JButton btnViewC    = new JButton("Ver completa C");
    private final JButton btnMultiply = new JButton("Multiplicar");
    private final JTable  tblA        = new JTable();
    private final JTable  tblB        = new JTable();
    private final JTable  tblC        = new JTable();

    // Modos
    private final JRadioButton rbSeq  = new JRadioButton("Secuencial", true);
    private final JRadioButton rbConc = new JRadioButton("Concurrente");
    private final JRadioButton rbPar  = new JRadioButton("Paralelo");

    // Historial y progreso
    private final DefaultTableModel statusModel =
        new DefaultTableModel(new Object[]{"Hilo","Estado"}, 0);
    private final JTable       statusTable = new JTable(statusModel);
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    // Datos
    private int[][] A, B, C;
    private ParallelMultiplier paral;  // nuestro distribuidor RMI

    public AppGUI() {
        super("MultiplicadorMatrices");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6,6));

        // colsA → rowsB
        spColsA.addChangeListener((ChangeListener)e ->
            spRowsB.setValue(spColsA.getValue())
        );

        // Panel norte: dimensiones + hilos + generar
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT,5,4));
        pnlTop.add(new JLabel("Filas A:"));    pnlTop.add(spRowsA);
        pnlTop.add(new JLabel("Cols A:"));     pnlTop.add(spColsA);
        pnlTop.add(new JLabel("Filas B:"));    pnlTop.add(spRowsB);
        pnlTop.add(new JLabel("Cols B:"));     pnlTop.add(spColsB);
        pnlTop.add(new JLabel("Hilos Conc:")); pnlTop.add(spThreadsConc);
        pnlTop.add(new JLabel("Hilos Paral:"));pnlTop.add(spThreadsPar);
        pnlTop.add(btnGenerate);
        add(pnlTop, BorderLayout.NORTH);

        // Preview A/B
        setupPreviewTable(tblA);
        setupPreviewTable(tblB);
        JScrollPane spA = makeScroll(tblA);
        JScrollPane spB = makeScroll(tblB);

        JPanel pnlCenter = new JPanel(new GridLayout(1,2,8,0));
        pnlCenter.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));
        pnlCenter.add(wrapTitled("Preview A", spA, btnViewA));
        pnlCenter.add(wrapTitled("Preview B", spB, btnViewB));
        add(pnlCenter, BorderLayout.CENTER);

        // Modos + multiplicar
        JPanel pnlModes = new JPanel();
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbSeq); bg.add(rbConc); bg.add(rbPar);
        pnlModes.add(rbSeq); pnlModes.add(rbConc); pnlModes.add(rbPar);
        pnlModes.add(btnMultiply);
        add(pnlModes, BorderLayout.SOUTH);

        // Preview C + historial
        setupPreviewTable(tblC);
        JScrollPane spC = makeScroll(tblC);
        JPanel pnlC = wrapTitled("Preview C", spC, btnViewC);

        JScrollPane spStatus = new JScrollPane(statusTable,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        spStatus.setBorder(BorderFactory.createTitledBorder("Historial de hilos"));

        JSplitPane east = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            pnlC,
            new JPanel(new BorderLayout(4,4)) {{
                add(progressBar, BorderLayout.NORTH);
                add(spStatus, BorderLayout.CENTER);
            }}
        );
        east.setResizeWeight(0.5);
        add(east, BorderLayout.EAST);

        // Listeners
        btnGenerate.addActionListener(e -> onGenerate());
        btnMultiply.addActionListener(e -> onMultiply());
        btnViewA.addActionListener(e -> showFull(A, "Matriz A completa"));
        btnViewB.addActionListener(e -> showFull(B, "Matriz B completa"));
        btnViewC.addActionListener(e -> showFull(C, "Matriz C completa"));

        setSize(1200, 700);
        setLocationRelativeTo(null);
    }

    private void onGenerate() {
        int rA = (int)spRowsA.getValue(), cA = (int)spColsA.getValue();
        int rB = (int)spRowsB.getValue(), cB = (int)spColsB.getValue();
        A = randomMatrix(rA, cA);
        B = randomMatrix(rB, cB);
        display(A, tblA, true);
        display(B, tblB, true);
        statusModel.setRowCount(0);
        progressBar.setValue(0);

        // Inicializamos ParallelMultiplier apuntando a los hosts remotos
        if (rbPar.isSelected()) {
            try {
                List<String> hosts = List.of("192.168.100.126", "192.168.100.217");
                paral = new ParallelMultiplier(hosts);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error RMI: "+ex,
                    "Error", JOptionPane.ERROR_MESSAGE);
                paral = null;
            }
        }
    }

    private void onMultiply() {
        if (A==null || B==null) {
            JOptionPane.showMessageDialog(this,
                "Genera primero A y B","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        btnMultiply.setEnabled(false);
        statusModel.setRowCount(0);
        progressBar.setValue(0);

        SwingWorker<Void,Integer> worker = new SwingWorker<>() {
            long t0, t1;
            AtomicInteger done = new AtomicInteger();

            @Override
            protected Void doInBackground() throws Exception {
                t0 = System.currentTimeMillis();
                int n = A.length, m = B[0].length;
                C = new int[n][m];

                if (rbSeq.isSelected()) {
                    // ---------------- SECUENCIAL ----------------
                    publish(new String[]{"Seq","Inicio secuencial"});
                    int total=n*m, cnt=0;
                    for(int i=0;i<n;i++){
                        for(int j=0;j<m;j++){
                            int s=0;
                            for(int k=0;k<A[0].length;k++) s+=A[i][k]*B[k][j];
                            C[i][j]=s;
                            cnt++; setProgress(cnt*100/total);
                        }
                    }
                    publish(new String[]{"Seq","Fin secuencial"});
                }
                else if (rbConc.isSelected()) {
                    // ---------------- CONCURRENTE LOCAL ----------------
                    int threads = (int)spThreadsConc.getValue();
                    publish(new String[]{"Conc","Hilos: "+threads});
                    ExecutorService svc = Executors.newFixedThreadPool(threads);
                    for(int i=0;i<n;i++){
                        final int row=i;
                        svc.submit(()->{
                            String tn = Thread.currentThread().getName();
                            publish(new String[]{tn,"Inicio fila "+row});
                            for(int j=0;j<m;j++){
                                int s=0;
                                for(int k=0;k<A[0].length;k++) s+=A[row][k]*B[k][j];
                                C[row][j]=s;
                            }
                            publish(new String[]{tn,"Fin fila "+row});
                            int d = done.incrementAndGet();
                            setProgress(d*100/n);
                        });
                    }
                    svc.shutdown();
                    svc.awaitTermination(1,TimeUnit.HOURS);
                }
                else {
                    // ---------------- PARALELO REMOTO ----------------
                    if (paral==null) throw new IllegalStateException("ParallelMultiplier no inicializado");
                    C = paral.multiplyRemote(A, B);
                }

                t1 = System.currentTimeMillis();
                return null;
            }

            @Override
            protected void process(List<String[]> updates) {
                for (String[] u: updates) {
                    statusModel.addRow(new Object[]{u[0],u[1]});
                }
            }

            @Override
            protected void done() {
                display(C, tblC, true);
                JOptionPane.showMessageDialog(AppGUI.this,
                    "Tiempo total: "+(t1-t0)+" ms","Completado",
                    JOptionPane.INFORMATION_MESSAGE);
                btnMultiply.setEnabled(true);
            }
        };

        worker.addPropertyChangeListener(evt->{
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer)evt.getNewValue());
            }
        });

        worker.execute();
    }

    private void setupPreviewTable(JTable tbl) {
        tbl.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tbl.setRowSelectionAllowed(false);
        tbl.setColumnSelectionAllowed(false);
    }

    private JScrollPane makeScroll(JTable tbl) {
        return new JScrollPane(tbl,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    private JPanel wrapTitled(String title, JComponent comp, JButton btn) {
        JPanel p = new JPanel(new BorderLayout(4,4));
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(comp, BorderLayout.CENTER);
        p.add(btn, BorderLayout.SOUTH);
        return p;
    }

    private void display(int[][] M, JTable tbl, boolean preview) {
        int r=M.length, c=M[0].length;
        int vr= preview? Math.min(MAX_PREVIEW,r):r;
        int vc= preview? Math.min(MAX_PREVIEW,c):c;
        Object[][] data=new Object[vr][vc];
        for(int i=0;i<vr;i++)for(int j=0;j<vc;j++)data[i][j]=M[i][j];
        String[] cols=new String[vc];
        for(int j=0;j<vc;j++)cols[j]=String.valueOf(j+1);

        DefaultTableModel model=new DefaultTableModel(data,cols){
            @Override public boolean isCellEditable(int row,int col){return false;}
        };
        tbl.setModel(model);

        FontMetrics fm=tbl.getFontMetrics(tbl.getFont());
        for(int j=0;j<vc;j++){
            int w=fm.stringWidth(cols[j]);
            for(int i=0;i<vr;i++){
                w=Math.max(w, fm.stringWidth(data[i][j].toString()));
            }
            tbl.getColumnModel().getColumn(j).setPreferredWidth(w+8);
        }
    }

    private void showFull(int[][] M, String title) {
        if (M==null) return;
        JDialog dlg=new JDialog(this,title,false);
        JTable t=new JTable();
        display(M,t,false);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane sp=new JScrollPane(t,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setRowHeaderView(createRowHeader(t));
        dlg.add(sp);
        dlg.setSize(750,550);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private JList<String> createRowHeader(JTable tbl) {
        int rows=tbl.getModel().getRowCount();
        String[] nums=new String[rows];
        for(int i=0;i<rows;i++)nums[i]=String.valueOf(i+1);
        JList<String> list=new JList<>(nums);
        list.setFixedCellWidth(40);
        list.setFixedCellHeight(tbl.getRowHeight());
        list.setCellRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(
                JList<?> l,Object v,int idx,boolean s,boolean f){
                super.getListCellRendererComponent(l,v,idx,false,false);
                setHorizontalAlignment(CENTER);
                setBackground(tbl.getTableHeader().getBackground());
                setForeground(tbl.getTableHeader().getForeground());
                setFont(tbl.getTableHeader().getFont());
                return this;
            }
        });
        return list;
    }

    private int[][] randomMatrix(int r,int c) {
        Random rnd=new Random();
        int[][] M=new int[r][c];
        for(int i=0;i<r;i++)for(int j=0;j<c;j++)M[i][j]=rnd.nextInt(10);
        return M;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppGUI().setVisible(true));
    }
}
