package com.pcelica.ui;

import com.pcelica.model.BeeUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class DetailDialog extends JDialog {
    private final BeeUser user;
    private final boolean readOnly;
    private final MainFrame parent;

    public DetailDialog(MainFrame parent, BeeUser user, boolean readOnly) {
        super(parent, "Detalji pčelara", true);
        this.parent = parent;
        this.user = user;
        this.readOnly = readOnly;
        init();
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void init() {
        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(new EmptyBorder(14,14,14,14));

        // header
        JPanel header = new JPanel(new BorderLayout());
        JLabel name = new JLabel(user.getFirstName() + " " + user.getLastName());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 20f));
        header.add(name, BorderLayout.WEST);

        JLabel idLabel = new JLabel(user.getDocNumber() == null ? "" : user.getDocNumber());
        idLabel.setFont(idLabel.getFont().deriveFont(Font.PLAIN, 12f));
        header.add(idLabel, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // main details area
        JPanel details = new JPanel(new GridBagLayout());
        details.setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8,8,8,8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;

        addRow(details, gc, "Spol:", user.getGender());
        addRow(details, gc, "Datum rođenja:", user.getBirthDate() == null ? "" : user.getBirthDate().toString());
        addRow(details, gc, "Mjesto rođenja:", user.getBirthPlace());
        addRow(details, gc, "Prebivalište:", user.getResidenceCity());
        addRow(details, gc, "Broj pčelinjih zajednica:", String.valueOf(user.getColonies()));

        root.add(details, BorderLayout.CENTER);

        // buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton bExport = new JButton("Export PDF");
        JButton bEdit = new JButton(readOnly ? "Učitaj snapshot kao glavni" : "Uredi");
        JButton bClose = new JButton("Zatvori");

        bExport.addActionListener(e -> {
            try {
                File f = parent.exportUserPdf(user);
                JOptionPane.showMessageDialog(this, "Exportirano: " + f.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Greška pri exportu: " + ex.getMessage());
            }
        });

        bEdit.addActionListener(e -> {
            if (readOnly) {
                int r = JOptionPane.showConfirmDialog(this, "Želite li učitati ovaj snapshot kao novi glavni snapshot kako biste mogli uređivati?", "Import snapshot", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) {
                    parent.importCurrentSnapshotAsMain();
                    setVisible(false);
                } else {
                    parent.exitSnapshotView();
                    setVisible(false);
                }
            } else {
                parent.editUserFromDetails(user);
                setVisible(false);
            }
        });

        bClose.addActionListener(e -> setVisible(false));

        btns.add(bExport);
        btns.add(bEdit);
        btns.add(bClose);

        root.add(btns, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void addRow(JPanel panel, GridBagConstraints gc, String label, String value) {
        gc.gridx = 0;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(l, gc);

        gc.gridx = 1;
        JLabel v = new JLabel(value == null ? "" : value);
        v.setFont(v.getFont().deriveFont(Font.PLAIN, 13f));
        panel.add(v, gc);

        gc.gridy++;
    }
}
