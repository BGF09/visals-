package com.neontrails;

/** Утилиты для работы с цветом. Все цвета тут 0xRRGGBB без альфы. */
public final class TrailColors {
    private TrailColors() {}

    public static float time() {
        return (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int rainbow(float hue) {
        hue = hue - (float) Math.floor(hue);
        return java.awt.Color.HSBtoRGB(hue, 0.8f, 1f) & 0xFFFFFF;
    }

    /** t = 0 у головы следа, t = 1 у хвоста. */
    public static int colorAt(TrailConfig c, float t, float time) {
        return switch (c.colorMode) {
            case SOLID -> c.color1;
            case GRADIENT -> lerp(c.color1, c.color2, t);
            case RAINBOW -> rainbow(t * 0.7f - time * c.rainbowSpeed * 0.3f);
            case PULSE -> lerp(c.color1, c.color2,
                    (float) (Math.sin(time * c.rainbowSpeed * 3f - t * 6f) * 0.5 + 0.5));
        };
    }
}
