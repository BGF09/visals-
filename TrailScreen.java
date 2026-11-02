package com.neontrails.gui;

import com.neontrails.TrailColors;
import com.neontrails.TrailConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.*;

/** Красивое меню настроек. Вся отрисовка кастомная, координаты — локальные (0..W, 0..H). */
public class TrailScreen extends Screen {

    private static final int W = 430, H = 330;

    private static final int BG = 0xF0101118;
    private static final int ELEM = 0xFF222438;
    private static final int ELEM_HOVER = 0xFF2D3050;
    private static final int TRACK = 0xFF3A3D5C;
    private static final int TEXT = 0xFFE6E8F5;
    private static final int MUTED = 0xFF8A8FB0;

    private static final String[] PRESET_NAMES = {"Neon", "Sunset", "Toxic", "Fire", "Ocean", "Candy", "Ice", "Blood"};
    private static final int[][] PRESETS = {
            {0x00E5FF, 0xB400FF}, {0xFF512F, 0xF09819}, {0x00FF87, 0x60EFFF}, {0xFF0000, 0xFFD000},
            {0x0061FF, 0x60EFFF}, {0xFF5EDF, 0xFFE53B}, {0xFFFFFF, 0x7DD3FC}, {0x7F0000, 0xFF1744}
    };

    private final List<Control> controls = new ArrayList<>();
    private Control dragging;
    private float scale = 1f;
    private int editing = 1; // какой цвет сейчас редактируем: 1 или 2

    public TrailScreen() {
        super(Text.literal("NeonTrails"));
    }

    private static TrailConfig cfg() { return TrailConfig.I; }

    private int getEdit() { return editing == 1 ? cfg().color1 : cfg().color2; }

