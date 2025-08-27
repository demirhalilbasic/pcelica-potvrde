// UserDialog.java
package com.pcelica.ui;

import com.pcelica.model.BeeUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;

public class UserDialog extends JDialog {
    private final BeeUser user;
    private boolean ok = false;

    private final JTextField tfFirst = new JTextField(20);
    private final JTextField tfLast = new JTextField(20);
    private final JComboBox<String> cbGender = new JComboBox<>(new String[]{"Muško", "Žensko", "Drugo"});
    private final JComboBox<Integer> cbDay = new JComboBox<>();
    private final JComboBox<String> cbMonth = new JComboBox<>();
    private final JComboBox<Integer> cbYear = new JComboBox<>();
    private final JTextField tfBirthPlace = new JTextField(20);
    private final JTextField tfResidence = new JTextField(20);
    private final JSpinner spColonies = new JSpinner(new SpinnerNumberModel(19, 0, 1000, 1));
    private final JComboBox<Integer> cbCertDay = new JComboBox<>();
    private final JComboBox<String> cbCertMonth = new JComboBox<>();
    private final JComboBox<Integer> cbCertYear = new JComboBox<>();

    private static final String[] BOSNIAN_MONTHS = new String[]{
            "januar","februar","mart","april","maj","juni","juli","avgust","septembar","oktobar","novembar","decembar"
    };

    public UserDialog(Window owner, String title, BeeUser u) {
        this(owner, title, u, false);
    }

    public UserDialog(Window owner, String title, BeeUser u, boolean lockName) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.user = u == null ? new BeeUser() : u;
        initUI(lockName);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private void initUI(boolean lockName) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(new EmptyBorder(12,12,12,12));

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6,8,6,8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;

        // name
        grid.add(new JLabel("Ime:"), gc);
        gc.gridx = 1;
        tfFirst.setEnabled(!lockName);
        grid.add(tfFirst, gc);
        gc.gridy++; gc.gridx = 0;

        grid.add(new JLabel("Prezime:"), gc);
        gc.gridx = 1;
        tfLast.setEnabled(!lockName);
        grid.add(tfLast, gc);
        gc.gridy++; gc.gridx = 0;

        // gender
        grid.add(new JLabel("Spol:"), gc);
        gc.gridx = 1;
        grid.add(cbGender, gc);
        gc.gridy++; gc.gridx = 0;

