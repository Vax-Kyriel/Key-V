package keyv;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

final class LangChip extends JComponent {
    private final Lang.Code code;
    private final Runnable onPick;
    private boolean hover;

    LangChip(Lang.Code code, Runnable onPick) {
        this.code = code;
        this.onPick = onPick;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(46, 32));
        setFont(Theme.UI);
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

            @Override
            public void mouseClicked(MouseEvent event) {
                onPick.run();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean selected = Lang.code() == code;
        g.setColor(selected ? Theme.GREEN : (hover ? Theme.BLUE_HOVER : Theme.BLUE));
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g.setColor(selected ? Theme.GREEN_INK : Theme.BLUE_INK);
        g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 13f));
        String text = code == Lang.Code.FR ? "FR" : "EN";
        FontMetrics metrics = g.getFontMetrics();
        int x = (getWidth() - metrics.stringWidth(text)) / 2;
        int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(text, x, y);
        g.dispose();
    }
}
