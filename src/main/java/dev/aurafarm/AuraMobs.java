package dev.aurafarm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Аура мобов. У каждого моба она зависит от опасности его вида и хранится в памяти
 * (после перезапуска мира пересчитывается заново). Над головой моба показывается
 * его аура и ранг. Тут же считается награда за убийство: чем опаснее моб и слабее оружие,
 * тем больше ауры, и наоборот.
 */
public final class AuraMobs {
    /** Базовая аура по виду моба. Для остальных: враждебные 100, мирные 10. */
    private static final Map<EntityType<?>, Integer> BASE = Map.<EntityType<?>, Integer>ofEntries(
        Map.entry(EntityType.ENDER_DRAGON, 6000),
        Map.entry(EntityType.WARDEN, 4000),
        Map.entry(EntityType.WITHER, 3500),
        Map.entry(EntityType.ELDER_GUARDIAN, 1200),
        Map.entry(EntityType.RAVAGER, 900),
        Map.entry(EntityType.EVOKER, 600),
        Map.entry(EntityType.PIGLIN_BRUTE, 450),
        Map.entry(EntityType.WITHER_SKELETON, 450),
        Map.entry(EntityType.VINDICATOR, 400),
        Map.entry(EntityType.GHAST, 400),
        Map.entry(EntityType.ENDERMAN, 350),
        Map.entry(EntityType.BLAZE, 350),
        Map.entry(EntityType.BREEZE, 350),
        Map.entry(EntityType.WITCH, 300),
        Map.entry(EntityType.IRON_GOLEM, 300),
        Map.entry(EntityType.ZOGLIN, 300),
        Map.entry(EntityType.CREEPER, 250),
        Map.entry(EntityType.HOGLIN, 250),
        Map.entry(EntityType.SHULKER, 250),
        Map.entry(EntityType.VEX, 250),
        Map.entry(EntityType.GUARDIAN, 200),
        Map.entry(EntityType.PILLAGER, 200),
        Map.entry(EntityType.PHANTOM, 200),
        Map.entry(EntityType.STRAY, 160),
        Map.entry(EntityType.SKELETON, 150),
        Map.entry(EntityType.BOGGED, 150),
        Map.entry(EntityType.MAGMA_CUBE, 150),
        Map.entry(EntityType.POLAR_BEAR, 150),
        Map.entry(EntityType.ZOMBIFIED_PIGLIN, 150),
        Map.entry(EntityType.DROWNED, 130),
        Map.entry(EntityType.SPIDER, 120),
        Map.entry(EntityType.HUSK, 120),
        Map.entry(EntityType.PIGLIN, 120),
        Map.entry(EntityType.CAVE_SPIDER, 110),
        Map.entry(EntityType.ZOMBIE, 100),
        Map.entry(EntityType.ZOMBIE_VILLAGER, 100),
        Map.entry(EntityType.SLIME, 80),
        Map.entry(EntityType.SILVERFISH, 60),
        Map.entry(EntityType.ENDERMITE, 60),
        Map.entry(EntityType.WOLF, 60),
        Map.entry(EntityType.SNOW_GOLEM, 50)
    );

    private static final Map<UUID, Integer> MOB_AURA = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();

    private AuraMobs() {}

    private static int baseAura(LivingEntity entity) {
        Integer base = BASE.get(entity.getType());
        if (base != null) {
            return base;
        }
        return entity instanceof Enemy ? 100 : 10;
    }

