package keyv;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

final class Logo extends JComponent {
    Logo() {
        setOpaque(false);
        setPreferredSize(new Dimension(72, 72));
        setMinimumSize(new Dimension(72, 72));
    }

    static Image icon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        paintKey(graphics, 64);
        graphics.dispose();
        return image;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        paintKey(g, Math.min(getWidth(), getHeight()));
        g.dispose();
    }

    private static void paintKey(Graphics2D g, int size) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(size / 64.0, size / 64.0);
        g.setColor(new java.awt.Color(28, 70, 80, 36));
        g.fillRoundRect(10, 18, 48, 28, 20, 20);
        g.setColor(new java.awt.Color(0x7DCAA8));
        g.fillRoundRect(30, 24, 26, 9, 8, 8);
        g.fillRoundRect(46, 31, 5, 9, 3, 3);
        g.fillRoundRect(53, 31, 5, 7, 3, 3);
        g.setColor(new java.awt.Color(0x8EC8E0));
        g.fillOval(6, 8, 34, 34);
        g.setColor(Theme.CARD);
        g.fillOval(13, 15, 20, 20);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.setColor(Theme.GREEN_INK);
        FontMetrics metrics = g.getFontMetrics();
        int x = 13 + (20 - metrics.stringWidth("V")) / 2;
        int y = 15 + (20 - metrics.getHeight()) / 2 + metrics.getAscent() - 1;
        g.drawString("V", x, y);
    }
}
