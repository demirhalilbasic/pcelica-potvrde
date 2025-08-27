// MainFrame.java
package com.pcelica.ui;

import com.pcelica.model.BeeUser;
import com.pcelica.pdf.PdfExporter;
import com.pcelica.store.DataStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.util.*;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class MainFrame extends JFrame {
    private final DataStore store;
    private final JComboBox<Integer> cbYears = new JComboBox<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"ID", "Ime", "Prezime", "Spol", "Datum rođenja", "Mesto rođenja", "Prebivalište", "Kolonije", "Broj dokumenta", "Datum potvrde"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextField tfSearch = new JTextField(20);
    private static final DateTimeFormatter OUT_DF = DateTimeFormatter.ofPattern("dd.MM.yyyy.");
    private static final DateTimeFormatter BK_PARSER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // toolbar components
    private JPanel topPanel;
    private JToolBar mainToolbar;
    private JToolBar searchToolbar;
    private JButton btnAdd, btnEdit, btnDelete, btnExport, btnRestoreStartup, btnRestoreFromBackup, btnMakeSnapshotMain;
    private JButton btnRefresh;
    private JMenuBar menuBar;
    private JMenu toolsMenu;

    // novo polje za stavku u dropdownu "Više"
    private JMenuItem menuDelete;

    // snapshot state
    private boolean viewingSnapshot = false;
    private Path currentSnapshotFile = null;
    private List<BeeUser> snapshotList = Collections.emptyList();

    public MainFrame(DataStore store) {
        super("Pčelarski podsticaj - potvrde");
        this.store = store;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 700);
        setMinimumSize(new Dimension(1000, 600));
        initUI();
        loadYears();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                try {
                    store.saveAll();
                } catch (Exception ex) { ex.printStackTrace(); }
                dispose();
                System.exit(0);
            }
        });
    }

    // Fixed initUI method for MainFrame
    // Assumes class fields already declare these JButton variables:
    // JButton btnAdd, btnEdit, btnDelete, btnExport, btnRefresh,
    // btnRestoreStartup, btnRestoreFromBackup, btnMakeSnapshotMain;
    // JComboBox<String> cbYears; JTable table; DefaultTableModel tableModel; RowSorter sorter;
    // JTextField tfSearch; JToolBar mainToolbar, searchToolbar; JPanel topPanel;
    // boolean viewingSnapshot; File currentSnapshotFile; List<File> snapshotList; Store store; // etc.

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // --- MENU BAR (unchanged) ---
        menuBar = new JMenuBar();
        toolsMenu = new JMenu("Alati");
        toolsMenu.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JMenuItem exportAllItem = new JMenuItem("Export svih podataka", loadIcon("/icons/export_all.png", 16, 16));
        JMenuItem importItem = new JMenuItem("Import podataka", loadIcon("/icons/import.png", 16, 16));
        JMenuItem restoreStartupItem = new JMenuItem("Restore startup", loadIcon("/icons/restore.png", 16, 16));
        JMenuItem restoreBackupItem = new JMenuItem("Restore from backup", loadIcon("/icons/backup.png", 16, 16));
        JMenuItem snapshotItem = new JMenuItem("Učitaj snapshot", loadIcon("/icons/snapshot.png", 16, 16));

        exportAllItem.addActionListener(e -> onExportAll());
        importItem.addActionListener(e -> onImport());
        restoreStartupItem.addActionListener(e -> {
            try {
                store.restoreSnapshot();
                viewingSnapshot = false;
                currentSnapshotFile = null;
                snapshotList = Collections.emptyList();
                if (btnMakeSnapshotMain != null) btnMakeSnapshotMain.setEnabled(false);
                refreshTable();
                JOptionPane.showMessageDialog(this, "Vraćeno stanje pri pokretanju aplikacije.");
            } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Greška: " + ex.getMessage()); }
        });
        restoreBackupItem.addActionListener(e -> onRestoreFromBackup());
        snapshotItem.addActionListener(e -> {
            if (!viewingSnapshot || currentSnapshotFile == null) {
                JOptionPane.showMessageDialog(this, "Nema snapshot-a za učitavanje.");
                return;
            }
            int r = JOptionPane.showConfirmDialog(this, "Želite li učitati odabrani snapshot kao novi glavni snapshot? (rezervisani brojevi snapshot-a će zamijeniti postojeće)", "Potvrdi", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                try {
                    store.importSnapshotAsMainReplaceReserved(currentSnapshotFile);
                    viewingSnapshot = false;
                    currentSnapshotFile = null;
                    snapshotList = Collections.emptyList();
                    if (btnMakeSnapshotMain != null) btnMakeSnapshotMain.setEnabled(false);
                    loadYears();
                    refreshTable();
                    JOptionPane.showMessageDialog(this, "Snapshot učitan kao novi glavni snapshot (rezervacije resetovane).");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Greška pri importu snapshot-a: " + ex.getMessage());
                }
            }
        });

        toolsMenu.add(exportAllItem);
        toolsMenu.add(importItem);
        toolsMenu.addSeparator();
        toolsMenu.add(restoreStartupItem);
        toolsMenu.add(restoreBackupItem);
        toolsMenu.add(snapshotItem);

        menuBar.add(toolsMenu);
        setJMenuBar(menuBar);

        // --- MAIN PANEL ---
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(new EmptyBorder(0, 0, 8, 0));

        // --- TOOLBAR (gornji red: Add/Edit/Export/Year/More at far right) ---
        mainToolbar = new JToolBar();
        mainToolbar.setFloatable(false);
        mainToolbar.setMargin(new Insets(0, 0, 5, 0));

        // Load icons
        ImageIcon addIcon = loadIcon("/icons/add.png", 24, 24);
        ImageIcon editIcon = loadIcon("/icons/edit.png", 24, 24);
        ImageIcon deleteIcon = loadIcon("/icons/delete.png", 24, 24);
        ImageIcon exportIcon = loadIcon("/icons/export.png", 24, 24);
        ImageIcon refreshIcon = loadIcon("/icons/refresh.png", 24, 24);
        ImageIcon arrowDownIcon = loadIcon("/icons/arrow_down.png", 16, 16);
        ImageIcon restoreIcon = loadIcon("/icons/restore.png", 16, 16);
        ImageIcon backupIcon = loadIcon("/icons/backup.png", 16, 16);
        ImageIcon snapshotIcon = loadIcon("/icons/snapshot.png", 16, 16);
        ImageIcon moreIcon = loadIcon("/icons/more.png", 24, 24);

        // Action buttons
        btnAdd = createToolbarButton("Dodaj", addIcon);
        btnEdit = createToolbarButton("Uredi", editIcon);
        btnExport = createToolbarButton("PDF", exportIcon);
        btnRefresh = createToolbarButton("Osvježi", refreshIcon);
        btnRefresh.setPreferredSize(new Dimension(110, 36)); // compact for search row

        // Keep snapshot button (used elsewhere)
        btnMakeSnapshotMain = createToolbarButton(null, snapshotIcon);
        btnMakeSnapshotMain.setToolTipText("Učitaj snapshot kao glavni");
        btnMakeSnapshotMain.setEnabled(false);

        // Add main action buttons (left)
        mainToolbar.add(btnAdd);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnEdit);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnExport);
        mainToolbar.add(Box.createHorizontalStrut(10));

        // Year selection
        JPanel yearPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        yearPanel.add(new JLabel("Godina:"));
        JPanel customComboPanel = new JPanel(new BorderLayout());
        cbYears.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120), 1));
        cbYears.setPreferredSize(new Dimension(100, 28));
        customComboPanel.add(cbYears, BorderLayout.CENTER);
        UIManager.put("ComboBox.squareButton", Boolean.FALSE);
        cbYears.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton button = new JButton();
                button.setIcon(arrowDownIcon);
                button.setBorder(BorderFactory.createEmptyBorder());
                button.setContentAreaFilled(false);
                button.setFocusable(false);
                return button;
            }
        });
        yearPanel.add(customComboPanel);
        mainToolbar.add(yearPanel);

        // glue pushes the next control to the far right
        mainToolbar.add(Box.createHorizontalGlue());

        // More button at top-right
        JButton btnMore = createToolbarButton("Više", moreIcon);
        btnMore.setPreferredSize(new Dimension(90, 36));
        JPopupMenu moreMenu = new JPopupMenu();

        // menuDelete is a class-level field so we can enable/disable it from refreshTable()
        menuDelete = new JMenuItem("Obriši", deleteIcon);
        menuDelete.addActionListener(e -> {
            if (viewingSnapshot) handleModifyWhileViewingSnapshot();
            else onDelete();
        });
        moreMenu.add(menuDelete);

        JMenuItem mRestoreStartup = new JMenuItem("Restore startup", restoreIcon);
        mRestoreStartup.addActionListener(e -> {
            try {
                store.restoreSnapshot();
                viewingSnapshot = false;
                currentSnapshotFile = null;
                snapshotList = Collections.emptyList();
                if (btnMakeSnapshotMain != null) btnMakeSnapshotMain.setEnabled(false);
                refreshTable();
                JOptionPane.showMessageDialog(this, "Vraćeno stanje pri pokretanju aplikacije.");
            } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Greška: " + ex.getMessage()); }
        });
        moreMenu.add(mRestoreStartup);

        JMenuItem mRestoreBackup = new JMenuItem("Restore from backup", backupIcon);
        mRestoreBackup.addActionListener(e -> onRestoreFromBackup());
        moreMenu.add(mRestoreBackup);

        JMenuItem mLoadSnapshot = new JMenuItem("Učitaj snapshot", snapshotIcon);
        mLoadSnapshot.addActionListener(e -> {
            if (!viewingSnapshot || currentSnapshotFile == null) {
                JOptionPane.showMessageDialog(this, "Nema snapshot-a za učitavanje.");
                return;
            }
            int r = JOptionPane.showConfirmDialog(this, "Želite li učitati odabrani snapshot kao novi glavni snapshot? (rezervisani brojevi snapshot-a će zamijeniti postojeće)", "Potvrdi", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                try {
                    store.importSnapshotAsMainReplaceReserved(currentSnapshotFile);
                    viewingSnapshot = false;
                    currentSnapshotFile = null;
                    snapshotList = Collections.emptyList();
                    if (btnMakeSnapshotMain != null) btnMakeSnapshotMain.setEnabled(false);
                    loadYears();
                    refreshTable();
                    JOptionPane.showMessageDialog(this, "Snapshot učitan kao novi glavni snapshot (rezervacije resetovane).");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Greška pri importu snapshot-a: " + ex.getMessage());
                }
            }
        });
        moreMenu.add(mLoadSnapshot);

        btnMore.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                moreMenu.show(btnMore, 0, btnMore.getHeight());
            }
        });

        mainToolbar.add(btnMore);

        // --- SEARCH ROW (refresh + search field, aligned left) ---
        searchToolbar = new JToolBar();
        searchToolbar.setFloatable(false);
        searchToolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0)); // left aligned

        // Refresh button first (max left)
        searchToolbar.add(btnRefresh);

        // Search label & field immediately to the right
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        searchPanel.add(new JLabel("Pretraga:"));

        tfSearch.setMaximumSize(new Dimension(300, 28));
        tfSearch.setPreferredSize(new Dimension(300, 28));
        tfSearch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120), 1),
                BorderFactory.createEmptyBorder(0, 5, 0, 5)
        ));

        searchPanel.add(tfSearch);
        searchToolbar.add(searchPanel);

        // Add toolbars to top panel
        topPanel.add(mainToolbar);
        topPanel.add(Box.createVerticalStrut(6));
        topPanel.add(searchToolbar);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // --- TABLE ---
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.setShowGrid(true);
        table.setGridColor(new Color(220, 220, 220));
        table.setIntercellSpacing(new Dimension(1, 1));
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(8, 0, 0, 0));
        mainPanel.add(scroll, BorderLayout.CENTER);

        setContentPane(mainPanel);

        // --- ACTIONS ---
        cbYears.addActionListener(e -> refreshTable());

        btnAdd.addActionListener(e -> {
            if (viewingSnapshot) handleModifyWhileViewingSnapshot();
            else onAdd();
        });
        btnEdit.addActionListener(e -> {
            if (viewingSnapshot) handleModifyWhileViewingSnapshot();
            else onEdit();
        });
        // delete now handled in menuDelete
        btnExport.addActionListener(e -> onExport());

        // btnRefresh on the search row
        btnRefresh.addActionListener(e -> refreshView());

        // search filter
        table.setRowSorter(sorter);
        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            void update() {
                String raw = tfSearch.getText();
                if (raw == null || raw.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                    return;
                }
                final String q = normalizeForSearch(raw);

                RowFilter<DefaultTableModel, Integer> rf = new RowFilter<DefaultTableModel, Integer>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        int cols = entry.getValueCount();
                        // skip column 0 (ID) because it's hidden/internal
                        for (int c = 1; c < cols; c++) {
                            Object val = entry.getValue(c);
                            if (val == null) continue;
                            String sv = normalizeForSearch(String.valueOf(val));
                            if (sv.contains(q)) return true;
                        }
                        return false;
                    }
                };
                sorter.setRowFilter(rf);
            }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        // double click details - robust check before converting indices
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    if (viewRow >= table.getRowCount()) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    if (modelRow < 0 || modelRow >= tableModel.getRowCount()) return;
                    String id = (String) tableModel.getValueAt(modelRow, 0);
                    BeeUser u = getUserByIdFromCurrentView(id);
                    if (u != null) {
                        DetailDialog dlg = new DetailDialog(MainFrame.this, u, viewingSnapshot);
                        dlg.setVisible(true);
                    }
                }
            }
        });
    }

    private String normalizeForSearch(String s) {
        if (s == null) return "";
        // Normalize Unicode to NFD and remove diacritic marks
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", ""); // remove diacritic marks
        return n.toLowerCase(Locale.ROOT);
    }

    private void resetColumnWidths() {
        // determine target width from viewport if available, otherwise table width
        int total = table.getWidth();
        Container p = table.getParent();
        if (p instanceof JViewport) {
            total = ((JViewport) p).getWidth();
        }
        if (total <= 0) return;

        int cols = table.getColumnModel().getColumnCount();
        if (cols <= 1) return; // nothing to resize (ID only)

        // count visible columns (skip the hidden ID column if its width is 0)
        int visibleCount = 0;
        for (int i = 0; i < cols; i++) {
            if (table.getColumnModel().getColumn(i).getMaxWidth() > 0) visibleCount++;
        }
        if (visibleCount <= 0) return;
        int per = Math.max(60, total / visibleCount); // minimum sensible width

        for (int i = 0; i < cols; i++) {
            // keep ID column hidden (min/max 0) if you already set it so
            if (i == 0) {
                table.getColumnModel().getColumn(i).setMinWidth(0);
                table.getColumnModel().getColumn(i).setMaxWidth(0);
                table.getColumnModel().getColumn(i).setPreferredWidth(0);
                continue;
            }
            table.getColumnModel().getColumn(i).setMinWidth(40);
            table.getColumnModel().getColumn(i).setPreferredWidth(per);
            table.getColumnModel().getColumn(i).setMaxWidth(Integer.MAX_VALUE);
        }
        // force revalidate/repaint
        table.revalidate();
        table.repaint();
    }

    private void onExportAll() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Odaberite folder za export podataka");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File targetDir = chooser.getSelectedFile();

                // Export CSV
                Path csvFile = Paths.get(targetDir.getAbsolutePath(), "store.csv");
                Files.copy(Paths.get("data", "store.csv"), csvFile, StandardCopyOption.REPLACE_EXISTING);

                // Export reserved numbers
                Path jsonFile = Paths.get(targetDir.getAbsolutePath(), "reserved_numbers.json");
                Files.copy(Paths.get("data", "reserved_numbers.json"), jsonFile, StandardCopyOption.REPLACE_EXISTING);

                JOptionPane.showMessageDialog(this, "Podaci uspješno exportovani u: " + targetDir.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Greška pri exportu: " + ex.getMessage());
            }
        }
    }

    private void onImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Odaberite CSV fajl za import");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV fajlovi", "csv"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File csvFile = chooser.getSelectedFile();

            // Now ask for the reserved numbers JSON file
            chooser.setDialogTitle("Odaberite reserved_numbers.json fajl");
            chooser.setFileFilter(new FileNameExtensionFilter("JSON fajlovi", "json"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File jsonFile = chooser.getSelectedFile();

                int confirm = JOptionPane.showConfirmDialog(this,
                        "Ova akcija će zamijeniti sve postojeće podatke. Želite li nastaviti?",
                        "Potvrda importa", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        // Copy files to data directory
                        Files.copy(csvFile.toPath(), Paths.get("data", "store.csv"), StandardCopyOption.REPLACE_EXISTING);
                        Files.copy(jsonFile.toPath(), Paths.get("data", "reserved_numbers.json"), StandardCopyOption.REPLACE_EXISTING);

                        // Reload the datastore
                        store.load();
                        viewingSnapshot = false;
                        currentSnapshotFile = null;
                        snapshotList = Collections.emptyList();
                        loadYears();
                        refreshTable();

                        JOptionPane.showMessageDialog(this, "Podaci uspješno importovani");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Greška pri importu: " + ex.getMessage());
                    }
                }
            }
        }
    }

    private JButton createToolbarButton(String text, ImageIcon icon) {
        JButton button = new JButton(text, icon);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBackground(new Color(240, 240, 240));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)  // Increased padding
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Use a fixed size to prevent "dancing" on hover
        Dimension buttonSize = new Dimension(button.getPreferredSize().width + 10, 48);
        button.setPreferredSize(buttonSize);
        button.setMinimumSize(buttonSize);
        button.setMaximumSize(buttonSize);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(220, 220, 220));
                // Don't change the border to prevent movement
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(240, 240, 240));
            }
        });

        return button;
    }

    private ImageIcon loadIcon(String path, int width, int height) {
        try {
            // Try to load as resource first
            URL imgURL = getClass().getResource(path);
            if (imgURL != null) {
                ImageIcon icon = new ImageIcon(imgURL);
                Image img = icon.getImage();
                BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = resized.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(img, 0, 0, width, height, null);
                g2.dispose();
                return new ImageIcon(resized);
            }

            // Try to load from file system as ICO or other formats
            String baseDir = System.getProperty("user.dir");
            Path iconPath = Paths.get(baseDir, "icons", path.substring(path.lastIndexOf("/") + 1));

            if (Files.exists(iconPath)) {
                String fileName = iconPath.getFileName().toString().toLowerCase();

                if (fileName.endsWith(".ico")) {
                    // ICO loading
                    Image image = Toolkit.getDefaultToolkit().getImage(iconPath.toString());
                    return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
                } else {
                    // Standard image formats
                    ImageIcon icon = new ImageIcon(iconPath.toString());
                    Image img = icon.getImage();
                    BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = resized.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.drawImage(img, 0, 0, width, height, null);
                    g2.dispose();
                    return new ImageIcon(resized);
                }
            }

            System.err.println("Could not load icon: " + path);
            return null;
        } catch (Exception e) {
            System.err.println("Error loading icon: " + path + " - " + e.getMessage());
            return null;
        }
    }

    private void loadYears() {
        Set<Integer> years = store.getYears();
        cbYears.removeAllItems();
        years.stream().sorted().forEach(cbYears::addItem);
        // ensure we set selected item only if combo contains it
        Integer now = LocalDate.now().getYear();
        if (years.contains(now)) {
            cbYears.setSelectedItem(now);
        } else if (!years.isEmpty()) {
            cbYears.setSelectedItem(years.stream().sorted().findFirst().orElse(now));
        }
        // refreshTable() will be called by the combo action listener or we call it here to be safe
        refreshTable();
    }

    private void refreshTable() {
        // If sorter is attached to table, save its state and detach it.
        RowFilter<? super DefaultTableModel, ? super Integer> activeFilter = null;
        List<? extends RowSorter.SortKey> activeSortKeys = null;
        boolean sorterWasAttached = table.getRowSorter() == sorter;
        if (sorterWasAttached) {
            try {
                activeFilter = sorter.getRowFilter();
                activeSortKeys = sorter.getSortKeys();
            } catch (Exception ignored) {}
            // detach sorter to avoid stale-index warnings while we modify the model
            table.setRowSorter(null);
        }

        // ensure selection is cleared to avoid stale view indexes
        table.clearSelection();

        Integer year = (Integer) cbYears.getSelectedItem();
        if (year == null) {
            tableModel.setRowCount(0);
            // reattach sorter with previous state (if any)
            if (sorterWasAttached) {
                table.setRowSorter(sorter);
                try { sorter.setRowFilter(activeFilter); sorter.setSortKeys(activeSortKeys); } catch (Exception ignored) {}
            }
            return;
        }

        // Clear and repopulate model (while sorter is detached)
        tableModel.setRowCount(0);
        List<BeeUser> users;
        if (viewingSnapshot) {
            users = new ArrayList<>();
            for (BeeUser u : snapshotList) if (u.getYear() == year) users.add(u);
        } else {
            users = store.getForYear(year);
        }
        for (BeeUser u : users) {
            tableModel.addRow(new Object[]{
                    u.getId(),
                    u.getFirstName(),
                    u.getLastName(),
                    u.getGender(),
                    u.getBirthDate() == null ? "" : u.getBirthDate().format(OUT_DF),
                    u.getBirthPlace(),
                    u.getResidenceCity(),
                    u.getColonies(),
                    u.getDocNumber(),
                    u.getCertificateDate() == null ? "" : u.getCertificateDate().format(OUT_DF)
            });
        }

        // Reattach sorter and restore its state safely
        table.setRowSorter(sorter);
        try {
            sorter.setRowFilter(activeFilter);
            sorter.setSortKeys(activeSortKeys);
        } catch (Exception ignored) {}

        // enable/disable modification controls (null-safe)
        boolean canModify = !viewingSnapshot;
        if (btnAdd != null) btnAdd.setEnabled(canModify);
        if (btnEdit != null) btnEdit.setEnabled(canModify);
        if (btnDelete != null) btnDelete.setEnabled(canModify);
        if (menuDelete != null) menuDelete.setEnabled(canModify);
        if (btnMakeSnapshotMain != null) btnMakeSnapshotMain.setEnabled(viewingSnapshot);
    }

    private BeeUser getUserByIdFromCurrentView(String id) {
        if (viewingSnapshot) {
            return snapshotList.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        } else {
            Integer year = (Integer) cbYears.getSelectedItem();
            if (year == null) return null;
            return store.getForYear(year).stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        }
    }

    // ACTIONS (Add/Edit/Delete/Export) ------------------------------------------------

    private void onAdd() {
        Integer year = (Integer) cbYears.getSelectedItem();
        if (year == null) {
            JOptionPane.showMessageDialog(this, "Odaberi godinu prvo.");
            return;
        }

        JPanel p = new JPanel(new GridLayout(2,2,6,6));
        JTextField tfFirst = new JTextField(20);
        JTextField tfLast = new JTextField(20);
        p.add(new JLabel("Ime:")); p.add(tfFirst);
        p.add(new JLabel("Prezime:")); p.add(tfLast);

        int res = JOptionPane.showConfirmDialog(this, p, "Novi pčelar - upišite ime i prezime (automatski povuci podatke ako postoji)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String firstRaw = tfFirst.getText().trim();
        String lastRaw = tfLast.getText().trim();
        if (firstRaw.isEmpty() || lastRaw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ime i prezime su obavezni.");
            return;
        }

        String first = toTitleCase(firstRaw);
        String last = toTitleCase(lastRaw);

        if (store.existsSameName(year, first, last)) {
            JOptionPane.showMessageDialog(this, "Već postoji pčelar s istim imenom i prezimenom za odabranu godinu.");
            return;
        }

        BeeUser previous = store.getLatestByNameBefore(first, last, year);
        BeeUser toEdit;
        if (previous != null) {
            toEdit = new BeeUser();
            toEdit.setFirstName(previous.getFirstName());
            toEdit.setLastName(previous.getLastName());
            toEdit.setGender(previous.getGender());
            toEdit.setBirthDate(previous.getBirthDate());
            toEdit.setBirthPlace(previous.getBirthPlace());
            toEdit.setResidenceCity(previous.getResidenceCity());
            toEdit.setColonies(previous.getColonies());
            toEdit.setCertificateDate(previous.getCertificateDate());
            JOptionPane.showMessageDialog(this, "Pronađeni podaci iz godine " + previous.getYear() + ". Možete potvrditi ili izmijeniti podatke u sljedećem dijalogu.");
        } else {
            toEdit = new BeeUser();
            toEdit.setFirstName(first);
            toEdit.setLastName(last);

            // Set default certificate date to the last user's certificate date in the same year
            List<BeeUser> currentYearUsers = store.getForYear(year);
            if (!currentYearUsers.isEmpty()) {
                BeeUser lastUser = currentYearUsers.get(currentYearUsers.size() - 1);
                toEdit.setCertificateDate(lastUser.getCertificateDate());
            } else {
                toEdit.setCertificateDate(LocalDate.now());
            }
        }

        // lockName = true -> in second dialog name fields are not editable (user wanted this)
        UserDialog dlg = new UserDialog(this, "Dodaj pčelara", toEdit, true); // lockName = true
        dlg.setVisible(true);
        if (!dlg.isOk()) return;
        BeeUser u = dlg.getUser();

        // assign id BEFORE persisting so update/delete can reference it
        u.setId(UUID.randomUUID().toString());

        if (store.existsSameName(year, u.getFirstName(), u.getLastName())) {
            JOptionPane.showMessageDialog(this, "Nakon izmjene, postoji pčelar s istim imenom i prezimenom za odabranu godinu.");
            return;
        }

        int seq = store.reserveNext(year);
        u.setSeqNumber(seq);
        u.setYear(year);
        u.setDocNumber(String.format("14-%02d/%02d", seq, year % 100));
        store.addUser(u);
        refreshTable();
    }

    private void onEdit() {
        BeeUser u = selectedUser();
        if (u == null) {
            JOptionPane.showMessageDialog(this, "Odaberi korisnika iz tabele.");
            return;
        }
        BeeUser clone = new BeeUser();
        clone.setId(u.getId());
        clone.setFirstName(u.getFirstName());
        clone.setLastName(u.getLastName());
        clone.setGender(u.getGender());
        clone.setBirthDate(u.getBirthDate());
        clone.setBirthPlace(u.getBirthPlace());
        clone.setResidenceCity(u.getResidenceCity());
        clone.setColonies(u.getColonies());
        clone.setDocNumber(u.getDocNumber());
        clone.setSeqNumber(u.getSeqNumber());
        clone.setYear(u.getYear());
        clone.setCertificateDate(u.getCertificateDate());

        // Allow changing name when editing (lockName = false)
        UserDialog dlg = new UserDialog(this, "Uredi pčelara", clone, false); // lockName = false
        dlg.setVisible(true);
        if (!dlg.isOk()) return;
        BeeUser edited = dlg.getUser();
        if (!edited.getFirstName().equalsIgnoreCase(u.getFirstName()) ||
                !edited.getLastName().equalsIgnoreCase(u.getLastName())) {
            if (store.existsSameName(edited.getYear(), edited.getFirstName(), edited.getLastName())) {
                JOptionPane.showMessageDialog(this, "Već postoji pčelar s istim imenom i prezimenom za odabranu godinu.");
                return;
            }
        }
        edited.setSeqNumber(u.getSeqNumber());
        edited.setDocNumber(u.getDocNumber());
        edited.setId(u.getId());
        store.updateUser(edited);
        refreshTable();
    }

    private void onDelete() {
        BeeUser u = selectedUser();
        if (u == null) {
            JOptionPane.showMessageDialog(this, "Odaberi korisnika za brisanje.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Jeste li sigurni da želite izbrisati korisnika? Broj će ostati rezervisan.", "Potvrdi", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        store.deleteUser(u);
        refreshTable();
    }

    private void onExport() {
        BeeUser u = selectedUser();
        if (u == null) {
            JOptionPane.showMessageDialog(this, "Odaberi korisnika za export.");
            return;
        }
        try {
            File f = exportUserPdf(u);

            // Check for missing data and show warning
            StringBuilder warning = new StringBuilder();
            if (u.getBirthPlace() == null || u.getBirthPlace().trim().isEmpty()) {
                warning.append("• Mjesto rođenja\n");
            }
            if (u.getResidenceCity() == null || u.getResidenceCity().trim().isEmpty()) {
                warning.append("• Mjesto prebivališta\n");
            }

            if (warning.length() > 0) {
                JOptionPane.showMessageDialog(this,
                        "PDF je uspješno exportovan, ali nedostaju sljedeći podaci:\n" + warning.toString() +
                                "\nMolimo provjerite podatke o pčelaru.",
                        "Upozorenje", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "PDF exportiran: " + f.getAbsolutePath());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Greška pri exportu: " + ex.getMessage());
        }
    }

    private void refreshView() {
        // Clear search field and filter first
        tfSearch.setText("");

        // Reset sorting/filter state on sorter
        try {
            sorter.setRowFilter(null);
            sorter.setSortKeys(null);
        } catch (Exception ignored) {}

        // Detach sorter and clear selection to avoid stale-index warnings
        table.clearSelection();
        table.setRowSorter(null);

        // Refresh data
        refreshTable();

        // Reset column widths to equal sizes (user requested)
        resetColumnWidths();
    }

    public File exportUserPdf(BeeUser u) throws Exception {
        return PdfExporter.exportToDesktopFolder(u);
    }

    private void handleModifyWhileViewingSnapshot() {
        int r = JOptionPane.showConfirmDialog(this, "Trenutno gledate backup snapshot (read-only). Želite li učitati ovaj snapshot kao novi glavni snapshot kako biste mogli uređivati?", "Import snapshot as main?", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) importCurrentSnapshotAsMain();
        else exitSnapshotView();
    }

    public void importCurrentSnapshotAsMain() {
        if (currentSnapshotFile == null) {
            JOptionPane.showMessageDialog(this, "Nema izabranog snapshot fajla.");
            return;
        }
        try {
            // replace reserved behavior
            store.importSnapshotAsMainReplaceReserved(currentSnapshotFile);
            viewingSnapshot = false;
            currentSnapshotFile = null;
            snapshotList = Collections.emptyList();
            btnMakeSnapshotMain.setEnabled(false);
            loadYears();
            refreshTable();
            JOptionPane.showMessageDialog(this, "Snapshot je učitan kao novi glavni snapshot (rezervacije resetovane).");
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Greška pri importu: " + ex.getMessage()); }
    }

    public void exitSnapshotView() {
        viewingSnapshot = false;
        currentSnapshotFile = null;
        snapshotList = Collections.emptyList();
        btnMakeSnapshotMain.setEnabled(false);
        loadYears();
        refreshTable();
    }

    public void editUserFromDetails(BeeUser user) {
        BeeUser u = user;
        BeeUser clone = new BeeUser();
        clone.setId(u.getId());
        clone.setFirstName(u.getFirstName());
        clone.setLastName(u.getLastName());
        clone.setGender(u.getGender());
        clone.setBirthDate(u.getBirthDate());
        clone.setBirthPlace(u.getBirthPlace());
        clone.setResidenceCity(u.getResidenceCity());
        clone.setColonies(u.getColonies());
        clone.setDocNumber(u.getDocNumber());
        clone.setSeqNumber(u.getSeqNumber());
        clone.setYear(u.getYear());
        clone.setCertificateDate(u.getCertificateDate());

        // Allow changing name when editing from details
        UserDialog dlg = new UserDialog(this, "Uredi pčelara", clone, false); // lockName = false
        dlg.setVisible(true);
        if (!dlg.isOk()) return;
        BeeUser edited = dlg.getUser();
        if (!edited.getFirstName().equalsIgnoreCase(u.getFirstName()) ||
                !edited.getLastName().equalsIgnoreCase(u.getLastName())) {
            if (store.existsSameName(edited.getYear(), edited.getFirstName(), edited.getLastName())) {
                JOptionPane.showMessageDialog(this, "Već postoji pčelar s istim imenom i prezimenom za odabranu godinu.");
                return;
            }
        }
        edited.setSeqNumber(u.getSeqNumber());
        edited.setDocNumber(u.getDocNumber());
        edited.setId(u.getId());
        store.updateUser(edited);
        refreshTable();
    }

    private BeeUser selectedUser() {
        int sel = table.getSelectedRow();
        if (sel < 0) return null;
        int modelRow = table.convertRowIndexToModel(sel);
        String id = (String) tableModel.getValueAt(modelRow, 0);
        return getUserByIdFromCurrentView(id);
    }

    /**
     * Restore from backup: show readable list (dd.MM.yyyy. (weekday) HH:mm:ss — <type>)
     * and allow user to pick one. When chosen, load it into snapshotList and set viewingSnapshot=true.
     */
    private void onRestoreFromBackup() {
        try {
            Path dataDir = Paths.get("data");
            if (!Files.exists(dataDir)) {
                JOptionPane.showMessageDialog(this, "Nema data foldera ili backup fajlova.");
                return;
            }
            List<Path> backups = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dataDir, "backup_*.csv")) {
                for (Path p : ds) backups.add(p);
            }
            if (backups.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nema backup fajlova u data/.");
                return;
            }
            // sort newest first
            backups.sort(Comparator.comparing(Path::getFileName).reversed());

            // build display names in bosnian
            String[] display = new String[backups.size()];
            for (int i = 0; i < backups.size(); i++) {
                String fname = backups.get(i).getFileName().toString();
                String human = fname;
                try {
                    String core = fname.substring("backup_".length(), fname.length() - ".csv".length());
                    int idx = core.lastIndexOf('_');
                    String ts = core.substring(0, idx);
                    String reason = core.substring(idx + 1);
                    LocalDateTime dt = LocalDateTime.parse(ts, BK_PARSER);
                    String weekday = bosnianDay(dt.getDayOfWeek());
                    String when = dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm:ss"));
                    String reasonReadable = reasonToReadable(reason);
                    human = String.format("%s (%s) — %s", when, weekday, reasonReadable);
                } catch (Exception ex) {
                    // fallback to filename
                }
                display[i] = human;
            }

            String chosen = (String) JOptionPane.showInputDialog(this, "Izaberite backup fajl:", "Restore from backup", JOptionPane.PLAIN_MESSAGE, null, display, display[0]);
            if (chosen == null) return;
            int idx = Arrays.asList(display).indexOf(chosen);
            if (idx < 0) return;
            Path selected = backups.get(idx);
            List<BeeUser> parsed = store.readCsv(selected);
            snapshotList = parsed;
            viewingSnapshot = true;
            currentSnapshotFile = selected;
            btnMakeSnapshotMain.setEnabled(true);
            refreshTable();
            JOptionPane.showMessageDialog(this, "Snapshot učitan iz: " + selected.getFileName() + " (read-only). Ako želite uređivati, kliknite 'Učitaj ovaj snapshot kao glavni'.");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Greška pri učitavanju backup-a: " + ex.getMessage());
        }
    }

    private String reasonToReadable(String reason) {
        if (reason == null) return "nepoznato";
        switch (reason.toUpperCase(Locale.ROOT)) {
            case "ADD": return "nakon dodavanja";
            case "EDIT": return "nakon uređivanja";
            case "DELETE": return "nakon brisanja";
            case "CLOSE": return "pri zatvaranju aplikacije";
            case "STARTUP": return "prilikom pokretanja aplikacije";
            case "IMPORT": return "uvoz snapshot-a (union)";
            case "IMPORT_REPLACE": return "uvoz snapshot-a (zamjena rezervacija)";
            case "RESTORE_STARTUP": return "restore startup";
            default: return reason.toLowerCase();
        }
    }

    private String bosnianDay(DayOfWeek d) {
        switch (d) {
            case MONDAY: return "ponedjeljak";
            case TUESDAY: return "utorak";
            case WEDNESDAY: return "srijeda";
            case THURSDAY: return "četvrtak";
            case FRIDAY: return "petak";
            case SATURDAY: return "subota";
            case SUNDAY: return "nedjelja";
            default: return d.toString().toLowerCase();
        }
    }

    // helper: title case
    private String toTitleCase(String input) {
        if (input == null || input.isBlank()) return input;
        StringBuilder out = new StringBuilder();
        String[] words = input.trim().toLowerCase().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) out.append(' ');
            out.append(capitalizeCompound(words[i]));
        }
        return out.toString();
    }

    private String capitalizeCompound(String token) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < token.length()) {
            char c = token.charAt(i);
            if (c == '-' || c == '\'') {
                sb.append(c);
                i++;
                continue;
            }
            int j = i;
            while (j < token.length() && token.charAt(j) != '-' && token.charAt(j) != '\'') j++;
            String seg = token.substring(i, j);
            if (!seg.isEmpty()) {
                sb.append(Character.toUpperCase(seg.charAt(0)));
                if (seg.length() > 1) sb.append(seg.substring(1));
            }
            i = j;
        }
        return sb.toString();
    }
}