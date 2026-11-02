package com.neontrails;

import com.neontrails.gui.TrailScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeonTrailsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("NeonTrails");
    public static KeyBinding TOGGLE_KEY;
    /** Меню всегда открывается на Right Shift (читаем клавишу напрямую, не зависит от настроек управления). */
    public static final int MENU_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static boolean menuKeyWasDown = false;

    @Override
    public void onInitializeClient() {
        TrailConfig.load();

        TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.neontrails.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.neontrails"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean down = InputUtil.isKeyPressed(mc.getWindow().getHandle(), MENU_KEY);
            if (down && !menuKeyWasDown && mc.currentScreen == null && mc.player != null) {
                mc.setScreen(new TrailScreen());
            }
            menuKeyWasDown = down;

            while (TOGGLE_KEY.wasPressed()) {
                TrailConfig.I.enabled = !TrailConfig.I.enabled;
                TrailConfig.save();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("NeonTrails: " + (TrailConfig.I.enabled ? "ВКЛ" : "ВЫКЛ")), true);
                }
            }
            TrailManager.tick(mc);
        });

        WorldRenderEvents.LAST.register(TrailRenderer::render);
        LOGGER.info("NeonTrails загружен. Меню: Right Shift");
    }
}
