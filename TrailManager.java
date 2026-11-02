package com.neontrails;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Запоминает позиции игроков каждый тик. */
public final class TrailManager {
    private static final Map<UUID, Deque<Vec3d>> TRAILS = new HashMap<>();

    private TrailManager() {}

    public static Deque<Vec3d> get(UUID id) {
        return TRAILS.get(id);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            TRAILS.clear();
            return;
        }
        TrailConfig cfg = TrailConfig.I;
        Set<UUID> seen = new HashSet<>();

        for (PlayerEntity p : mc.world.getPlayers()) {
            boolean self = p == mc.player;
            if (self ? !cfg.showSelf : !cfg.showOthers) continue;
            if (p.isSpectator()) continue;

            seen.add(p.getUuid());
            Deque<Vec3d> d = TRAILS.computeIfAbsent(p.getUuid(), k -> new ArrayDeque<>());
            Vec3d pos = p.getPos().add(0, cfg.height, 0);

            // телепорт — сбрасываем след, чтобы не было длинной полосы через всю карту
            if (!d.isEmpty() && d.peekFirst().squaredDistanceTo(pos) > 64) d.clear();

            boolean moved = d.isEmpty() || d.peekFirst().squaredDistanceTo(pos) > 0.0004;
            if (moved) d.addFirst(pos);
            else if (!d.isEmpty()) d.pollLast(); // стоим на месте — след плавно "втягивается"

            while (d.size() > cfg.length) d.pollLast();
        }
        TRAILS.keySet().removeIf(id -> !seen.contains(id));
    }
}
