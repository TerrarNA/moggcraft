package dev.aurafarm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

public class AuraMod implements ModInitializer {
    public static final String MOD_ID = "aurafarm";

    private static MinecraftServer server;

    /** Текущий сервер (в одиночной игре встроенный). null, если мир не запущен. */
    public static MinecraftServer server() {
        return server;
    }

    @Override
    public void onInitialize() {
        // Сохранение ауры
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
            AuraStorage.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> AuraStorage.save());
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

        // Удары идут в счёт аура-батла (игрок по игроку, игрок по мобу, моб по игроку)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (server == null) {
                return true;
            }
            long now = server.getTickCount();
            if (entity instanceof ServerPlayer victim) {
                if (source.getEntity() instanceof ServerPlayer attacker) {
                    AuraBattles.onHit(attacker, victim, now);
                } else if (source.getEntity() instanceof LivingEntity mob) {
                    AuraBattles.onMobStrike(mob, victim);
                }
            } else if (source.getEntity() instanceof ServerPlayer attacker) {
                AuraBattles.onMobHit(attacker, entity, now);
            }
            return true;
        });

        // События, меняющие ауру
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim) {
                // Смерть во время батла: игрок проиграл
                AuraBattles.onDeath(victim);

                // Кринж-смерти игрока
                if (source.is(DamageTypes.CACTUS)) {
                    AuraManager.add(victim, -50, "умер от кактуса");
                } else if (source.is(DamageTypes.LAVA)) {
                    AuraManager.add(victim, -30, "сгорел в лаве");
                } else if (source.is(DamageTypes.FALL)) {
                    AuraManager.add(victim, -20, "разбился при падении");
                }
                return;
            }

            // Моб умер: батл с ним закончен, а убийца получает ауру по опасности моба и силе оружия
            AuraBattles.onMobDeath(entity);
            if (source.getEntity() instanceof ServerPlayer killer) {
                int reward = AuraMobs.killReward(killer, entity);
                if (reward > 0) {
                    AuraManager.add(killer, reward, "убил " + entity.getType().getDescription().getString());
                }
            }
            AuraMobs.forget(entity.getUUID());
        });

        // Каждый тик сервера: батлы, гипергамия, поведение мобов, подписи, мьюинг, лукс, HUD и частицы
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            long tick = s.getTickCount();
            AuraBattles.tick(s, tick);
            AuraHypergamy.tick(s, tick);
            AuraPresence.tick(s, tick);
            AuraMobs.tick(s, tick);
            AuraNameplates.update(s, tick);

            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                AuraMewing.tick(player);

                // Раз в минуту: пассивная аура за лукс
                if (tick % 1200 == 0) {
                    int lux = AuraLux.score(player);
                    if (lux >= 20) {
                        AuraManager.add(player, lux / 10, "лукс");
                    }
                }

                // HUD обновляем 4 раза в секунду
                if (tick % 5 == 0) {
                    AuraHud.show(player, tick);
                }
                // Частицы раз в 2 тика, чтобы не грузить сервер
                if (tick % 2 == 0 && player.level() instanceof ServerLevel level) {
                    AuraEffects.spawn(player, level, AuraManager.get(player.getUUID()), tick);
                }
            }

            // Автосохранение раз в 5 минут
            if (tick % 6000 == 0) {
                AuraStorage.save();
            }
        });

        // Команды: /aura, /jaw, /lux, /bonesmash, /hyper, /mogg, /battle
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            AuraCommands.register(dispatcher));
    }
}
