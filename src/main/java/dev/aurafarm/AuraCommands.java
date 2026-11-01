package dev.aurafarm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class AuraCommands {
    private AuraCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /aura и /aura add <число> (для тестов)
        dispatcher.register(Commands.literal("aura")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                int aura = AuraManager.get(player.getUUID());
                int jaw = AuraManager.getJaw(player.getUUID());
                String text = "Аура: " + aura + " [" + AuraManager.rank(aura) + "], челюсть: " + jaw
                    + " [" + AuraManager.jawRank(jaw) + "], позиция в таблице: " + AuraHypergamy.position(player) + "/10";
                ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                return aura;
            })
            .then(Commands.literal("add")
                .then(Commands.argument("amount", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        return AuraManager.add(player, amount, "команда");
                    }))));

        // /jaw и /jaw add <число> (для тестов): линия челюсти
        dispatcher.register(Commands.literal("jaw")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                int jaw = AuraManager.getJaw(player.getUUID());
                String text = "Линия челюсти: " + jaw + " [" + AuraManager.jawRank(jaw)
                    + "]. Растёт от бонсмэшинга (/bonesmash). True Adam с 75.";
                ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                return jaw;
            })
            .then(Commands.literal("add")
                .then(Commands.argument("amount", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        return AuraManager.addJaw(player.getUUID(), amount);
                    }))));

        // /lux показывает очки лука
        dispatcher.register(Commands.literal("lux")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String text = AuraLux.describe(player);
                ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                return AuraLux.score(player);
            }));

        // /bonesmash (и короткий вариант /bonsmash): бонсмэшинг топором или булавой в руке
        for (String name : new String[] {"bonesmash", "bonsmash"}) {
            dispatcher.register(Commands.literal(name)
                .executes(ctx -> {
                    ServerPlayer me = ctx.getSource().getPlayerOrException();
                    AuraBonesmash.smash(me, ctx.getSource().getServer().getTickCount());
                    return 1;
                }));
        }

        // /hyper (или /hypergamy): таблица и кто выше твоих ожиданий
        for (String name : new String[] {"hyper", "hypergamy"}) {
            dispatcher.register(Commands.literal(name)
                .executes(ctx -> {
                    ServerPlayer me = ctx.getSource().getPlayerOrException();
                    String text = AuraHypergamy.ranking(me, ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                    return 1;
                }));
        }

        // /mogg <игрок>
        dispatcher.register(Commands.literal("mogg")
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer me = ctx.getSource().getPlayerOrException();
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                    AuraMogg.mogg(me, target, ctx.getSource().getServer().getTickCount());
                    return 1;
                })));

        // /battle accept, /battle mob (ближайший моб), /battle <игрок или любая цель>
        dispatcher.register(Commands.literal("battle")
            .then(Commands.literal("accept")
                .executes(ctx -> {
                    ServerPlayer me = ctx.getSource().getPlayerOrException();
                    var server = ctx.getSource().getServer();
                    AuraBattles.accept(me, server, server.getTickCount());
                    return 1;
                }))
            .then(Commands.literal("mob")
                .executes(ctx -> {
                    ServerPlayer me = ctx.getSource().getPlayerOrException();
                    AuraBattles.startNearestMob(me, ctx.getSource().getServer().getTickCount());
                    return 1;
                }))
            .then(Commands.argument("target", EntityArgument.entity())
                .executes(ctx -> {
                    ServerPlayer me = ctx.getSource().getPlayerOrException();
                    Entity target = EntityArgument.getEntity(ctx, "target");
                    long now = ctx.getSource().getServer().getTickCount();
                    if (target instanceof ServerPlayer other) {
                        AuraBattles.challenge(me, other, now);
                    } else if (target instanceof LivingEntity mob) {
                        AuraBattles.startMob(me, mob, now);
                    } else {
                        me.sendSystemMessage(Component.literal("С этой целью батл невозможен."));
                    }
                    return 1;
                })));
    }
}
