package dev.aurafarm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Бонсмэшинг: берёшь в руку топор (или булаву) и бьёшь себя по лицу ради джавлайна.
 * Чем круче инструмент, тем больше награда и тем больнее провал. У булавы самая большая ставка.
 * Успех даёт ауру и повышает линию челюсти. Провал отнимает ауру, понижает челюсть,
 * наносит урон (но не убивает) и вызывает тошноту.
 * Иногда провал оказывается тяжёлой травмой: всё х2.
 */
public final class AuraBonesmash {
    private record Tool(Item item, String name, int chancePercent, int aura, int damage, int nauseaSeconds, int jaw) {}

    private static final List<Tool> TOOLS = List.of(
        new Tool(Items.WOODEN_AXE, "Деревянный топор", 50, 10, 2, 5, 1),
        new Tool(Items.STONE_AXE, "Каменный топор", 50, 20, 3, 6, 1),
        new Tool(Items.GOLDEN_AXE, "Золотой топор", 50, 30, 3, 6, 2),
        new Tool(Items.IRON_AXE, "Железный топор", 50, 40, 4, 8, 2),
        new Tool(Items.DIAMOND_AXE, "Алмазный топор", 50, 80, 5, 10, 3),
        new Tool(Items.NETHERITE_AXE, "Нетеритовый топор", 50, 150, 6, 12, 5),
        new Tool(Items.MACE, "Булава", 45, 300, 8, 15, 8)
    );

    private static final int COOLDOWN_TICKS = 300;
    private static final int CRITICAL_PERCENT = 15;

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private AuraBonesmash() {}

    private static Tool find(ItemStack stack) {
        for (Tool tool : TOOLS) {
            if (stack.is(tool.item())) {
                return tool;
            }
        }
        return null;
    }

    public static void smash(ServerPlayer player, long now) {
        Tool tool = find(player.getMainHandItem());
        if (tool == null) {
            player.sendSystemMessage(Component.literal(
                "Возьми в руку топор или булаву. Чем круче инструмент, тем больше ставка (булава самая жёсткая)."));
            return;
        }

        Long cooldown = COOLDOWN_UNTIL.get(player.getUUID());
        if (cooldown != null && cooldown > now) {
            long seconds = (cooldown - now) / 20 + 1;
            player.sendSystemMessage(Component.literal("Лицу нужно отдохнуть: ещё " + seconds + " с."));
            return;
        }
        COOLDOWN_UNTIL.put(player.getUUID(), now + COOLDOWN_TICKS);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean success = random.nextInt(100) < tool.chancePercent();

        if (success) {
            AuraManager.add(player, tool.aura(), "бонсмэшинг: " + tool.name());
            int jaw = AuraManager.addJaw(player.getUUID(), tool.jaw());
            player.sendSystemMessage(Component.literal(
                "Бонсмэшинг удался! Челюсть +" + tool.jaw() + " (уровень " + jaw + " [" + AuraManager.jawRank(jaw) + "])."));
            spawnParticles(player, true);
            return;
        }

        boolean critical = random.nextInt(100) < CRITICAL_PERCENT;
        int multiplier = critical ? 2 : 1;

        AuraManager.add(player, -tool.aura() * multiplier,
            critical ? "тяжёлая травма" : "провал бонсмэшинга");

        // Челюсть при провале падает вдвое меньше, чем растёт при успехе
        int jawLoss = Math.max(1, tool.jaw() / 2) * multiplier;
        int jaw = AuraManager.addJaw(player.getUUID(), -jawLoss);

        // Урон без смерти: оставляем минимум полсердца
        float newHealth = Math.max(1.0F, player.getHealth() - tool.damage() * multiplier);
        player.setHealth(newHealth);

        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, tool.nauseaSeconds() * multiplier * 20, 0));

        String jawText = " Челюсть -" + jawLoss + " (уровень " + jaw + " [" + AuraManager.jawRank(jaw) + "]).";
        if (critical) {
            player.sendSystemMessage(Component.literal("ТЯЖЁЛАЯ ТРАВМА! Ты промахнулся мимо кости. Штрафы удвоены." + jawText));
        } else {
            player.sendSystemMessage(Component.literal("Провал. Ты только сделал хуже, тебя тошнит." + jawText));
        }
        spawnParticles(player, false);
    }

    private static void spawnParticles(ServerPlayer player, boolean success) {
        if (player.level() instanceof ServerLevel level) {
            double x = player.getX();
            double y = player.getY() + 1.6;
            double z = player.getZ();
            if (success) {
                level.sendParticles(ParticleTypes.CRIT, x, y, z, 20, 0.3, 0.2, 0.3, 0.4);
            } else {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 8, 0.3, 0.2, 0.3, 0.1);
            }
        }
    }
}
