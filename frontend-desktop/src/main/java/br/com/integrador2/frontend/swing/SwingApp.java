package br.com.integrador2.frontend.swing;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Ponto de entrada de exemplo para a interface Swing. Substituir pelas telas
 * reais definidas na spec do frontend.
 */
public final class SwingApp {

    private SwingApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SwingApp::createAndShow);
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("integrador2 - Swing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(new JLabel("Frontend Swing pronto.", JLabel.CENTER));
        frame.setSize(400, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