        // birth date (dropdowns)
        grid.add(new JLabel("Datum rođenja:"), gc);
        gc.gridx = 1;
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (int d = 1; d <= 31; d++) cbDay.addItem(d);
        for (String m : BOSNIAN_MONTHS) cbMonth.addItem(m);
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear; y >= 1900; y--) cbYear.addItem(y);
        datePanel.add(cbDay);
        datePanel.add(cbMonth);
        datePanel.add(cbYear);
        grid.add(datePanel, gc);
        gc.gridy++; gc.gridx = 0;

        // birthplace
        grid.add(new JLabel("Mjesto rođenja:"), gc);
        gc.gridx = 1;
        grid.add(tfBirthPlace, gc);
        gc.gridy++; gc.gridx = 0;

        // residence
        grid.add(new JLabel("Prebivalište / Grad:"), gc);
        gc.gridx = 1;
        grid.add(tfResidence, gc);
        gc.gridy++; gc.gridx = 0;

        // colonies
        grid.add(new JLabel("Broj pčelinjih zajednica:"), gc);
        gc.gridx = 1;
        grid.add(spColonies, gc);
        gc.gridy++; gc.gridx = 0;

        // certificate date
        grid.add(new JLabel("Datum potvrde:"), gc);
        gc.gridx = 1;
        JPanel certDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (int d = 1; d <= 31; d++) cbCertDay.addItem(d);
        for (String m : BOSNIAN_MONTHS) cbCertMonth.addItem(m);
        int currentYear2 = LocalDate.now().getYear();
        for (int y = currentYear2; y >= currentYear2 - 5; y--) cbCertYear.addItem(y);
        certDatePanel.add(cbCertDay);
        certDatePanel.add(cbCertMonth);
        certDatePanel.add(cbCertYear);
        grid.add(certDatePanel, gc);
        gc.gridy++; gc.gridx = 0;

        root.add(grid, BorderLayout.CENTER);

        // buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton bOk = new JButton("OK");
        JButton bCancel = new JButton("Odustani");

        // Style buttons with better colors
        bOk.setBackground(new Color(70, 130, 180));  // Steel blue
        bOk.setForeground(Color.WHITE);
        bOk.setFocusPainted(false);
        bOk.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        bCancel.setBackground(new Color(220, 220, 220));
        bCancel.setForeground(Color.BLACK);
        bCancel.setFocusPainted(false);
        bCancel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        // Add hover effects
        bOk.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                bOk.setBackground(new Color(65, 105, 225));  // Royal blue
            }

            @Override
            public void mouseExited(MouseEvent e) {
                bOk.setBackground(new Color(70, 130, 180));  // Back to steel blue
            }
        });

        bCancel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                bCancel.setBackground(new Color(200, 200, 200));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                bCancel.setBackground(new Color(220, 220, 220));
            }
        });

        buttons.add(bOk);
        buttons.add(bCancel);
        root.add(buttons, BorderLayout.SOUTH);

        bOk.addActionListener(e -> {
            if (!validateInput()) return;
            saveToUser();
            ok = true;
            setVisible(false);
        });
        bCancel.addActionListener(e -> {
            ok = false;
            setVisible(false);
        });

        // prefill fields if user has values
        if (user.getFirstName() != null) tfFirst.setText(user.getFirstName());
        if (user.getLastName() != null) tfLast.setText(user.getLastName());
        if (user.getGender() != null) cbGender.setSelectedItem(user.getGender());
        if (user.getBirthDate() != null) {
            LocalDate d = user.getBirthDate();
            cbDay.setSelectedItem(d.getDayOfMonth());
            cbMonth.setSelectedIndex(d.getMonthValue() - 1);
            cbYear.setSelectedItem(d.getYear());
        } else {
            cbDay.setSelectedItem(1);
            cbMonth.setSelectedIndex(0);
            cbYear.setSelectedItem(LocalDate.now().getYear());
        }
        if (user.getBirthPlace() != null) tfBirthPlace.setText(user.getBirthPlace());
        if (user.getResidenceCity() != null) tfResidence.setText(user.getResidenceCity());
        spColonies.setValue(user.getColonies() <= 0 ? 19 : user.getColonies());

        // Prefill certificate date
        if (user.getCertificateDate() != null) {
            LocalDate d = user.getCertificateDate();
            cbCertDay.setSelectedItem(d.getDayOfMonth());
            cbCertMonth.setSelectedIndex(d.getMonthValue() - 1);
            cbCertYear.setSelectedItem(d.getYear());
        } else {
            // Set to current date as default
            LocalDate today = LocalDate.now();
            cbCertDay.setSelectedItem(today.getDayOfMonth());
            cbCertMonth.setSelectedIndex(today.getMonthValue() - 1);
            cbCertYear.setSelectedItem(today.getYear());
        }

        setContentPane(root);
    }

    private boolean validateInput() {
        if (tfFirst.getText().trim().isEmpty() || tfLast.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ime i prezime su obavezni.", "Greška", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        // additional validation could go here
        return true;
    }

    private void saveToUser() {
        user.setFirstName(formatName(tfFirst.getText().trim()));
        user.setLastName(formatName(tfLast.getText().trim()));
        user.setGender((String) cbGender.getSelectedItem());
        int day = (Integer) cbDay.getSelectedItem();
        int month = cbMonth.getSelectedIndex() + 1;
        int year = (Integer) cbYear.getSelectedItem();
        try {
            user.setBirthDate(LocalDate.of(year, month, day));
        } catch (Exception ex) {
            user.setBirthDate(null);
        }
        // <-- promijenjeno: sačuvaj točno kako korisnik unese
        user.setBirthPlace(tfBirthPlace.getText().trim());
        user.setResidenceCity(tfResidence.getText().trim());
        user.setColonies(((Number) spColonies.getValue()).intValue());

        // Save certificate date
        int certDay = (Integer) cbCertDay.getSelectedItem();
        int certMonth = cbCertMonth.getSelectedIndex() + 1;
        int certYear = (Integer) cbCertYear.getSelectedItem();
        try {
            user.setCertificateDate(LocalDate.of(certYear, certMonth, certDay));
        } catch (Exception ex) {
            user.setCertificateDate(null);
        }
    }

    private String formatName(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String[] parts = raw.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (p.length() > 0) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public boolean isOk() { return ok; }

    public BeeUser getUser() { return user; }
}