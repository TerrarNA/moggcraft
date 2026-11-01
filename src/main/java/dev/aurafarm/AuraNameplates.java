package dev.aurafarm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Аура и ранг над головой у игроков. Показываются как суффикс ника через команду
 * (team) из scoreboard: у каждого игрока своя команда aura_xxxxxxxx.
 * Свою надпись игрок не видит, зато её видят остальные.
 */
public final class AuraNameplates {
    private static final Map<UUID, String> LAST = new HashMap<>();

    private AuraNameplates() {}

    public static void update(MinecraftServer server, long tick) {
        if (tick % 20 != 0) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int aura = AuraManager.get(player.getUUID());
            String text = " ✦" + aura + " " + AuraManager.rank(aura);
            if (text.equals(LAST.get(player.getUUID()))) {
                continue;
            }

            String teamName = "aura_" + player.getUUID().toString().substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (current != null && !current.getName().equals(teamName)) {
                continue; // игрок уже в чужой команде, не трогаем
            }
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
            }
            team.setPlayerSuffix(Component.literal(text).withStyle(ChatFormatting.GOLD));
            if (current == null) {
                scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            }
            LAST.put(player.getUUID(), text);
        }
    }
}