    /** Аура любого существа: игрока, моба. У остальных 0. */
    public static int getAura(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return AuraManager.get(player.getUUID());
        }
        if (entity instanceof LivingEntity living) {
            return MOB_AURA.computeIfAbsent(living.getUUID(), id -> baseAura(living));
        }
        return 0;
    }

    /** Меняет ауру моба и возвращает новое значение. */
    public static int addAura(LivingEntity mob, int delta) {
        int value = getAura(mob) + delta;
        MOB_AURA.put(mob.getUUID(), value);
        return value;
    }

    public static void forget(UUID id) {
        MOB_AURA.remove(id);
        LAST_SEEN.remove(id);
    }

    // ----- награда за убийство -----

    /** Уровень опасности моба от 0 до 6 по его ауре. */
    private static int dangerTier(int aura) {
        if (aura < 50) return 0;
        if (aura < 120) return 1;
        if (aura < 250) return 2;
        if (aura < 450) return 3;
        if (aura < 1000) return 4;
        if (aura < 3000) return 5;
        return 6;
    }

    /** Сила оружия в руке от 0 (кулак или любой предмет) до 5 (нетерит, булава). */
    private static int weaponPower(ItemStack stack) {
        if (stack.is(Items.NETHERITE_SWORD) || stack.is(Items.NETHERITE_AXE) || stack.is(Items.MACE)) return 5;
        if (stack.is(Items.DIAMOND_SWORD) || stack.is(Items.DIAMOND_AXE) || stack.is(Items.TRIDENT)) return 4;
        if (stack.is(Items.IRON_SWORD) || stack.is(Items.IRON_AXE)
            || stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return 3;
        if (stack.is(Items.STONE_SWORD) || stack.is(Items.STONE_AXE)) return 2;
        if (stack.is(Items.WOODEN_SWORD) || stack.is(Items.WOODEN_AXE)
            || stack.is(Items.GOLDEN_SWORD) || stack.is(Items.GOLDEN_AXE)) return 1;
        return 0;
    }

    private static double multiplier(int handicap) {
        if (handicap <= -3) return 0.0;
        if (handicap == -2) return 0.25;
        if (handicap == -1) return 0.5;
        if (handicap == 0) return 1.0;
        if (handicap == 1) return 1.5;
        if (handicap == 2) return 2.0;
        if (handicap == 3) return 3.0;
        return 4.0;
    }

    /**
     * Сколько ауры получает убийца. База: десятая часть ауры моба. Множитель зависит от того,
     * насколько моб опаснее оружия: слабым оружием по сильному мобу х4, сильным по слабому 0.
     */
    public static int killReward(ServerPlayer killer, LivingEntity victim) {
        int danger = getAura(victim);
        if (danger < 20) {
            return 0;
        }
        int base = danger / 10;
        int handicap = dangerTier(danger) - weaponPower(killer.getMainHandItem());
        double mult = multiplier(handicap);
        int reward = (int) Math.round(base * mult);
        if (reward == 0 && mult > 0 && base > 0) {
            reward = 1;
        }
        return reward;
    }

    // ----- надпись над головой -----

    private static void label(Mob mob) {
        Component current = mob.getCustomName();
        // Имя от игрока (бирка) не трогаем
        if (current != null && !current.getString().contains("✦")) {
            return;
        }
        int aura = getAura(mob);
        Component name = mob.getType().getDescription().copy()
            .append(Component.literal(" ✦" + aura + " " + AuraManager.rank(aura)));
        if (current != null && current.getString().equals(name.getString())) {
            return;
        }
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }

    /** Раз в 2 секунды обновляет надписи у мобов в радиусе 48 блоков от игроков. */
    public static void tick(MinecraftServer server, long tick) {
        if (tick % 40 != 0) {
            return;
        }
        Set<UUID> done = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0))) {
                if (done.add(mob.getUUID())) {
                    label(mob);
                    LAST_SEEN.put(mob.getUUID(), tick);
                }
            }
        }

        // Раз в 5 минут забываем мобов, которых давно не видели
        if (tick % 6000 == 0) {
            Iterator<Map.Entry<UUID, Long>> iterator = LAST_SEEN.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                if (tick - entry.getValue() > 6000) {
                    MOB_AURA.remove(entry.getKey());
                    iterator.remove();
                }
            }
        }
    }
}
