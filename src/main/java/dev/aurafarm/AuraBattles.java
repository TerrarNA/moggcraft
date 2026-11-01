package dev.aurafarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Аура-батлы: дуэль на 60 секунд с игроком или с мобом.
 * Очки: +2 за удар, +8 за каждый третий удар подряд (комбо). Если соперник умер, победа сразу.
 *
 * Игрок против игрока: победитель +100 ауры, проигравший -50 и "был moggнут", ничья ничего не меняет.
 *
 * Игрок против моба (/battle mob или /battle <цель>): проигравший "был moggнут".
 * Если проиграл моб: слабее игрока на 2 ранга и больше, он умирает, иначе его аура падает.
 * Если проиграл игрок: сильнее на 2 ранга и больше моб убивает его, иначе падает его аура.
 */
public final class AuraBattles {
    private static final int DURATION_TICKS = 1200;
    private static final int CHALLENGE_TICKS = 1200;
    private static final int COMBO_WINDOW_TICKS = 60;
    private static final int WIN_REWARD = 100;
    private static final int LOSE_PENALTY = -50;

    private static final class Battle {
        final UUID a;
        final UUID b;
        final long endTick;
        int scoreA;
        int scoreB;
        int comboA;
        int comboB;
        long lastHitA = -10_000;
        long lastHitB = -10_000;

        Battle(UUID a, UUID b, long endTick) {
            this.a = a;
            this.b = b;
            this.endTick = endTick;
        }

        boolean has(UUID id) {
            return a.equals(id) || b.equals(id);
        }

        UUID other(UUID id) {
            return a.equals(id) ? b : a;
        }

        int score(UUID id) {
            return a.equals(id) ? scoreA : scoreB;
        }
    }

    private static final class MobBattle {
        final UUID player;
        final UUID mob;
        final long endTick;
        int scorePlayer;
        int scoreMob;
        int combo;
        long lastHit = -10_000;

        MobBattle(UUID player, UUID mob, long endTick) {
            this.player = player;
            this.mob = mob;
            this.endTick = endTick;
        }
    }

    private record Challenge(UUID from, long expireTick) {}

    private static final List<Battle> BATTLES = new ArrayList<>();
    private static final List<MobBattle> MOB_BATTLES = new ArrayList<>();
    private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();

    private AuraBattles() {}

    // ----- поиск -----

    private static Battle find(UUID id) {
        for (Battle battle : BATTLES) {
            if (battle.has(id)) {
                return battle;
            }
        }
        return null;
    }

    private static MobBattle findMobByPlayer(UUID playerId) {
        for (MobBattle battle : MOB_BATTLES) {
            if (battle.player.equals(playerId)) {
                return battle;
            }
        }
        return null;
    }

    private static MobBattle findMobByMob(UUID mobId) {
        for (MobBattle battle : MOB_BATTLES) {
            if (battle.mob.equals(mobId)) {
                return battle;
            }
        }
        return null;
    }

    private static boolean inAnyBattle(UUID playerId) {
        return find(playerId) != null || findMobByPlayer(playerId) != null;
    }

    private static String name(ServerPlayer player) {
        return player.getName().getString();
    }

    private static String mobName(LivingEntity mob) {
        return mob.getType().getDescription().getString();
    }

    // ----- игрок против игрока -----

    public static void challenge(ServerPlayer from, ServerPlayer to, long now) {
        if (from.getUUID().equals(to.getUUID())) {
            from.sendSystemMessage(Component.literal("Себя на батл вызвать нельзя."));
            return;
        }
        if (inAnyBattle(from.getUUID()) || inAnyBattle(to.getUUID())) {
            from.sendSystemMessage(Component.literal("Кто-то из вас уже в батле."));
            return;
        }
        CHALLENGES.put(to.getUUID(), new Challenge(from.getUUID(), now + CHALLENGE_TICKS));
        from.sendSystemMessage(Component.literal("Вызов отправлен игроку " + name(to) + ". У него 60 секунд, чтобы принять."));
        to.sendSystemMessage(Component.literal(name(from) + " вызывает тебя на аура-батл! Напиши /battle accept (60 секунд)."));
    }