    private void setEdit(int rgb) {
        if (editing == 1) cfg().color1 = rgb & 0xFFFFFF; else cfg().color2 = rgb & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ build
    @Override
    protected void init() {
        controls.clear();

        // ---------- левая колонка ----------
        int lx = 16, lw = 196;
        controls.add(new Toggle(lx, 56, 96, 20, "Включено", () -> cfg().enabled, v -> cfg().enabled = v));
        controls.add(new Toggle(lx + 100, 56, 96, 20, "Свечение", () -> cfg().glow, v -> cfg().glow = v));
        controls.add(new Toggle(lx, 80, 96, 20, "Я сам", () -> cfg().showSelf, v -> cfg().showSelf = v));
        controls.add(new Toggle(lx + 100, 80, 96, 20, "Другие", () -> cfg().showOthers, v -> cfg().showOthers = v));
        controls.add(new Toggle(lx, 104, 96, 20, "Сужение", () -> cfg().taper, v -> cfg().taper = v));
        controls.add(new Toggle(lx + 100, 104, 96, 20, "Сквозь", () -> cfg().throughWalls, v -> cfg().throughWalls = v));

        controls.add(new Cycle(lx, 128, lw, 20, "Стиль",
                () -> cfg().style.label,
                () -> cfg().style = cfg().style.next(),
                () -> cfg().style = cfg().style.prev()));

        controls.add(new Slider(lx, 152, lw, 22, "Ширина", 0.05, 1.5, () -> cfg().width,
                v -> cfg().width = (float) v, v -> String.format("%.2f", v), false));
        controls.add(new Slider(lx, 176, lw, 22, "Длина", 5, 150, () -> cfg().length,
                v -> cfg().length = (int) Math.round(v), v -> String.valueOf((int) Math.round(v)), true));
        controls.add(new Slider(lx, 200, lw, 22, "Прозрачность", 0.1, 1.0, () -> cfg().alpha,
                v -> cfg().alpha = (float) v, v -> (int) Math.round(v * 100) + "%", false));
        controls.add(new Slider(lx, 224, lw, 22, "Высота", 0.0, 2.0, () -> cfg().height,
                v -> cfg().height = (float) v, v -> String.format("%.2f", v), false));
        controls.add(new Slider(lx, 248, lw, 22, "Скорость цвета", 0.1, 4.0, () -> cfg().rainbowSpeed,
                v -> cfg().rainbowSpeed = (float) v, v -> String.format("%.1fx", v), false));

        // ---------- правая колонка ----------
        int rx = 228, rw = 186;
        controls.add(new Cycle(rx, 56, rw, 20, "Цвет",
                () -> cfg().colorMode.label,
                () -> cfg().colorMode = cfg().colorMode.next(),
                () -> cfg().colorMode = cfg().colorMode.prev()));

        controls.add(new Button(rx, 80, 91, 20, "Цвет 1", () -> editing = 1, () -> editing == 1, () -> cfg().color1));
        controls.add(new Button(rx + 95, 80, 91, 20, "Цвет 2", () -> editing = 2, () -> editing == 2, () -> cfg().color2));

        addChannel("R", 16, rx, 104, rw);
        addChannel("G", 8, rx, 128, rw);
        addChannel("B", 0, rx, 152, rw);

        for (int i = 0; i < PRESETS.length; i++) {
            final int[] pr = PRESETS[i];
            final String name = PRESET_NAMES[i];
            int col = i % 4, row = i / 4;
            controls.add(new Swatch(rx + col * 48, 198 + row * 22, 42, 18, pr[0], pr[1], name, () -> {
                cfg().color1 = pr[0];
                cfg().color2 = pr[1];
            }));
        }

        controls.add(new Button(rx, 246, 91, 22, "Случайно", () -> {
            Random r = new Random();
            float h = r.nextFloat();
            cfg().color1 = TrailColors.rainbow(h);
            cfg().color2 = TrailColors.rainbow(h + 0.2f + r.nextFloat() * 0.4f);
        }, null, null));
        controls.add(new Button(rx + 95, 246, 91, 22, "Сброс", () -> TrailConfig.I = new TrailConfig(), null, null));
    }

    private void addChannel(String label, int shift, int x, int y, int w) {
        controls.add(new Slider(x, y, w, 22, label, 0, 255,
                () -> (getEdit() >> shift) & 255,
                v -> setEdit((getEdit() & ~(255 << shift)) | (((int) Math.round(v)) << shift)),
                v -> String.valueOf((int) Math.round(v)), true,
                () -> (getEdit() & ~(255 << shift)),
                () -> (getEdit() & ~(255 << shift)) | (255 << shift)));
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        float time = TrailColors.time();
        TextRenderer tr = this.textRenderer;

        c.fill(0, 0, width, height, 0xA0000000);

        scale = Math.min(1f, Math.min((height - 8f) / H, (width - 8f) / W));
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(width / 2f, height / 2f, 0);
        m.scale(scale, scale, 1f);
        m.translate(-W / 2f, -H / 2f, 0);

        int mx = lx(mouseX), my = ly(mouseY);

        // панель + рамка
        rrect(c, -1, -1, W + 2, H + 2, 0xFF2A2C44);
        rrect(c, 0, 0, W, H, BG);

        // шапка
        m.push();
        m.translate(16, 10, 0);
        m.scale(1.6f, 1.6f, 1f);
        String title = "NeonTrails";
        int tx = 0;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            int col = 0xFF000000 | TrailColors.rainbow(i * 0.07f + time * 0.15f);
            c.drawText(tr, ch, tx, 0, col, true);
            tx += tr.getWidth(ch);
        }
        m.pop();
        String sub = "visual / trails";
        c.drawText(tr, sub, W - 16 - tr.getWidth(sub), 14, MUTED, false);
        c.drawText(tr, "Right Shift — закрыть/открыть", W - 16 - tr.getWidth("Right Shift — закрыть/открыть"), 24, 0xFF5E6285, false);

        // анимированная линия
        for (int i = 0; i < W; i++) {
            c.fill(i, 36, i + 1, 38, 0xFF000000 | TrailColors.rainbow(i / (float) W - time * 0.25f));
        }

        c.drawText(tr, "НАСТРОЙКИ", 16, 44, MUTED, false);
        c.drawText(tr, "ЦВЕТ", 228, 44, MUTED, false);

        for (Control ctl : controls) ctl.render(c, tr, mx, my);

        // hex-код и квадрат с цветом
        int e = getEdit();
        String hex = String.format("#%06X", e);
        c.drawText(tr, "Цвет " + editing + ":", 228, 180, MUTED, false);
        c.drawText(tr, hex, 228 + tr.getWidth("Цвет 0: ") + 2, 180, 0xFF000000 | e, false);
        c.drawText(tr, "ПРЕСЕТЫ", 228, 189, MUTED, false);

        drawPreview(c, tr, time);

        m.pop();
    }

