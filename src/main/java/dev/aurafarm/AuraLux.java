package dev.aurafarm;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Луксмаксинг: очки лука считаются по надетым предметам.
 * Лук даёт пассивную ауру и усиливает moggинг.
 */
public final class AuraLux {
    private record Piece(EquipmentSlot slot, Item item, String name, int points) {}

    private static final List<Piece> PIECES = List.of(
        new Piece(EquipmentSlot.HEAD, Items.CARVED_PUMPKIN, "Маска", 10),
        new Piece(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, "Алмазный шлем", 20),
        new Piece(EquipmentSlot.HEAD, Items.GOLDEN_HELMET, "Корона", 25),
        new Piece(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "Нетеритовая корона", 30),
        new Piece(EquipmentSlot.CHEST, Items.CHAINMAIL_CHESTPLATE, "Цепь", 15),
        new Piece(EquipmentSlot.CHEST, Items.ELYTRA, "Плащ", 20),
        new Piece(EquipmentSlot.CHEST, Items.GOLDEN_CHESTPLATE, "Золотая куртка", 25),
        new Piece(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "Нетеритовая куртка", 30),
        new Piece(EquipmentSlot.LEGS, Items.GOLDEN_LEGGINGS, "Золотые штаны", 10),
        new Piece(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "Нетеритовые штаны", 15),
        new Piece(EquipmentSlot.FEET, Items.GOLDEN_BOOTS, "Золотые кроссы", 10),
        new Piece(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "Нетеритовые кроссы", 15)
    );

    private AuraLux() {}

    public static int score(ServerPlayer player) {
        int total = 0;
        for (Piece piece : PIECES) {
            if (player.getItemBySlot(piece.slot()).is(piece.item())) {
                total += piece.points();
            }
        }
        return total;
    }

    public static String describe(ServerPlayer player) {
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (Piece piece : PIECES) {
            if (player.getItemBySlot(piece.slot()).is(piece.item())) {
                sb.append("\n  + ").append(piece.name()).append(" (").append(piece.points()).append(")");
                total += piece.points();
            }
        }
        if (total == 0) {
            sb.append("\n  Пока ничего. Надень маску (тыква), цепь, корону или золотые шмотки.");
        }
        return "Твой лукс: " + total + sb;
    }
}
