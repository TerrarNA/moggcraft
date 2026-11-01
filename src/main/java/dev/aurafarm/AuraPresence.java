package dev.aurafarm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Влияние ауры игрока на окружающих:
 * - ранг ниже Sub 5 (аура -100 и меньше): даже мирные мобы нападают на игрока;
 * - Chadlite, Chad, True Adam (1500..7999): слабые мобы разбегаются;
 * - God-Tier, Alienmaxxing, The Ultimate Halo (8000+): все рядом с аурой ниже 700 (MTN включительно)
 *   получают урон, и игроки тоже.
 */
public final class AuraPresence {
    private AuraPresence() {}

    private static void hurt(LivingEntity target, ServerLevel level, DamageSource source, float amount) {
        target.hurtServer(level, source, amount);
    }

    public static void tick(MinecraftServer server, long tick) {
        if (tick % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            int aura = AuraManager.get(player.getUUID());
            int index = AuraManager.rankIndex(aura);

            if (aura <= -100) {
                cursed(player, level, tick);
            } else if (index >= 10 && index <= 12) {
                flee(player, level, aura);
            }
            if (index >= 13) {
                smite(player, level, index, tick);
            }
        }
    }

    /** Мирные мобы бегут к игроку с очень низкой аурой и кусают его. */
    private static void cursed(ServerPlayer player, ServerLevel level, long tick) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0))) {
            if (mob instanceof Enemy) {
                continue; // враждебные нападают и так
            }
            mob.getNavigation().moveTo(player, 1.3);
            if (tick % 20 == 0 && mob.distanceToSqr(player) < 4.0) {
                hurt(player, level, level.damageSources().mobAttack(mob), 1.0F);
            }
        }
    }

    /** Мобы со слабее аурой убегают от игрока. */
    private static void flee(ServerPlayer player, ServerLevel level, int playerAura) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(14.0))) {
            if (AuraMobs.getAura(mob) >= playerAura) {
                continue; // боссы и сильные мобы не боятся
            }
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            double dx = mob.getX() - player.getX();
            double dz = mob.getZ() - player.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.01) {
                dx = 1.0;
                dz = 0.0;
                length = 1.0;
            }
            mob.getNavigation().moveTo(
                mob.getX() + dx / length * 10.0, mob.getY(), mob.getZ() + dz / length * 10.0, 1.4);
        }
    }

    /** Раз в секунду бьёт всех рядом, у кого аура ниже 700 (MTN включительно). */
    private static void smite(ServerPlayer player, ServerLevel level, int rankIndex, long tick) {
        if (tick % 20 != 0) {
            return;
        }
        double radius = rankIndex == 13 ? 8.0 : rankIndex == 14 ? 10.0 : 12.0;
        float damage = rankIndex == 13 ? 2.0F : rankIndex == 14 ? 3.0F : 4.0F;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))) {
            if (target == player || target.getType() == EntityType.ARMOR_STAND) {
                continue;
            }
            if (target.distanceTo(player) > radius || AuraMobs.getAura(target) >= 700) {
                continue;
            }
            hurt(target, level, level.damageSources().magic(), damage);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
        }
    }
}