    private void drawPreview(DrawContext c, TextRenderer tr, float time) {
        TrailConfig cfg = cfg();
        int px = 16, py = 282, pw = 398, ph = 36;
        rrect(c, px, py, pw, ph, 0xFF0A0B12);

        int maxCols = (pw - 24) / 2;
        int cols = Math.max(8, (int) (maxCols * Math.max(0.12f, cfg.length / 150f)));
        int headX = px + pw - 12;
        float base = 1.5f + cfg.width * 4f;

        for (int i = 0; i < cols; i++) {
            float t = 1f - i / (float) (cols - 1);        // i = cols-1 -> голова
            float fade = (float) Math.pow(1f - t, 0.8);
            int x = headX - (cols - 1 - i) * 2;
            int rgb = TrailColors.colorAt(cfg, t, time);
            int a = clamp((int) (255 * cfg.alpha * fade));
            int aGlow = clamp((int) (255 * cfg.alpha * fade * 0.22f));
            float hw = base * (cfg.taper ? fade : 1f);
            int cy = py + ph / 2 + (int) (Math.sin(i * 0.09 + time * 3) * 7);

            switch (cfg.style) {
                case RIBBON -> {
                    if (cfg.glow) c.fill(x, (int) (cy - hw * 2.2f), x + 2, (int) (cy + hw * 2.2f) + 1, (aGlow << 24) | rgb);
                    c.fill(x, (int) (cy - hw), x + 2, (int) (cy + hw) + 1, (a << 24) | rgb);
                }
                case HELIX -> {
                    for (int s = 0; s < 2; s++) {
                        int yy = cy + (int) (Math.sin(i * 0.35 - time * 4 + s * Math.PI) * base * 1.6f);
                        if (cfg.glow) c.fill(x - 1, yy - 2, x + 3, yy + 3, (aGlow << 24) | rgb);
                        c.fill(x, yy - 1, x + 2, yy + 1, (a << 24) | rgb);
                    }
                }
                case DOTS -> {
                    if (i % 4 == 0) {
                        int yy = cy + (int) (Math.sin(i * 12.9898) * base * 1.5f);
                        int sz = Math.max(1, (int) (hw * 0.6f) + 1);
                        if (cfg.glow) c.fill(x - sz, yy - sz, x + sz + 2, yy + sz + 2, (aGlow << 24) | rgb);
                        c.fill(x, yy, x + sz, yy + sz, (a << 24) | rgb);
                    }
                }
            }
        }
        c.drawText(tr, "Превью", px + 6, py + 4, 0xFF5E6285, false);
    }

    private static int clamp(int a) { return Math.max(0, Math.min(255, a)); }

