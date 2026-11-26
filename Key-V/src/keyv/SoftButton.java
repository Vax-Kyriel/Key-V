package keyv;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

final class SoftButton extends JButton {
    enum Kind { GREEN, BLUE }

    private final Kind kind;
    private boolean hover;

    SoftButton(String text, Kind kind) {
        super(text);
        this.kind = kind;
        setFont(Theme.UI);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width + 18, 36);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fill = fill();
        g.setColor(fill);
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g.setColor(isEnabled() ? ink() : Theme.MUTED);
        g.setFont(getFont());
        FontMetrics metrics = g.getFontMetrics();
        int x = (getWidth() - metrics.stringWidth(getText())) / 2;
        int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(getText(), x, y);
        g.dispose();
    }

    private Color ink() {
        return kind == Kind.GREEN ? Theme.GREEN_INK : Theme.BLUE_INK;
    }

    private Color fill() {
        if (!isEnabled()) {
            return Theme.DISABLED;
        }
        if (kind == Kind.GREEN) {
            return hover ? Theme.GREEN_HOVER : Theme.GREEN;
        }
        return hover ? Theme.BLUE_HOVER : Theme.BLUE;
    }
}
