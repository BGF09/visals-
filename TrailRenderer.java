package com.neontrails;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Рисует следы в мире. */
public final class TrailRenderer {
    private TrailRenderer() {}

    public static void render(WorldRenderContext ctx) {
        TrailConfig cfg = TrailConfig.I;
        if (!cfg.enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        float td = ctx.tickCounter().getTickDelta(true);
        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
        Quaternionf rot = camera.getRotation();
        float time = TrailColors.time();

        List<List<Vec3d>> trails = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            boolean self = p == mc.player;
            if (self ? !cfg.showSelf : !cfg.showOthers) continue;
            if (p.isSpectator()) continue;

            Deque<Vec3d> d = TrailManager.get(p.getUuid());
            if (d == null || d.isEmpty()) continue;

            List<Vec3d> pts = new ArrayList<>(d.size() + 1);
            addPoint(pts, p.getLerpedPos(td).add(0, cfg.height, 0).subtract(cam));
            for (Vec3d v : d) addPoint(pts, v.subtract(cam));

            // в первом лице не рисуем кусок, который торчит прямо перед камерой
            if (self && mc.options.getPerspective().isFirstPerson()) {
                while (pts.size() > 2 && pts.get(0).lengthSquared() < 2.25) pts.remove(0);
            }
            if (pts.size() >= 2) trails.add(pts);
        }
        if (trails.isEmpty()) return;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (cfg.throughWalls) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();

        for (List<Vec3d> pts : trails) {
            switch (cfg.style) {
                case RIBBON -> {
                    if (cfg.glow) ribbon(pts, mat, cfg, time, 2.4f, 0.22f, true);
                    ribbon(pts, mat, cfg, time, 1f, 1f, false);
                }
                case HELIX -> {
                    int strands = 2;
                    for (int s = 0; s < strands; s++) {
                        List<Vec3d> strand = helixStrand(pts, cfg, time, s, strands);
                        if (cfg.glow) ribbon(strand, mat, cfg, time, 0.9f, 0.22f, true);
                        ribbon(strand, mat, cfg, time, 0.35f, 1f, false);
                    }
                }
                case DOTS -> {
                    Vector3f right = rot.transform(new Vector3f(1, 0, 0));
                    Vector3f up = rot.transform(new Vector3f(0, 1, 0));
                    if (cfg.glow) dots(pts, mat, cfg, time, right, up, 2.6f, 0.2f, true);
                    dots(pts, mat, cfg, time, right, up, 1f, 1f, false);
                }
            }
        }

        // возвращаем состояние рендера как было
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addPoint(List<Vec3d> list, Vec3d v) {
        if (list.isEmpty() || list.get(list.size() - 1).squaredDistanceTo(v) > 1e-6) list.add(v);
    }

    private static void setBlend(boolean additive) {
        if (additive) RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        else RenderSystem.defaultBlendFunc();
    }

    private static Vec3d tangent(List<Vec3d> pts, int i) {
        int n = pts.size();
        Vec3d t = pts.get(Math.max(i - 1, 0)).subtract(pts.get(Math.min(i + 1, n - 1)));
        return t.lengthSquared() < 1e-9 ? new Vec3d(0, 0, 1) : t;
    }

    private static Vec3d sideVec(Vec3d tan, Vec3d p) {
        Vec3d s = tan.crossProduct(p);
        if (s.lengthSquared() < 1e-10) s = new Vec3d(0, 1, 0).crossProduct(tan);
        if (s.lengthSquared() < 1e-10) return new Vec3d(1, 0, 0);
        return s.normalize();
    }

    /** Лента, всегда повёрнутая лицом к камере. */
    private static void ribbon(List<Vec3d> pts, Matrix4f mat, TrailConfig cfg, float time,
                               float wMul, float aMul, boolean additive) {
        int n = pts.size();
        if (n < 2) return;
        setBlend(additive);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n; i++) {
            Vec3d p = pts.get(i);
            Vec3d side = sideVec(tangent(pts, i), p);

            float t = i / (float) (n - 1);
            float fade = (float) Math.pow(1f - t, 0.8);
            double hw = cfg.width * 0.5 * wMul * (cfg.taper ? Math.pow(1f - t, 0.7) : 1.0);
            float alpha = cfg.alpha * aMul * fade;

            int col = TrailColors.colorAt(cfg, t, time);
            float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, bl = (col & 255) / 255f;

            b.vertex(mat, (float) (p.x + side.x * hw), (float) (p.y + side.y * hw), (float) (p.z + side.z * hw)).color(r, g, bl, alpha);
            b.vertex(mat, (float) (p.x - side.x * hw), (float) (p.y - side.y * hw), (float) (p.z - side.z * hw)).color(r, g, bl, alpha);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    /** Смещает точки по спирали вокруг оси следа. */
    private static List<Vec3d> helixStrand(List<Vec3d> pts, TrailConfig cfg, float time, int strand, int strands) {
        int n = pts.size();
        List<Vec3d> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec3d p = pts.get(i);
            Vec3d tan = tangent(pts, i);
            Vec3d side = sideVec(tan, p);
            Vec3d up = tan.crossProduct(side);
            up = up.lengthSquared() < 1e-10 ? new Vec3d(0, 1, 0) : up.normalize();

            float t = i / (float) (n - 1);
            double phase = i * 0.55 - time * 4.0 + strand * Math.PI * 2.0 / strands;
            double radius = cfg.width * 0.8 * (cfg.taper ? 1.0 - t * 0.6 : 1.0);
            out.add(p.add(side.multiply(Math.cos(phase) * radius)).add(up.multiply(Math.sin(phase) * radius)));
        }
        return out;
    }

    /** Мерцающие квадратики-искры вдоль следа. */
    private static void dots(List<Vec3d> pts, Matrix4f mat, TrailConfig cfg, float time,
                             Vector3f right, Vector3f up, float sizeMul, float aMul, boolean additive) {
        int n = pts.size();
        if (n < 2) return;
        setBlend(additive);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n; i++) {
            Vec3d p = pts.get(i);
            float t = i / (float) (n - 1);
            float fade = (float) Math.pow(1f - t, 0.8);

            // детерминированный "шум" для каждой точки
            double j1 = Math.sin(i * 12.9898) * 0.5, j2 = Math.sin(i * 78.233) * 0.5, j3 = Math.sin(i * 37.719) * 0.5;
            double jit = cfg.width * 0.35;
            double cx = p.x + j1 * jit, cy = p.y + j2 * jit, cz = p.z + j3 * jit;

            float twinkle = 0.6f + 0.4f * (float) Math.sin(time * 8f + i * 1.7f);
            float s = cfg.width * 0.35f * sizeMul * twinkle * (cfg.taper ? 0.4f + 0.6f * fade : 1f);
            float alpha = cfg.alpha * aMul * fade;

            int col = TrailColors.colorAt(cfg, t, time);
            float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, bl = (col & 255) / 255f;

            float rx = right.x * s, ry = right.y * s, rz = right.z * s;
            float ux = up.x * s, uy = up.y * s, uz = up.z * s;
            float x = (float) cx, y = (float) cy, z = (float) cz;

            b.vertex(mat, x - rx - ux, y - ry - uy, z - rz - uz).color(r, g, bl, alpha);
            b.vertex(mat, x + rx - ux, y + ry - uy, z + rz - uz).color(r, g, bl, alpha);
            b.vertex(mat, x + rx + ux, y + ry + uy, z + rz + uz).color(r, g, bl, alpha);
            b.vertex(mat, x - rx + ux, y - ry + uy, z - rz + uz).color(r, g, bl, alpha);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
    }
}
