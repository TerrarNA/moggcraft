package dev.aurafarm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Гипергамия. У каждого игрока и моба есть положение в таблице от 1 до 10:
 * среднее между рангом ауры и рангом линии челюсти (у мобов только аура).
 * 1-6: малое или нулевое внимание, 7-10: полное внимание.
 * Те, у кого положение 7-10, притягивают взгляды: все, у кого положение ниже 7,
 * в радиусе 20 блоков плачут (частицы слёз). Раз в 10 секунд топ-3 игроков
 * с положением 7+ получают +1 ауры за каждого такого "плачущего" рядом, максимум +3.
 */
public final class AuraHypergamy {
    private static final double RANGE = 20.0;
    private static final int FULL_ATTENTION = 7;
    private static final int TOP_N = 3;
    private static final int MAX_BONUS = 3;

    private AuraHypergamy() {}

    public static int position(Entity entity) {
        int auraIndex = AuraManager.rankIndex(AuraMobs.getAura(entity));
        int jawIndex = entity instanceof ServerPlayer player
            ? AuraManager.jawRankIndex(AuraManager.getJaw(player.getUUID()))
            : auraIndex;
        return AuraManager.position(auraIndex, jawIndex);
    }

    private static boolean isTarget(LivingEntity entity) {
        return entity.getType() != EntityType.ARMOR_STAND && entity.isAlive();
    }

    public static void tick(MinecraftServer server, long now) {
        boolean tears = now % 10 == 0;
        boolean bonus = now % 200 == 0;
        if (!tears && !bonus) {
            return;
        }

        List<ServerPlayer> stars = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (position(player) >= FULL_ATTENTION) {
                stars.add(player);
            }
        }
        stars.sort(Comparator
            .comparingInt((ServerPlayer p) -> position(p))
            .thenComparingInt(p -> AuraManager.get(p.getUUID()))
            .reversed());

        int index = 0;
        for (ServerPlayer star : stars) {
            if (!(star.level() instanceof ServerLevel level)) {
                continue;
            }
            int admirers = 0;
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, star.getBoundingBox().inflate(RANGE))) {
                if (other == star || !isTarget(other) || other.distanceTo(star) > RANGE) {
                    continue;
                }
                if (position(other) >= FULL_ATTENTION) {
                    continue; // те, кто на том же уровне, не плачут
                }
                admirers++;
                if (tears) {
                    cry(level, other);
                }
            }

            if (bonus && index < TOP_N && admirers > 0) {
                AuraManager.add(star, Math.min(admirers, MAX_BONUS), "внимание (" + admirers + ")");
            }
            index++;
        }
    }

    /** Слёзы из глаз: капли воды по обе стороны головы. */
    private static void cry(ServerLevel level, LivingEntity entity) {
        double y = entity.getY() + entity.getEyeHeight() - 0.1;
        level.sendParticles(ParticleTypes.FALLING_WATER, entity.getX(), y, entity.getZ(), 3, 0.2, 0.0, 0.2, 0.0);
    }

    /** Текст для команды /hyper: рейтинг онлайн и кто выше твоих ожиданий. */
    public static String ranking(ServerPlayer viewer, MinecraftServer server) {
        int myPosition = position(viewer);
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.sort(Comparator
            .comparingInt((ServerPlayer p) -> position(p))
            .thenComparingInt(p -> AuraManager.get(p.getUUID()))
            .reversed());

        StringBuilder sb = new StringBuilder("Гипергамия: таблица игроков онлайн");
        int place = 1;
        for (ServerPlayer player : players) {
            int position = position(player);
            int aura = AuraManager.get(player.getUUID());
            int jaw = AuraManager.getJaw(player.getUUID());
            String label;
            if (player == viewer) {
                label = "это ты";
            } else if (position > myPosition) {
                label = "выше ожиданий (улов)";
            } else if (position == myPosition) {
                label = "ровня";
            } else {
                label = "ниже ожиданий (сеттл)";
            }
            sb.append("\n#").append(place++).append(" ")
                .append(player.getName().getString())
                .append(": позиция ").append(position).append("/10 (")
                .append(position >= FULL_ATTENTION ? "полное внимание" : "малое внимание")
                .append("), аура ").append(aura).append(" [").append(AuraManager.rank(aura)).append("]")
                .append(", челюсть ").append(jaw).append(" [").append(AuraManager.jawRank(jaw)).append("], ")
                .append(label);
        }
        sb.append("\nПозиция 7-10: рядом все, кто ниже, плачут. Внимание (+аура) получает только топ-3.");
        return sb.toString();
    }
}