    // ------------------------------------------------------------------ input
    private int lx(double mx) { return (int) Math.floor((mx - width / 2.0) / scale + W / 2.0); }
    private int ly(double my) { return (int) Math.floor((my - height / 2.0) / scale + H / 2.0); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = lx(mouseX), my = ly(mouseY);
        for (Control ctl : controls) {
            if (ctl.hit(mx, my)) {
                if (ctl.click(mx, my, button)) dragging = ctl;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(lx(mouseX), ly(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() { TrailConfig.save(); }

    // ------------------------------------------------------------------ draw helpers
    static void rrect(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + w, y + h - 1, color);
    }

    static void hGrad(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i++) {
            float t = w <= 1 ? 0 : i / (float) (w - 1);
            c.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | TrailColors.lerp(c1, c2, t));
        }
    }

    // ------------------------------------------------------------------ widgets
    private abstract static class Control {
        final int x, y, w, h;
        Control(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean hit(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
        abstract void render(DrawContext c, TextRenderer tr, int mx, int my);
        /** @return true, если после клика контрол надо "тянуть" мышью */
        boolean click(int mx, int my, int button) { return false; }
        void drag(int mx, int my) {}
    }

    private static final class Toggle extends Control {
        final String label; final BooleanSupplier get; final Consumer<Boolean> set;
        Toggle(int x, int y, int w, int h, String label, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, w, h); this.label = label; this.get = get; this.set = set;
        }
        @Override void render(DrawContext c, TextRenderer tr, int mx, int my) {
            rrect(c, x, y, w, h, hit(mx, my) ? ELEM_HOVER : ELEM);
            boolean on = get.getAsBoolean();
            int sw = 22, sh = 10, sx = x + w - sw - 6, sy = y + (h - sh) / 2;
            rrect(c, sx, sy, sw, sh, on ? (0xFF000000 | cfg().color1) : TRACK);
            int kx = on ? sx + sw - sh + 1 : sx + 1;
            c.fill(kx, sy + 1, kx + sh - 2, sy + sh - 1, 0xFFFFFFFF);
            c.drawText(tr, label, x + 7, y + (h - 8) / 2, on ? TEXT : MUTED, false);
        }
        @Override boolean click(int mx, int my, int button) { set.accept(!get.getAsBoolean()); return false; }
    }

    private static final class Cycle extends Control {
        final String label; final Supplier<String> value; final Runnable next, prev;
        Cycle(int x, int y, int w, int h, String label, Supplier<String> value, Runnable next, Runnable prev) {
            super(x, y, w, h); this.label = label; this.value = value; this.next = next; this.prev = prev;
        }
        @Override void render(DrawContext c, TextRenderer tr, int mx, int my) {
            rrect(c, x, y, w, h, hit(mx, my) ? ELEM_HOVER : ELEM);
            c.drawText(tr, label, x + 7, y + (h - 8) / 2, MUTED, false);
            String s = "< " + value.get() + " >";
            c.drawText(tr, s, x + w - 7 - tr.getWidth(s), y + (h - 8) / 2, 0xFF000000 | cfg().color1, false);
        }
        @Override boolean click(int mx, int my, int button) {
            if (button == 1) prev.run(); else next.run();
            return false;
        }
    }

    private static final class Slider extends Control {
        final String label; final double min, max;
        final DoubleSupplier get; final DoubleConsumer set; final DoubleFunction<String> fmt;
        final boolean integer; final IntSupplier left, right;

        Slider(int x, int y, int w, int h, String label, double min, double max, DoubleSupplier get,
               DoubleConsumer set, DoubleFunction<String> fmt, boolean integer) {
            this(x, y, w, h, label, min, max, get, set, fmt, integer, null, null);
        }
        Slider(int x, int y, int w, int h, String label, double min, double max, DoubleSupplier get,
               DoubleConsumer set, DoubleFunction<String> fmt, boolean integer, IntSupplier left, IntSupplier right) {
            super(x, y, w, h);
            this.label = label; this.min = min; this.max = max; this.get = get; this.set = set;
            this.fmt = fmt; this.integer = integer; this.left = left; this.right = right;
        }

        @Override void render(DrawContext c, TextRenderer tr, int mx, int my) {
            double v = get.getAsDouble();
            double t = Math.max(0, Math.min(1, (v - min) / (max - min)));
            rrect(c, x, y, w, h, hit(mx, my) ? ELEM_HOVER : ELEM);
            c.drawText(tr, label, x + 7, y + 3, TEXT, false);
            String s = fmt.apply(v);
            c.drawText(tr, s, x + w - 7 - tr.getWidth(s), y + 3, MUTED, false);

            int tx = x + 7, tw = w - 14, ty = y + h - 8;
            if (left != null) {
                hGrad(c, tx, ty, tw, 4, left.getAsInt(), right.getAsInt());
            } else {
                c.fill(tx, ty, tx + tw, ty + 4, TRACK);
                int fw = (int) (tw * t);
                if (fw > 0) hGrad(c, tx, ty, fw, 4, cfg().color1, TrailColors.lerp(cfg().color1, cfg().color2, (float) t));
            }
            int kx = tx + (int) (tw * t);
            c.fill(kx - 2, ty - 2, kx + 2, ty + 6, 0xFFFFFFFF);
        }

        private void apply(int mx) {
            double t = Math.max(0, Math.min(1, (mx - (x + 7)) / (double) (w - 14)));
            double v = min + (max - min) * t;
            set.accept(integer ? Math.round(v) : v);
        }
        @Override boolean click(int mx, int my, int button) { apply(mx); return true; }
        @Override void drag(int mx, int my) { apply(mx); }
    }

    private static final class Button extends Control {
        final String label; final Runnable action; final BooleanSupplier selected; final IntSupplier swatch;
        Button(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier selected, IntSupplier swatch) {
            super(x, y, w, h); this.label = label; this.action = action; this.selected = selected; this.swatch = swatch;
        }
        @Override void render(DrawContext c, TextRenderer tr, int mx, int my) {
            boolean sel = selected != null && selected.getAsBoolean();
            if (sel) rrect(c, x - 1, y - 1, w + 2, h + 2, 0xFF000000 | cfg().color1);
            rrect(c, x, y, w, h, hit(mx, my) ? ELEM_HOVER : ELEM);
            int tx = x + (w - tr.getWidth(label)) / 2;
            if (swatch != null) {
                c.fill(x + 6, y + 5, x + 16, y + h - 5, 0xFF000000 | swatch.getAsInt());
                tx = x + 22;
            }
            c.drawText(tr, label, tx, y + (h - 8) / 2, sel || swatch == null ? TEXT : MUTED, false);
        }
        @Override boolean click(int mx, int my, int button) { action.run(); return false; }
    }

    private static final class Swatch extends Control {
        final int c1, c2; final String name; final Runnable action;
        Swatch(int x, int y, int w, int h, int c1, int c2, String name, Runnable action) {
            super(x, y, w, h); this.c1 = c1; this.c2 = c2; this.name = name; this.action = action;
        }
        @Override void render(DrawContext c, TextRenderer tr, int mx, int my) {
            boolean hov = hit(mx, my);
            if (hov) rrect(c, x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF);
            hGrad(c, x, y, w, h, c1, c2);
            if (hov) {
                int tw = tr.getWidth(name);
                c.fill(x + (w - tw) / 2 - 2, y + 4, x + (w + tw) / 2 + 2, y + h - 4, 0xA0000000);
                c.drawText(tr, name, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
            }
        }
        @Override boolean click(int mx, int my, int button) { action.run(); return false; }
    }
}
