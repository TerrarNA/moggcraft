package dev.aurafarm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Moggинг: сравнивается сила (аура + лукс * 3). Кто сильнее, тот моггнул.
 * Моггнутый игрок на 10 секунд получает визуальный дебафф (злые частицы над головой).
 */
public final class AuraMogg {
    private static final int COOLDOWN_TICKS = 600;
    private static final int MOGGED_TICKS = 200;
    private static final double MAX_DISTANCE = 20.0;
    private static final int MARGIN = 10;

    private static final Map<UUID, Long> MOGGED_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private AuraMogg() {}

    public static boolean isMogged(UUID id, long now) {
        Long until = MOGGED_UNTIL.get(id);
        return until != null && until > now;
    }

    public static int power(ServerPlayer player) {
        return AuraManager.get(player.getUUID()) + AuraLux.score(player) * 3 + AuraManager.getJaw(player.getUUID()) * 5;
    }

    public static void mogg(ServerPlayer me, ServerPlayer target, long now) {
        if (me.getUUID().equals(target.getUUID())) {
            me.sendSystemMessage(Component.literal("Себя моггнуть нельзя."));
            return;
        }
        Long cooldown = COOLDOWN_UNTIL.get(me.getUUID());
        if (cooldown != null && cooldown > now) {
            long seconds = (cooldown - now) / 20 + 1;
            me.sendSystemMessage(Component.literal("Моггинг перезаряжается: ещё " + seconds + " с."));
            return;
        }
        if (me.distanceTo(target) > MAX_DISTANCE) {
            me.sendSystemMessage(Component.literal("Слишком далеко, моггить можно в радиусе 20 блоков."));
            return;
        }

        COOLDOWN_UNTIL.put(me.getUUID(), now + COOLDOWN_TICKS);
        int mine = power(me);
        int theirs = power(target);
        String myName = me.getName().getString();
        String targetName = target.getName().getString();

        if (mine > theirs + MARGIN) {
            MOGGED_UNTIL.put(target.getUUID(), now + MOGGED_TICKS);
            AuraManager.add(me, 25, "моггнул " + targetName);
            AuraManager.add(target, -10, "тебя моггнул " + myName);
            me.sendSystemMessage(Component.literal("Ты моггнул " + targetName + "! (" + mine + " против " + theirs + ")"));
            target.sendSystemMessage(Component.literal(myName + " моггнул тебя. (" + theirs + " против " + mine + ")"));
            if (me.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    me.getX(), me.getY() + 1.0, me.getZ(), 20, 0.5, 0.9, 0.5, 0.3);
            }
        } else if (mine < theirs) {
            AuraManager.add(me, -15, "неудачный моггинг");
            me.sendSystemMessage(Component.literal(targetName + " сильнее тебя (" + theirs + " против " + mine + "). Кринж."));
        } else {
            me.sendSystemMessage(Component.literal("Вы примерно равны (" + mine + " и " + theirs + "), никто никого не моггнул."));
        }
    }
}