    public static void accept(ServerPlayer acceptor, MinecraftServer server, long now) {
        Challenge challenge = CHALLENGES.get(acceptor.getUUID());
        if (challenge == null || challenge.expireTick() < now) {
            CHALLENGES.remove(acceptor.getUUID());
            acceptor.sendSystemMessage(Component.literal("Тебя никто не вызывал на батл."));
            return;
        }
        ServerPlayer challenger = server.getPlayerList().getPlayer(challenge.from());
        CHALLENGES.remove(acceptor.getUUID());
        if (challenger == null) {
            acceptor.sendSystemMessage(Component.literal("Тот, кто тебя вызвал, уже вышел."));
            return;
        }
        if (inAnyBattle(acceptor.getUUID()) || inAnyBattle(challenger.getUUID())) {
            acceptor.sendSystemMessage(Component.literal("Кто-то из вас уже в батле."));
            return;
        }
        BATTLES.add(new Battle(challenger.getUUID(), acceptor.getUUID(), now + DURATION_TICKS));
        Component start = Component.literal("АУРА-БАТЛ начался! 60 секунд. Бей, делай комбо (3 удара подряд), побеждает тот, у кого больше очков.");
        challenger.sendSystemMessage(start);
        acceptor.sendSystemMessage(start);
    }

    /** Вызывается при каждом уроне от игрока по игроку. */
    public static void onHit(ServerPlayer attacker, ServerPlayer victim, long now) {
        Battle battle = find(attacker.getUUID());
        if (battle == null || !battle.has(victim.getUUID())) {
            return;
        }
        boolean isA = battle.a.equals(attacker.getUUID());
        long lastHit = isA ? battle.lastHitA : battle.lastHitB;
        int combo = isA ? battle.comboA : battle.comboB;

        combo = (now - lastHit <= COMBO_WINDOW_TICKS) ? combo + 1 : 1;
        int points = 2;
        if (combo % 3 == 0) {
            points += 8;
            attacker.sendSystemMessage(Component.literal("КОМБО x" + combo + "! +8 очков"), true);
        }

        if (isA) {
            battle.scoreA += points;
            battle.comboA = combo;
            battle.lastHitA = now;
        } else {
            battle.scoreB += points;
            battle.comboB = combo;
            battle.lastHitB = now;
        }
    }

    private static void announceResult(ServerPlayer winner, ServerPlayer loser, String reason) {
        AuraManager.add(winner, WIN_REWARD, "победа в батле");
        AuraManager.add(loser, LOSE_PENALTY, "поражение в батле");
        Component message = Component.literal("АУРА-БАТЛ окончен: победил " + name(winner) + " (" + reason + ")! "
            + name(loser) + " был moggнут.");
        winner.sendSystemMessage(message);
        loser.sendSystemMessage(message);
    }

    // ----- игрок против моба -----

