package com.pcelica;

import com.pcelica.store.DataStore;
import com.pcelica.ui.MainFrame;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        // Look & Feel setup
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            UIManager.put("Table.rowHeight", 28);
            UIManager.put("Table.font", new javax.swing.plaf.FontUIResource("SansSerif", Font.PLAIN, 13));
            UIManager.put("TableHeader.font", new javax.swing.plaf.FontUIResource("SansSerif", Font.BOLD, 13));
            UIManager.put("Button.font", new javax.swing.plaf.FontUIResource("SansSerif", Font.PLAIN, 13));
            UIManager.put("Label.font", new javax.swing.plaf.FontUIResource("SansSerif", Font.PLAIN, 13));
            UIManager.put("TextField.font", new javax.swing.plaf.FontUIResource("SansSerif", Font.PLAIN, 13));
            UIManager.put("ComboBox.font", new javax.swing.plaf.FontUIResource("SansSerif", Font.PLAIN, 13));
        } catch (Exception e) {
            // fallback
        }

        SwingUtilities.invokeLater(() -> {
            try {
                DataStore ds = new DataStore();
                MainFrame frame = new MainFrame(ds);

                // 🔹 Učitaj ikonicu iz resursa
                try {
                    ImageIcon icon = new ImageIcon(
                            Main.class.getResource("/icons/logo.png")
                    );
                    frame.setIconImage(icon.getImage());
                } catch (Exception ex) {
                    System.err.println("Ikonica nije pronađena: " + ex.getMessage());
                }

                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Greška pri pokretanju aplikacije: " + e.getMessage());
            }
        });
    }
}