package keyv;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JComponent;
import javax.swing.Timer;

final class FluffyCat extends JComponent {
    static final int EVERY_MS = 3 * 60 * 1000;
    private static final int VISIT_MS = 4600;
    private static final Color BLACK = new Color(0x161616);
    private static final Color EAR = new Color(0x8E79B8);
    private static final Color MOUTH = new Color(0xF0728C);
    private static final Color TONGUE = new Color(0xF7A8B8);

    private final Timer clock;
    private Timer frames;
    private long started;
    private boolean playing;

    FluffyCat() {
        setOpaque(false);
        clock = new Timer(EVERY_MS, event -> visit());
        clock.setInitialDelay(1500);
        clock.setRepeats(true);
    }

    void startSchedule() {
        if (!clock.isRunning()) {
            clock.start();
        }
    }

    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    private void visit() {
        if (playing || getWidth() < 40) {
            return;
        }
        playing = true;
        started = System.nanoTime();
        if (frames == null) {
            frames = new Timer(33, event -> tick());
        }
        frames.start();
    }

    private void tick() {
        float progress = (System.nanoTime() - started) / 1_000_000f / VISIT_MS;
        if (progress >= 1f) {
            frames.stop();
            playing = false;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (!playing) {
            return;
        }
        float progress = Math.min(1f, (System.nanoTime() - started) / 1_000_000f / VISIT_MS);
        float alpha = 1f;
        float rest = getHeight() * 0.64f;
        float below = getHeight() + 170f;
        float y;
        if (progress < 0.34f) {
            float u = ease(progress / 0.34f);
            y = below + (rest - below) * u;
        } else if (progress > 0.78f) {
            float u = ease((progress - 0.78f) / 0.22f);
            y = rest + (below - rest) * u;
            alpha = 1f - u;
        } else {
            y = rest;
        }
        float paw = tap(progress, 0.40f, 0.54f) + tap(progress, 0.58f, 0.74f);
        float blink = 0f;
        if (progress > 0.46f && progress < 0.52f) {
            blink = (float) Math.sin((progress - 0.46f) / 0.06f * Math.PI);
        }
        float sway = (float) Math.sin(progress * Math.PI * 2) * 5f;

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        g.translate(getWidth() * 0.68, y);
        paintCat(g, paw, blink, sway);
        if (paw > 12f) {
            float ripple = (paw - 12f) / 10f;
            g.setColor(new Color(0x8E, 0x79, 0xB8, (int) (120 * ripple)));
            g.setStroke(new BasicStroke(2f));
            int ring = (int) (18 + ripple * 14);
            g.drawOval(4, (int) (78 + paw), ring, (int) (ring * 0.4));
        }
        g.dispose();
    }

    private static float ease(float u) {
        float t = Math.max(0f, Math.min(1f, u));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float tap(float progress, float start, float end) {
        if (progress < start || progress > end) {
            return 0f;
        }
        float u = (progress - start) / (end - start);
        return (float) Math.sin(u * Math.PI) * 18f;
    }

    private static void paintCat(Graphics2D g, float paw, float blink, float sway) {
        g.setColor(new Color(0, 0, 0, 40));
        g.fillOval(-40, 88, 88, 16);

        g.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(BLACK);
        Path2D tail = new Path2D.Float();
        tail.moveTo(16, 28);
        tail.curveTo(70, 36, 78, -20 + sway, 46, -62 + sway);
        g.draw(tail);
        g.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D hook = new Path2D.Float();
        hook.moveTo(52, -46 + sway);
        hook.quadTo(28, -86 + sway, 22, -58 + sway);
        g.draw(hook);

        g.fillRoundRect(-24, -50, 48, 100, 40, 40);
        g.fillOval(-32, 16, 64, 72);
        g.fillOval(-40, -116, 80, 78);

        g.fillPolygon(new int[] {-30, -8, -42}, new int[] {-100, -150, -112}, 3);
        g.fillPolygon(new int[] {10, 32, 44}, new int[] {-150, -100, -112}, 3);
        g.setColor(EAR);
        g.fillPolygon(new int[] {-24, -12, -32}, new int[] {-106, -138, -112}, 3);
        g.fillPolygon(new int[] {16, 26, 34}, new int[] {-138, -106, -112}, 3);

        int eyeH = Math.max(4, Math.round(30 * (1f - blink * 0.82f)));
        int eyeY = -88 + (30 - eyeH) / 2;
        g.setColor(new Color(0xF7FCFF));
        g.fillOval(-32, eyeY, 28, eyeH);
        g.fillOval(6, eyeY, 28, eyeH);
        if (eyeH > 12) {
            g.setColor(BLACK);
            g.fillOval(-20, eyeY + 4, 8, 9);
            g.fillOval(16, eyeY + 4, 8, 9);
            g.setColor(Color.WHITE);
            g.fillOval(-22, eyeY + 6, 4, 4);
            g.fillOval(14, eyeY + 6, 4, 4);
        }

        g.setColor(MOUTH);
        g.fillOval(-16, -58, 32, 18);
        g.setColor(BLACK);
        g.fillOval(-18, -66, 36, 14);
        g.setColor(TONGUE);
        g.fillOval(-8, -50, 16, 10);
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[] {-10, -4, -7}, new int[] {-58, -58, -48}, 3);
        g.fillPolygon(new int[] {4, 10, 7}, new int[] {-58, -58, -48}, 3);
        g.setColor(BLACK);
        g.fillOval(-5, -68, 10, 6);

        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(-32, -60, -62, -68);
        g.drawLine(-34, -54, -66, -54);
        g.drawLine(-32, -48, -60, -42);
        g.drawLine(32, -60, 62, -68);
        g.drawLine(34, -54, 66, -54);
        g.drawLine(32, -48, 60, -42);

        int pawY = Math.round(paw);
        g.fillOval(-26, 64, 22, 18);
        g.fillOval(6, 62 + pawY, 24, 18);
    }
}
