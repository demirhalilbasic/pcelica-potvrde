package com.pcelica.ui;

import com.pcelica.model.BeeUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.stream.IntStream;

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
    private final boolean lockName;

    private static final String[] BOSNIAN_MONTHS = new String[]{
            "januar","februar","mart","april","maj","juni","juli","avgust","septembar","oktobar","novembar","decembar"
    };

    public UserDialog(Window owner, String title, BeeUser u) {
        this(owner, title, u, false);
    }

    /**
     * @param owner parent
     * @param title title
     * @param u BeeUser prefilled (may have null fields)
     * @param lockName if true, first and last name fields will be disabled (cannot be edited)
     */
    public UserDialog(Window owner, String title, BeeUser u, boolean lockName) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.user = u == null ? new BeeUser() : u;
        this.lockName = lockName;
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
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
        grid.add(tfFirst, gc);
        gc.gridy++; gc.gridx = 0;

        grid.add(new JLabel("Prezime:"), gc);
        gc.gridx = 1;
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

        root.add(grid, BorderLayout.CENTER);

        // buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton bOk = new JButton("OK");
        JButton bCancel = new JButton("Odustani");
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

        // lock name fields if requested
        tfFirst.setEnabled(!lockName);
        tfLast.setEnabled(!lockName);

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
        user.setBirthPlace(formatName(tfBirthPlace.getText().trim()));
        user.setResidenceCity(formatName(tfResidence.getText().trim()));
        user.setColonies(((Number) spColonies.getValue()).intValue());
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
