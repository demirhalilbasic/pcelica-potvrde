// MainFrame.java
package com.pcelica.ui;

import com.pcelica.model.BeeUser;
import com.pcelica.pdf.PdfExporter;
import com.pcelica.store.DataStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
            new String[]{"ID", "Ime", "Prezime", "Spol", "Datum rođenja", "Mesto rođenja", "Prebivalište", "Kolonije", "Broj dokumenta"}, 0) {
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

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Create a panel for toolbars that uses BoxLayout for vertical arrangement
        topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(new EmptyBorder(0, 0, 8, 0));

        // Main toolbar with actions
        mainToolbar = new JToolBar();
        mainToolbar.setFloatable(false);
        mainToolbar.setFloatable(false);
        mainToolbar.setMargin(new Insets(0, 0, 5, 0));

        // Load icons
        ImageIcon addIcon = loadIcon("/icons/add.png", 32, 32);  // Increased size
        ImageIcon editIcon = loadIcon("/icons/edit.png", 32, 32);
        ImageIcon deleteIcon = loadIcon("/icons/delete.png", 32, 32);
        ImageIcon exportIcon = loadIcon("/icons/export.png", 32, 32);
        ImageIcon restoreIcon = loadIcon("/icons/restore.png", 32, 32);
        ImageIcon backupIcon = loadIcon("/icons/backup.png", 32, 32);
        ImageIcon snapshotIcon = loadIcon("/icons/snapshot.png", 32, 32);
        ImageIcon arrowDownIcon = loadIcon("/icons/arrow_down.png", 16, 16);

        // Year selection with custom arrow - completely replace default arrow
        JPanel yearPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        yearPanel.add(new JLabel("Godina:"));

        // Create a completely custom combobox without the default arrow
        JPanel customComboPanel = new JPanel(new BorderLayout());
        cbYears.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120), 1));
        cbYears.setPreferredSize(new Dimension(100, 28));
        customComboPanel.add(cbYears, BorderLayout.CENTER);

        // Remove the default combobox arrow
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
        mainToolbar.addSeparator(new Dimension(15, 0));

        // Action buttons with improved styling
        btnAdd = createToolbarButton("Dodaj", addIcon);
        btnEdit = createToolbarButton("Uredi", editIcon);
        btnDelete = createToolbarButton("Obriši", deleteIcon);
        btnExport = createToolbarButton("Export PDF", exportIcon);
        btnRestoreStartup = createToolbarButton("Restore startup", restoreIcon);
        btnRestoreFromBackup = createToolbarButton("Restore from backup", backupIcon);
        btnMakeSnapshotMain = createToolbarButton("Učitaj snapshot", snapshotIcon);
        btnMakeSnapshotMain.setEnabled(false);

        // Add buttons to toolbar with proper spacing
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnAdd);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnEdit);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnDelete);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnExport);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnRestoreStartup);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnRestoreFromBackup);
        mainToolbar.add(Box.createHorizontalStrut(5));
        mainToolbar.add(btnMakeSnapshotMain);

        // Search toolbar - aligned to left
        searchToolbar = new JToolBar();
        searchToolbar.setFloatable(false);
        searchToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        searchPanel.add(new JLabel("Pretraga:"));

        // Style the search field
        tfSearch.setMaximumSize(new Dimension(200, 28));
        tfSearch.setPreferredSize(new Dimension(200, 28));
        tfSearch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120), 1),
                BorderFactory.createEmptyBorder(0, 5, 0, 5)
        ));

        searchPanel.add(tfSearch);
        searchToolbar.add(searchPanel);

        // Add toolbars to the top panel
        topPanel.add(mainToolbar);
        topPanel.add(Box.createVerticalStrut(5));
        topPanel.add(searchToolbar);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Style table
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.setShowGrid(true);
        table.setGridColor(new Color(220, 220, 220));
        table.setIntercellSpacing(new Dimension(1, 1));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(8, 0, 0, 0));
        mainPanel.add(scroll, BorderLayout.CENTER);

        setContentPane(mainPanel);

        // actions
        cbYears.addActionListener(e -> refreshTable());
        btnAdd.addActionListener(e -> {
            if (viewingSnapshot) handleModifyWhileViewingSnapshot();
            else onAdd();
        });
        btnEdit.addActionListener(e -> {
            if (viewingSnapshot) handleModifyWhileViewingSnapshot();
            else onEdit();
        });
        btnDelete.addActionListener(e -> {
            if (viewingSnapshot) handleModifyWhileViewingSnapshot();
            else onDelete();
        });
        btnExport.addActionListener(e -> onExport());
        btnRestoreStartup.addActionListener(e -> {
            try {
                store.restoreSnapshot();
                viewingSnapshot = false;
                currentSnapshotFile = null;
                snapshotList = Collections.emptyList();
                btnMakeSnapshotMain.setEnabled(false);
                refreshTable();
                JOptionPane.showMessageDialog(this, "Vraćeno stanje pri pokretanju aplikacije.");
            } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Greška: " + ex.getMessage()); }
        });
        btnRestoreFromBackup.addActionListener(e -> onRestoreFromBackup());
        btnMakeSnapshotMain.addActionListener(e -> {
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
                    btnMakeSnapshotMain.setEnabled(false);
                    loadYears();
                    refreshTable();
                    JOptionPane.showMessageDialog(this, "Snapshot učitan kao novi glavni snapshot (rezervacije resetovane).");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Greška pri importu snapshot-a: " + ex.getMessage());
                }
            }
        });

        // search
        table.setRowSorter(sorter);
        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            void update() {
                String text = tfSearch.getText();
                if (text == null || text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        // double click details
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row < 0) return;
                    int modelRow = table.convertRowIndexToModel(row);
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
        cbYears.setSelectedItem(LocalDate.now().getYear());
        refreshTable();
    }

    private void refreshTable() {
        Integer year = (Integer) cbYears.getSelectedItem();
        if (year == null) return;
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
                    u.getDocNumber()
            });
        }

        // enable/disable modification buttons
        btnAdd.setEnabled(!viewingSnapshot);
        btnEdit.setEnabled(!viewingSnapshot);
        btnDelete.setEnabled(!viewingSnapshot);
        btnMakeSnapshotMain.setEnabled(viewingSnapshot);
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

        UserDialog dlg = new UserDialog(this, "Uredi pčelara", clone, true); // lock name when editing via dialog from list
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

        UserDialog dlg = new UserDialog(this, "Uredi pčelara", clone, true);
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