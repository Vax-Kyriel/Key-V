package keyv;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

final class SoftScrollBarUI extends BasicScrollBarUI {
    @Override
    protected JButton createDecreaseButton(int orientation) {
        return hiddenButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return hiddenButton();
    }

    @Override
    public Dimension getPreferredSize(JComponent component) {
        return scrollbar.getOrientation() == VERTICAL ? new Dimension(18, 80) : new Dimension(80, 18);
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle track) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.CARD);
        g.fillRect(track.x, track.y, track.width, track.height);
        g.setColor(new java.awt.Color(0xE3F2F4));
        if (scrollbar.getOrientation() == VERTICAL) {
            g.fillRoundRect(track.x + 4, track.y + 6, track.width - 8, track.height - 12, 12, 12);
        } else {
            g.fillRoundRect(track.x + 6, track.y + 4, track.width - 12, track.height - 8, 12, 12);
        }
        g.dispose();
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumb) {
        if (thumb.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean vertical = scrollbar.getOrientation() == VERTICAL;
        int x = thumb.x + (vertical ? 4 : 6);
        int y = thumb.y + (vertical ? 4 : 4);
        int w = vertical ? thumb.width - 8 : thumb.width - 8;
        int h = vertical ? thumb.height - 8 : thumb.height - 8;
        if (vertical && h > 56) {
            int shown = Math.min(h, 64);
            y += (h - shown) / 2;
            h = shown;
        }
        if (!vertical && w > 56) {
            int shown = Math.min(w, 64);
            x += (w - shown) / 2;
            w = shown;
        }
        g.setColor(isDragging ? Theme.GREEN_INK : new java.awt.Color(0x7DCAA8));
        int arc = vertical ? w : h;
        g.fillRoundRect(x, y, Math.max(w, 8), Math.max(h, 8), arc, arc);
        g.dispose();
    }

    private static JButton hiddenButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        return button;
    }
}
