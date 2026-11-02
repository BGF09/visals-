package com.neontrails;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Все настройки мода. Хранятся в config/neontrails.json */
public class TrailConfig {

    public enum Style {
        RIBBON("Лента"), HELIX("Спираль"), DOTS("Искры");
        public final String label;
        Style(String l) { label = l; }
        public Style next() { return values()[(ordinal() + 1) % values().length]; }
        public Style prev() { return values()[(ordinal() + values().length - 1) % values().length]; }
    }

    public enum ColorMode {
        SOLID("Один цвет"), GRADIENT("Градиент"), RAINBOW("Радуга"), PULSE("Пульс");
        public final String label;
        ColorMode(String l) { label = l; }
        public ColorMode next() { return values()[(ordinal() + 1) % values().length]; }
        public ColorMode prev() { return values()[(ordinal() + values().length - 1) % values().length]; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("neontrails.json");

    /** Текущий конфиг (всегда обращайтесь через TrailConfig.I) */
    public static TrailConfig I = new TrailConfig();

    public boolean enabled = true;
    public boolean showSelf = true;
    public boolean showOthers = false;
    public boolean glow = true;
    public boolean taper = true;
    public boolean throughWalls = false;

    public Style style = Style.RIBBON;
    public ColorMode colorMode = ColorMode.GRADIENT;

    /** Цвета в формате 0xRRGGBB */
    public int color1 = 0x00E5FF;
    public int color2 = 0xB400FF;

    public float width = 0.35f;        // ширина ленты в блоках
    public int length = 40;            // длина следа (кол-во точек, 1 точка = 1 тик)
    public float alpha = 0.85f;        // прозрачность
    public float height = 0.9f;        // высота над ногами
    public float rainbowSpeed = 1.0f;  // скорость анимации цвета

    private void sanitize() {
        width = Math.max(0.05f, Math.min(1.5f, width));
        length = Math.max(5, Math.min(150, length));
        alpha = Math.max(0.1f, Math.min(1f, alpha));
        height = Math.max(0f, Math.min(2f, height));
        rainbowSpeed = Math.max(0.1f, Math.min(4f, rainbowSpeed));
        if (style == null) style = Style.RIBBON;
        if (colorMode == null) colorMode = ColorMode.GRADIENT;
        color1 &= 0xFFFFFF;
        color2 &= 0xFFFFFF;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    TrailConfig c = GSON.fromJson(r, TrailConfig.class);
                    if (c != null) {
                        c.sanitize();
                        I = c;
                    }
                }
            }
        } catch (Exception e) {
            NeonTrailsClient.LOGGER.error("Не удалось загрузить конфиг", e);
        }
    }

    public static void save() {
        try {
            I.sanitize();
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(I, w);
            }
        } catch (Exception e) {
            NeonTrailsClient.LOGGER.error("Не удалось сохранить конфиг", e);
        }
    }
}
