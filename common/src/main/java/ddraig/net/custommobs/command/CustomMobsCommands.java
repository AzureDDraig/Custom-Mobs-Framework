package ddraig.net.custommobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import ddraig.net.custommobs.data.MobRegistry;
import ddraig.net.custommobs.data.RaidSystem;
import ddraig.net.custommobs.entity.CustomMobEntity;
import ddraig.net.custommobs.network.ModPackets;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomMobsCommands {
    private static final SuggestionProvider<CommandSourceStack> MOB_SUGGESTIONS = (context, builder) -> 
            SharedSuggestionProvider.suggest(MobRegistry.loadedMobs.keySet().stream().filter(id -> !id.startsWith("__proj_preview_")).toList(), builder);

    private static final SuggestionProvider<CommandSourceStack> REWARD_TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("command", "item", "hand"), builder);

    private static final SuggestionProvider<CommandSourceStack> PROJECTILE_SUGGESTIONS = (context, builder) -> {
        java.util.List<String> list = new java.util.ArrayList<>(MobRegistry.loadedProjectiles.keySet());
        list.addAll(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(net.minecraft.resources.ResourceLocation::toString).toList());
        return SharedSuggestionProvider.suggest(list, builder);
    };

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, selection) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("custom_mobs")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("creator-ui")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ModPackets.openCreatorUi(player);
                            return 1;
                        })
                )
                .then(Commands.literal("reload-config")
                        .executes(context -> {
                            ddraig.net.custommobs.data.ModConfig.reload();
                            ddraig.net.custommobs.data.RaidSystem.loadRaids();
                            ModPackets.syncTemplatesToAll(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.reloaded"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("projectile-creator-ui")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ModPackets.openProjectileCreatorUi(player);
                            return 1;
                        })
                )
                .then(Commands.literal("spawn-projectile")
                        .then(Commands.argument("projectile_id", StringArgumentType.string())
                                .suggests(PROJECTILE_SUGGESTIONS)
                                .executes(context -> spawnProjectile(context.getSource(), StringArgumentType.getString(context, "projectile_id")))
                        )
                )
                .then(Commands.literal("status")
                        .executes(context -> {
                            int count = 0;
                            Map<String, Integer> counts = new HashMap<>();
                            for (var entity : context.getSource().getLevel().getAllEntities()) {
                                if (entity instanceof CustomMobEntity mob) {
                                    count++;
                                    counts.put(mob.getTemplateId(), counts.getOrDefault(mob.getTemplateId(), 0) + 1);
                                }
                            }
                            int finalCount = count;
                            context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.status", finalCount, counts.toString()), false);
                            return 1;
                        })
                )
                .then(Commands.literal("killall")
                        .executes(context -> {
                            int count = 0;
                            for (var entity : context.getSource().getLevel().getAllEntities()) {
                                if (entity instanceof CustomMobEntity mob) {
                                    mob.discard();
                                    count++;
                                }
                            }
                            int finalCount = count;
                            context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.killall", finalCount), false);
                            return 1;
                        })
                )
                .then(Commands.literal("spawn")
                        .then(Commands.argument("mob_id", StringArgumentType.string())
                                .suggests(MOB_SUGGESTIONS)
                                .executes(context -> spawnMob(context.getSource(), StringArgumentType.getString(context, "mob_id"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                        .executes(context -> spawnMob(context.getSource(), StringArgumentType.getString(context, "mob_id"), IntegerArgumentType.getInteger(context, "count")))
                                )
                        )
                )
                .then(Commands.literal("spawn-mob")
                        .then(Commands.argument("mob_id", StringArgumentType.string())
                                .suggests(MOB_SUGGESTIONS)
                                .executes(context -> spawnMobCustom(context.getSource(), StringArgumentType.getString(context, "mob_id"), false, context.getSource().getPosition()))
                                .then(Commands.argument("is_elite", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(context -> spawnMobCustom(context.getSource(), StringArgumentType.getString(context, "mob_id"), com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "is_elite"), context.getSource().getPosition()))
                                )
                                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                        .executes(context -> spawnMobCustom(context.getSource(), StringArgumentType.getString(context, "mob_id"), false, net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context, "pos")))
                                        .then(Commands.argument("is_elite", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> spawnMobCustom(context.getSource(), StringArgumentType.getString(context, "mob_id"), com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "is_elite"), net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context, "pos")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("bestiary")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .then(Commands.argument("mob_id", StringArgumentType.string())
                                                .suggests(MOB_SUGGESTIONS)
                                                .executes(context -> addBestiaryDiscovery(
                                                        context.getSource(),
                                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "mob_id")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .then(Commands.argument("mob_id", StringArgumentType.string())
                                                .suggests(MOB_SUGGESTIONS)
                                                .executes(context -> removeBestiaryDiscovery(
                                                        context.getSource(),
                                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "mob_id")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("add-raid-reward")
                        .then(Commands.argument("raid_id", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(RaidSystem.getRaids().stream().map(r -> r.raidId.contains(" ") ? "\"" + r.raidId + "\"" : r.raidId).toList(), builder))
                                .then(Commands.literal("hand")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String raidId = StringArgumentType.getString(context, "raid_id");
                                            net.minecraft.world.item.ItemStack handStack = player.getMainHandItem();
                                            if (handStack.isEmpty()) {
                                                context.getSource().sendFailure(Component.literal("Your main hand is empty!"));
                                                return 0;
                                            }
                                            net.minecraft.nbt.CompoundTag compound = handStack.save(new net.minecraft.nbt.CompoundTag());
                                            String rewardString = "nbt: " + compound.toString();
                                            RaidSystem.addRaidReward(raidId, rewardString);
                                            context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.add_reward", raidId, handStack.getHoverName().getString() + " x" + handStack.getCount()), true);
                                            return 1;
                                        })
                                        .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    String raidId = StringArgumentType.getString(context, "raid_id");
                                                    int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count");
                                                    net.minecraft.world.item.ItemStack handStack = player.getMainHandItem().copy();
                                                    if (handStack.isEmpty()) {
                                                        context.getSource().sendFailure(Component.literal("Your main hand is empty!"));
                                                        return 0;
                                                    }
                                                    handStack.setCount(count);
                                                    net.minecraft.nbt.CompoundTag compound = handStack.save(new net.minecraft.nbt.CompoundTag());
                                                    String rewardString = "nbt: " + compound.toString();
                                                    RaidSystem.addRaidReward(raidId, rewardString);
                                                    context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.add_reward", raidId, handStack.getHoverName().getString() + " x" + count), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.argument("reward_type", StringArgumentType.string())
                                        .suggests(REWARD_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("value", StringArgumentType.string())
                                                .executes(context -> {
                                                    String raidId = StringArgumentType.getString(context, "raid_id");
                                                    String type = StringArgumentType.getString(context, "reward_type");
                                                    String value = StringArgumentType.getString(context, "value");
                                                    
                                                    String rewardString = type.equalsIgnoreCase("command") ? "/" + value : value;
                                                    RaidSystem.addRaidReward(raidId, rewardString);
                                                    context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.add_reward", raidId, rewardString), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("delete-raid")
                        .then(Commands.argument("raid_id", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(RaidSystem.getRaids().stream().map(r -> r.raidId.contains(" ") ? "\"" + r.raidId + "\"" : r.raidId).toList(), builder))
                                .executes(context -> {
                                    String raidId = StringArgumentType.getString(context, "raid_id");
                                    if (RaidSystem.deleteRaid(raidId)) {
                                        context.getSource().sendSuccess(() -> Component.literal("Successfully deleted raid: " + raidId), true);
                                        return 1;
                                    } else {
                                        context.getSource().sendFailure(Component.literal("Raid not found: " + raidId));
                                        return 0;
                                    }
                                })
                        )
                )
                .then(Commands.literal("raid")
                        .then(Commands.literal("start")
                                .then(Commands.argument("raid_id", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(RaidSystem.getRaids().stream().map(r -> r.raidId.contains(" ") ? "\"" + r.raidId + "\"" : r.raidId).toList(), builder))
                                        .executes(context -> {
                                            String raidId = StringArgumentType.getString(context, "raid_id");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ServerLevel level = player.serverLevel();
                                            BlockPos pos = player.blockPosition();

                                            // Search for a RaidBlockEntity within 64 blocks
                                            ddraig.net.custommobs.block.entity.RaidBlockEntity closestBe = null;
                                            double closestDist = Double.MAX_VALUE;

                                            for (ddraig.net.custommobs.block.entity.RaidBlockEntity rbe : RaidSystem.getActiveBlockEntities()) {
                                                if (rbe.getRaidId().equalsIgnoreCase(raidId) || rbe.getRaidId().replace(' ', '_').equalsIgnoreCase(raidId)) {
                                                    if (rbe.getLevel() == level) {
                                                        double d = rbe.getBlockPos().distSqr(pos);
                                                        if (d < closestDist && d <= 4096.0D) { // 64 blocks range squared
                                                            closestDist = d;
                                                            closestBe = rbe;
                                                        }
                                                    }
                                                }
                                            }

                                            final ddraig.net.custommobs.block.entity.RaidBlockEntity finalClosestBe = closestBe;
                                            if (finalClosestBe != null) {
                                                finalClosestBe.triggerRaidByPlayer(player);
                                                context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.start_block", raidId, finalClosestBe.getBlockPos().toShortString()), true);
                                                return 1;
                                            } else {
                                                // Start global command raid!
                                                RaidSystem.startRaid(context.getSource().getServer(), raidId, level, pos);
                                                context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.start_global", raidId), true);
                                                return 1;
                                            }
                                        })
                                )
                        )
                        .then(Commands.literal("force-start")
                                .then(Commands.argument("raid_id", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(RaidSystem.getRaids().stream().map(r -> r.raidId.contains(" ") ? "\"" + r.raidId + "\"" : r.raidId).toList(), builder))
                                        .executes(context -> {
                                            String raidId = StringArgumentType.getString(context, "raid_id");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ServerLevel level = player.serverLevel();
                                            BlockPos pos = player.blockPosition();

                                            ddraig.net.custommobs.block.entity.RaidBlockEntity closestBe = null;
                                            double closestDist = Double.MAX_VALUE;

                                            for (ddraig.net.custommobs.block.entity.RaidBlockEntity rbe : RaidSystem.getActiveBlockEntities()) {
                                                if (rbe.getRaidId().equalsIgnoreCase(raidId) || rbe.getRaidId().replace(' ', '_').equalsIgnoreCase(raidId)) {
                                                    if (rbe.getLevel() == level) {
                                                        double d = rbe.getBlockPos().distSqr(pos);
                                                        if (d < closestDist && d <= 4096.0D) {
                                                            closestDist = d;
                                                            closestBe = rbe;
                                                        }
                                                    }
                                                }
                                            }

                                            final ddraig.net.custommobs.block.entity.RaidBlockEntity finalClosestBe = closestBe;
                                            if (finalClosestBe != null) {
                                                finalClosestBe.triggerRaidByPlayer(player, true);
                                                context.getSource().sendSuccess(() -> Component.literal("Force started raid block '" + raidId + "' at " + finalClosestBe.getBlockPos().toShortString()), true);
                                                return 1;
                                            } else {
                                                RaidSystem.startRaid(context.getSource().getServer(), raidId, level, pos);
                                                context.getSource().sendSuccess(() -> Component.literal("Started global raid '" + raidId + "'"), true);
                                                return 1;
                                            }
                                        })
                                )
                        )
                        .then(Commands.literal("stop")
                                .then(Commands.argument("raid_id", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(RaidSystem.getRaids().stream().map(r -> r.raidId.contains(" ") ? "\"" + r.raidId + "\"" : r.raidId).toList(), builder))
                                        .executes(context -> {
                                            String raidId = StringArgumentType.getString(context, "raid_id");
                                            boolean stopped = RaidSystem.stopRaid(context.getSource().getServer(), raidId);

                                            for (ddraig.net.custommobs.block.entity.RaidBlockEntity rbe : RaidSystem.getActiveBlockEntities()) {
                                                if (rbe.getRaidId().equalsIgnoreCase(raidId) || rbe.getRaidId().replace(' ', '_').equalsIgnoreCase(raidId)) {
                                                    rbe.abortRaid();
                                                    stopped = true;
                                                }
                                            }

                                            if (stopped) {
                                                context.getSource().sendSuccess(() -> Component.translatable("command.custom_mobs.stop_success", raidId), true);
                                                return 1;
                                            } else {
                                                context.getSource().sendFailure(Component.translatable("command.custom_mobs.stop_failure", raidId));
                                                return 0;
                                            }
                                        })
                                )
                        )
                        .then(Commands.literal("info")
                                .executes(context -> {
                                    List<String> activeGlobal = RaidSystem.getActiveRaidIds();
                                    List<String> activeBlocks = new java.util.ArrayList<>();
                                    for (ddraig.net.custommobs.block.entity.RaidBlockEntity be : RaidSystem.getActiveBlockEntities()) {
                                        if (be.getActiveRaidState() != ddraig.net.custommobs.block.entity.RaidBlockEntity.RaidState.IDLE) {
                                            activeBlocks.add(be.getRaidId() + " at " + be.getBlockPos().toShortString() + " (State: " + be.getActiveRaidState() + ", Wave: " + (be.getCurrentWave() + 1) + ")");
                                        }
                                    }
                                    
                                    context.getSource().sendSuccess(() -> Component.literal("Active Global Raids: " + activeGlobal.toString()), false);
                                    context.getSource().sendSuccess(() -> Component.literal("Active Raid Blocks: " + (activeBlocks.isEmpty() ? "None" : String.join(", ", activeBlocks))), false);
                                    return 1;
                                })
                        )
                )
        );
    }

    private static int spawnMob(CommandSourceStack source, String mobId, int count) {
        if (!MobRegistry.loadedMobs.containsKey(mobId)) {
            source.sendFailure(Component.translatable("command.custom_mobs.spawn.unknown", mobId));
            return 0;
        }

        BlockPos pos = BlockPos.containing(source.getPosition());
        ServerLevel level = source.getLevel();
        for (int i = 0; i < count; i++) {
            CustomMobEntity mob = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(level);
            if (mob != null) {
                mob.setTemplateId(mobId);
                mob.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, source.getRotation().y, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
                level.addFreshEntity(mob);
            }
        }
        source.sendSuccess(() -> Component.translatable("command.custom_mobs.spawn.success_multiple", count, mobId), true);
        return count;
    }

    private static int spawnMobCustom(CommandSourceStack source, String mobId, boolean isElite, net.minecraft.world.phys.Vec3 position) {
        if (!MobRegistry.loadedMobs.containsKey(mobId)) {
            source.sendFailure(Component.translatable("command.custom_mobs.spawn.unknown", mobId));
            return 0;
        }

        BlockPos pos = BlockPos.containing(position);
        ServerLevel level = source.getLevel();
        CustomMobEntity mob = ddraig.net.custommobs.registry.ModEntities.CUSTOM_MOB.get().create(level);
        if (mob != null) {
            mob.setTemplateId(mobId);
            mob.setElite(isElite);
            mob.moveTo(position.x, position.y, position.z, source.getRotation().y, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(mob);
            source.sendSuccess(() -> Component.translatable("command.custom_mobs.spawn.success_single", isElite ? "Elite " : "", mobId), true);
            return 1;
        }
        return 0;
    }

    private static int addBestiaryDiscovery(CommandSourceStack source, ServerPlayer targetPlayer, String mobId) {
        ddraig.net.custommobs.data.DatabaseManager.discoverMob(targetPlayer.getUUID(), mobId);
        ddraig.net.custommobs.network.ModPackets.syncBestiaryDiscoveries(targetPlayer);
        source.sendSuccess(() -> Component.translatable("command.custom_mobs.bestiary.add", mobId, targetPlayer.getScoreboardName()), true);
        return 1;
    }

    private static int removeBestiaryDiscovery(CommandSourceStack source, ServerPlayer targetPlayer, String mobId) {
        ddraig.net.custommobs.data.DatabaseManager.removeDiscovery(targetPlayer.getUUID(), mobId);
        ddraig.net.custommobs.network.ModPackets.syncBestiaryDiscoveries(targetPlayer);
        source.sendSuccess(() -> Component.translatable("command.custom_mobs.bestiary.remove", mobId, targetPlayer.getScoreboardName()), true);
        return 1;
    }

    private static int spawnProjectile(CommandSourceStack source, String projId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();

            double dx = player.getLookAngle().x;
            double dy = player.getLookAngle().y;
            double dz = player.getLookAngle().z;

            if (MobRegistry.loadedProjectiles.containsKey(projId)) {
                ddraig.net.custommobs.entity.CustomProjectileEntity proj = new ddraig.net.custommobs.entity.CustomProjectileEntity(level, player);
                proj.setProjectileId(projId);
                proj.setPos(player.getX(), player.getY() + player.getEyeHeight() - 0.1, player.getZ());
                proj.shoot(dx, dy, dz, 1.6F, 1.0F);
                level.addFreshEntity(proj);
                source.sendSuccess(() -> Component.translatable("command.custom_mobs.projectile.fired_custom", projId), true);
                return 1;
            } else {
                net.minecraft.resources.ResourceLocation resLoc = net.minecraft.resources.ResourceLocation.tryParse(projId);
                if (resLoc != null) {
                    if (resLoc.getNamespace().equals("minecraft") && !projId.contains(":")) {
                        resLoc = new net.minecraft.resources.ResourceLocation("minecraft", projId);
                    }
                    final net.minecraft.resources.ResourceLocation finalResLoc = resLoc;
                    var entityTypeOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(resLoc);
                    if (entityTypeOpt.isPresent()) {
                        net.minecraft.world.entity.Entity entity = entityTypeOpt.get().create(level);
                        if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                            proj.setOwner(player);
                            proj.setPos(player.getX(), player.getY() + player.getEyeHeight() - 0.1, player.getZ());
                            proj.shoot(dx, dy, dz, 1.6F, 1.0F);
                            level.addFreshEntity(proj);
                            source.sendSuccess(() -> Component.translatable("command.custom_mobs.projectile.fired_vanilla", finalResLoc.toString()), true);
                            return 1;
                        } else {
                            source.sendFailure(Component.translatable("command.custom_mobs.projectile.not_projectile", finalResLoc.toString()));
                            return 0;
                        }
                    }
                }
            }
            source.sendFailure(Component.translatable("command.custom_mobs.projectile.failed", projId));
            return 0;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.custom_mobs.projectile.failed", e.getMessage()));
            return 0;
        }
    }
}