    public static void startMob(ServerPlayer player, LivingEntity mob, long now) {
        if (mob instanceof ServerPlayer) {
            player.sendSystemMessage(Component.literal("С игроками батл вызывается командой /battle <ник>."));
            return;
        }
        if (inAnyBattle(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Ты уже в батле."));
            return;
        }
        if (findMobByMob(mob.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("Этот моб уже в батле."));
            return;
        }
        MOB_BATTLES.add(new MobBattle(player.getUUID(), mob.getUUID(), now + DURATION_TICKS));
        if (mob instanceof Mob hostile) {
            hostile.setTarget(player);
        }
        player.sendSystemMessage(Component.literal("АУРА-БАТЛ с " + mobName(mob)
            + " начался! 60 секунд. Бей его, делай комбо (3 удара подряд). Проигравший будет moggнут."));
    }

    /** Батл с ближайшим мобом в радиусе 8 блоков. */
    public static void startNearestMob(ServerPlayer player, long now) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8.0))) {
            if (entity == player || entity instanceof ServerPlayer || entity.getType() == EntityType.ARMOR_STAND) {
                continue;
            }
            double distance = entity.distanceToSqr(player);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        if (best == null) {
            player.sendSystemMessage(Component.literal("Рядом (8 блоков) нет мобов для батла."));
            return;
        }
        startMob(player, best, now);
    }

    /** Игрок бьёт моба. */
    public static void onMobHit(ServerPlayer attacker, LivingEntity victim, long now) {
        MobBattle battle = findMobByPlayer(attacker.getUUID());
        if (battle == null || !battle.mob.equals(victim.getUUID())) {
            return;
        }
        battle.combo = (now - battle.lastHit <= COMBO_WINDOW_TICKS) ? battle.combo + 1 : 1;
        battle.lastHit = now;
        int points = 2;
        if (battle.combo % 3 == 0) {
            points += 8;
            attacker.sendSystemMessage(Component.literal("КОМБО x" + battle.combo + "! +8 очков"), true);
        }
        battle.scorePlayer += points;
    }

    /** Моб бьёт игрока. */
    public static void onMobStrike(LivingEntity attacker, ServerPlayer victim) {
        MobBattle battle = findMobByPlayer(victim.getUUID());
        if (battle != null && battle.mob.equals(attacker.getUUID())) {
            battle.scoreMob += 2;
        }
    }

    /** Моб умер во время батла: игрок побеждает, моб "был moggнут". */
    public static void onMobDeath(LivingEntity mob) {
        MobBattle battle = findMobByMob(mob.getUUID());
        MinecraftServer server = AuraMod.server();
        if (battle == null || server == null) {
            return;
        }
        MOB_BATTLES.remove(battle);
        ServerPlayer player = server.getPlayerList().getPlayer(battle.player);
        if (player == null) {
            return;
        }
        int mobAura = AuraMobs.getAura(mob);
        AuraManager.add(player, Math.max(20, mobAura / 8), "победа над " + mobName(mob));
        player.sendSystemMessage(Component.literal(mobName(mob) + " был moggнут и умер."));
    }

    private static void mobMogged(ServerPlayer player, LivingEntity mob, ServerLevel level) {
        int playerIndex = AuraManager.rankIndex(AuraManager.get(player.getUUID()));
        int mobAura = AuraMobs.getAura(mob);
        int mobIndex = AuraManager.rankIndex(mobAura);
        String mobName = mobName(mob);

        AuraManager.add(player, Math.max(20, mobAura / 8), "победа над " + mobName);
        if (playerIndex >= mobIndex + 2) {
            player.sendSystemMessage(Component.literal(mobName + " был moggнут и умер."));
            mob.hurtServer(level, level.damageSources().magic(), 10_000.0F);
        } else {
            int loss = Math.max(30, mobAura / 3);
            int now = AuraMobs.addAura(mob, -loss);
            player.sendSystemMessage(Component.literal(mobName + " был moggнут: аура упала на " + loss + " (теперь " + now + ")."));
        }
    }

    private static void playerMogged(ServerPlayer player, LivingEntity mob, ServerLevel level) {
        int playerIndex = AuraManager.rankIndex(AuraManager.get(player.getUUID()));
        int mobAura = AuraMobs.getAura(mob);
        int mobIndex = AuraManager.rankIndex(mobAura);
        String mobName = mobName(mob);

        AuraManager.add(player, -Math.max(30, mobAura / 10), "поражение от " + mobName);
        if (mobIndex >= playerIndex + 2) {
            player.sendSystemMessage(Component.literal(name(player) + " был moggнут " + mobName + " и умер."));
            player.hurtServer(level, level.damageSources().magic(), 10_000.0F);
        } else {
            player.sendSystemMessage(Component.literal(name(player) + " был moggнут " + mobName + ": аура упала."));
        }
    }

    private static void finishMob(ServerPlayer player, LivingEntity mob, MobBattle battle, ServerLevel level) {
        if (battle.scorePlayer > battle.scoreMob) {
            player.sendSystemMessage(Component.literal("АУРА-БАТЛ окончен " + battle.scorePlayer + ":" + battle.scoreMob + ". Ты победил!"));
            mobMogged(player, mob, level);
        } else if (battle.scoreMob > battle.scorePlayer) {
            player.sendSystemMessage(Component.literal("АУРА-БАТЛ окончен " + battle.scorePlayer + ":" + battle.scoreMob + ". Ты проиграл!"));
            playerMogged(player, mob, level);
        } else {
            player.sendSystemMessage(Component.literal("АУРА-БАТЛ окончен: ничья " + battle.scorePlayer + ":" + battle.scoreMob + "."));
        }
    }

    // ----- смерти и тики -----

    /** Если игрок умер во время батла, он проиграл. */
    public static void onDeath(ServerPlayer victim) {
        MinecraftServer server = AuraMod.server();
        if (server == null) {
            return;
        }

        MobBattle mobBattle = findMobByPlayer(victim.getUUID());
        if (mobBattle != null) {
            MOB_BATTLES.remove(mobBattle);
            AuraManager.add(victim, -50, "поражение в батле с мобом");
            victim.sendSystemMessage(Component.literal(name(victim) + " был moggнут."));
        }

        Battle battle = find(victim.getUUID());
        if (battle == null) {
            return;
        }
        BATTLES.remove(battle);
        ServerPlayer winner = server.getPlayerList().getPlayer(battle.other(victim.getUUID()));
        if (winner != null) {
            announceResult(winner, victim, "победа нокаутом");
        }
    }

    public static void tick(MinecraftServer server, long now) {
        CHALLENGES.values().removeIf(c -> c.expireTick() < now);

        Iterator<Battle> iterator = BATTLES.iterator();
        while (iterator.hasNext()) {
            Battle battle = iterator.next();
            ServerPlayer pa = server.getPlayerList().getPlayer(battle.a);
            ServerPlayer pb = server.getPlayerList().getPlayer(battle.b);
            if (pa == null || pb == null) {
                iterator.remove();
                ServerPlayer left = pa != null ? pa : pb;
                if (left != null) {
                    left.sendSystemMessage(Component.literal("Батл отменён: соперник вышел."));
                }
                continue;
            }
            if (now >= battle.endTick) {
                iterator.remove();
                if (battle.scoreA > battle.scoreB) {
                    announceResult(pa, pb, battle.scoreA + ":" + battle.scoreB);
                } else if (battle.scoreB > battle.scoreA) {
                    announceResult(pb, pa, battle.scoreB + ":" + battle.scoreA);
                } else {
                    Component draw = Component.literal("АУРА-БАТЛ окончен: ничья " + battle.scoreA + ":" + battle.scoreB + ".");
                    pa.sendSystemMessage(draw);
                    pb.sendSystemMessage(draw);
                }
            }
        }

        Iterator<MobBattle> mobIterator = MOB_BATTLES.iterator();
        while (mobIterator.hasNext()) {
            MobBattle battle = mobIterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(battle.player);
            if (player == null) {
                mobIterator.remove();
                continue;
            }
            LivingEntity mob = null;
            if (player.level() instanceof ServerLevel level && level.getEntity(battle.mob) instanceof LivingEntity found) {
                mob = found;
            }
            if (mob == null || !mob.isAlive()) {
                mobIterator.remove();
                player.sendSystemMessage(Component.literal("Соперник пропал, батл отменён."));
                continue;
            }
            if (now >= battle.endTick) {
                mobIterator.remove();
                if (player.level() instanceof ServerLevel level) {
                    finishMob(player, mob, battle, level);
                }
            } else if (now % 20 == 0 && mob instanceof Mob hostile) {
                hostile.setTarget(player);
            }
        }
    }

    /** Строка для HUD или null, если игрок не в батле. */
    public static String status(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        Battle battle = find(id);
        if (battle != null) {
            long seconds = Math.max(0, (battle.endTick - now) / 20);
            return "[БАТЛ] " + battle.score(id) + ":" + battle.score(battle.other(id)) + " (" + seconds + " с)";
        }
        MobBattle mobBattle = findMobByPlayer(id);
        if (mobBattle != null) {
            long seconds = Math.max(0, (mobBattle.endTick - now) / 20);
            return "[БАТЛ] " + mobBattle.scorePlayer + ":" + mobBattle.scoreMob + " (" + seconds + " с)";
        }
        return null;
    }
}
