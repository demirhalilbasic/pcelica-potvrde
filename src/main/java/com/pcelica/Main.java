package com.pcelica;

import com.pcelica.store.DataStore;
import com.pcelica.ui.MainFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Set modern Look & Feel (Nimbus) if available
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            // tweak some defaults for a cleaner look
            UIManager.put("Table.rowHeight", 28);
            UIManager.put("Table.font", new javax.swing.plaf.FontUIResource("SansSerif", java.awt.Font.PLAIN, 13));
            UIManager.put("TableHeader.font", new javax.swing.plaf.FontUIResource("SansSerif", java.awt.Font.BOLD, 13));
            UIManager.put("Button.font", new javax.swing.plaf.FontUIResource("SansSerif", java.awt.Font.PLAIN, 13));
            UIManager.put("Label.font", new javax.swing.plaf.FontUIResource("SansSerif", java.awt.Font.PLAIN, 13));
            UIManager.put("TextField.font", new javax.swing.plaf.FontUIResource("SansSerif", java.awt.Font.PLAIN, 13));
            UIManager.put("ComboBox.font", new javax.swing.plaf.FontUIResource("SansSerif", java.awt.Font.PLAIN, 13));
        } catch (Exception e) {
            // fallback to default LAF
        }

        SwingUtilities.invokeLater(() -> {
            try {
                DataStore ds = new DataStore();
                MainFrame frame = new MainFrame(ds);
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Greška pri pokretanju aplikacije: " + e.getMessage());
            }
        });
    }
}
