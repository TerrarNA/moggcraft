package dev.aurafarm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * HUD ауры. Рисуется через action bar (строка над хотбаром), поэтому работает
 * без клиентской части. Сообщение action bar всегда по центру экрана, поэтому
 * чтобы сдвинуть текст влево, справа дописываются пробелы. Размер шрифта из мода
 * на сервере не поменять, так что текст сделан компактным.
 */
public final class AuraHud {
    private static final String LEFT_SHIFT_PAD = " ".repeat(90);

    private AuraHud() {}

    public static void show(ServerPlayer player, long tick) {
        int aura = AuraManager.get(player.getUUID());

        ChatFormatting color;
        if (aura <= -300) color = ChatFormatting.DARK_RED;
        else if (aura < 0) color = ChatFormatting.DARK_GRAY;
        else if (aura < 100) color = ChatFormatting.GRAY;
        else if (aura < 300) color = ChatFormatting.AQUA;
        else if (aura < 700) color = ChatFormatting.GREEN;
        else if (aura < 1500) color = ChatFormatting.GOLD;
        else if (aura < 3000) color = ChatFormatting.LIGHT_PURPLE;
        else if (aura < 5000) color = ChatFormatting.RED;
        else if (aura < 8000) color = ChatFormatting.DARK_PURPLE;
        else if (aura < 12000) color = ChatFormatting.YELLOW;
        else if (aura < 20000) color = ChatFormatting.GREEN;
        else color = ChatFormatting.WHITE;

        MutableComponent text = Component.literal("✦" + aura + " " + AuraManager.rank(aura))
            .withStyle(color, ChatFormatting.BOLD);

        int jaw = AuraManager.getJaw(player.getUUID());
        text.append(Component.literal("  Чел " + jaw + " " + AuraManager.jawRank(jaw)).withStyle(ChatFormatting.AQUA));

        int lux = AuraLux.score(player);
        if (lux > 0) {
            text.append(Component.literal("  Лукс " + lux).withStyle(ChatFormatting.YELLOW));
        }

        int mew = AuraMewing.seconds(player.getUUID());
        if (mew >= 1) {
            text.append(Component.literal("  Мью " + mew + "с").withStyle(ChatFormatting.AQUA));
        }

        if (AuraMogg.isMogged(player.getUUID(), tick)) {
            text.append(Component.literal("  МОГГНУТ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }

        String battle = AuraBattles.status(player, tick);
        if (battle != null) {
            text.append(Component.literal("  " + battle).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }

        AuraManager.Flash flash = AuraManager.getFlash(player.getUUID());
        if (flash != null) {
            String sign = flash.delta() > 0 ? "+" : "";
            ChatFormatting deltaColor = flash.delta() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
            text.append(Component.literal("  " + sign + flash.delta()).withStyle(deltaColor));
        }

        // Пробелы справа сдвигают видимый текст влево
        text.append(Component.literal(LEFT_SHIFT_PAD));

        player.sendSystemMessage(text, true);
    }
}
