package dev.aurafarm;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Визуальные эффекты ауры: чем выше аура, тем круче частицы вокруг игрока.
 * Все эффекты чисто визуальные, баффов нет. Видят их все игроки рядом.
 */
public final class AuraEffects {
    private AuraEffects() {}

    public static void spawn(ServerPlayer player, ServerLevel level, int aura, long tick) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // Моггнутый игрок: злые частицы над головой, перебивают остальные эффекты
        if (AuraMogg.isMogged(player.getUUID(), tick)) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y + 2.2, z, 1, 0.3, 0.1, 0.3, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, x, y + 1.0, z, 2, 0.3, 0.6, 0.3, 0.01);
            return;
        }

        // Отрицательная аура (от Sub 3 до Giga-Subhuman): тёмный дымок над головой
        if (aura <= -100) {
            level.sendParticles(ParticleTypes.SMOKE, x, y + 2.1, z, 2, 0.2, 0.05, 0.2, 0.0);

            // Sub 2 (-200 и ниже): лёгкий пепел
            if (aura <= -200) {
                level.sendParticles(ParticleTypes.ASH, x, y + 1.0, z, 3, 0.5, 0.9, 0.5, 0.0);
            }

            // Sub 1 (-300 и ниже): больше пепла и тёмное облако над головой
            if (aura <= -300) {
                level.sendParticles(ParticleTypes.ASH, x, y + 1.0, z, 3, 0.5, 0.9, 0.5, 0.0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 2.0, z, 1, 0.3, 0.1, 0.3, 0.0);
            }

            // Sub 0 (-500 и ниже): густой дым у ног
            if (aura <= -500) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.2, z, 2, 0.4, 0.05, 0.4, 0.0);
            }

            // Sub <0 (-1000 и ниже): души тянутся вниз
            if (aura <= -1000) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1.0, z, 2, 0.4, 0.8, 0.4, 0.02);
            }

            // Giga-Subhuman (-2000 и ниже): вращающееся кольцо душ и дыма
            if (aura <= -2000) {
                ring(level, ParticleTypes.SOUL, x, y + 0.8, z, 1.0, 6, tick * 0.2);
                ring(level, ParticleTypes.SMOKE, x, y + 1.6, z, 0.8, 6, -tick * 0.2);
            }
            return;
        }

        // 100+: блёстки вокруг
        if (aura >= 100) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 1, 0.5, 0.8, 0.5, 0.01);
        }

        // 300+: огонь у ног
        if (aura >= 300) {
            level.sendParticles(ParticleTypes.FLAME, x, y + 0.1, z, 2, 0.3, 0.05, 0.3, 0.01);
        }

        // 700+: вращающееся кольцо огня вокруг игрока
        if (aura >= 700) {
            ring(level, ParticleTypes.FLAME, x, y + 0.2, z, 0.9, 4, tick * 0.25);
        }

        // 1500+: голубое огненное кольцо и искры, поднимающиеся вверх
        if (aura >= 1500) {
            ring(level, ParticleTypes.SOUL_FIRE_FLAME, x, y + 1.0, z, 0.7, 4, -tick * 0.3);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.0, z, 2, 0.4, 0.9, 0.4, 0.2);
        }

        // 3000+: полный режим бога: нимб и огненный столб
        if (aura >= 3000) {
            ring(level, ParticleTypes.END_ROD, x, y + 2.3, z, 0.4, 6, tick * 0.2);
            level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 6, 0.15, 1.0, 0.15, 0.05);
            if (tick % 40 == 0) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 12, 0.5, 0.9, 0.5, 0.3);
            }
        }

        // 5000+ (True Adam): тёмный портальный вихрь вокруг игрока
        if (aura >= 5000) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.0, z, 6, 0.5, 0.9, 0.5, 0.1);
        }

        // 8000+ (God-Tier): столб света над игроком
        if (aura >= 8000) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 3.0, z, 4, 0.1, 2.0, 0.1, 0.02);
        }

        // 12000+ (Alienmaxxing): зелёное кольцо звёзд и портальные искры
        if (aura >= 12000) {
            ring(level, ParticleTypes.HAPPY_VILLAGER, x, y + 1.2, z, 1.2, 6, -tick * 0.15);
            level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 8, 0.6, 1.0, 0.6, 0.5);
        }

        // 20000+ (The Ultimate Halo): огромный вращающийся нимб и золотые вспышки
        if (aura >= 20000) {
            ring(level, ParticleTypes.END_ROD, x, y + 2.8, z, 0.8, 12, tick * 0.1);
            ring(level, ParticleTypes.GLOW, x, y + 2.8, z, 0.6, 8, -tick * 0.1);
            if (tick % 20 == 0) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 20, 0.6, 1.0, 0.6, 0.4);
            }
        }

        // Мьюинг: мягкое свечение вокруг головы
        if (AuraMewing.seconds(player.getUUID()) >= 3) {
            level.sendParticles(ParticleTypes.ENCHANT, x, y + 1.8, z, 3, 0.3, 0.2, 0.3, 0.5);
        }
    }

    private static void ring(ServerLevel level, ParticleOptions type,
                             double cx, double cy, double cz, double radius, int points, double phase) {
        for (int i = 0; i < points; i++) {
            double angle = phase + i * (2 * Math.PI / points);
            level.sendParticles(type,
                cx + Math.cos(angle) * radius, cy, cz + Math.sin(angle) * radius,
                1, 0.0, 0.05, 0.0, 0.0);
        }
    }
}
