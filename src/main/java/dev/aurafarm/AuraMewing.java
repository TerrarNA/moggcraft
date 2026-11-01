package dev.aurafarm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/** Мьюинг: стой на месте и приседай, каждые 5 секунд будет капать аура. */
public final class AuraMewing {
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Integer> TICKS = new HashMap<>();

    private AuraMewing() {}

    /** Вызывать каждый тик сервера для каждого игрока. */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        double x = player.getX();
        double z = player.getZ();
        double[] last = LAST_POS.get(id);
        boolean still = last != null && Math.abs(last[0] - x) < 0.01 && Math.abs(last[1] - z) < 0.01;
        LAST_POS.put(id, new double[] {x, z});

        if (player.isShiftKeyDown() && still) {
            int ticks = TICKS.merge(id, 1, Integer::sum);
            if (ticks % 100 == 0) {
                AuraManager.add(player, 2, "мьюинг");
            }
        } else {
            TICKS.remove(id);
        }
    }

    /** Сколько секунд игрок уже мьюит (0, если не мьюит). */
    public static int seconds(UUID id) {
        return TICKS.getOrDefault(id, 0) / 20;
    }
}
