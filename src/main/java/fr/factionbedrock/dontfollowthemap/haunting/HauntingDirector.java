package fr.factionbedrock.dontfollowthemap.haunting;

import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.entity.UnseenEntity;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessableAnimals;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessedAnimal;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMAttachments;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import fr.factionbedrock.dontfollowthemap.util.DFTMAdvancements;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides when something happens to whom. Once per second, each haunted player whose next event is due gets one
 * (picked according to their stage); the other events answer what the player does (killing, sleeping, mining).
 */
public class HauntingDirector
{
    private static final int UPDATE_INTERVAL = 20;
    private static final long RETRY_DELAY = 20L * 30L;
    private static final long KILL_APPARITION_COOLDOWN = 20L * 60L * 4L;
    private static final long BED_SCARE_COOLDOWN = HauntingData.DAY / 2L;
    private static final long THEFT_COOLDOWN = 20L * 60L * 6L;
    private static final long GRAVE_COOLDOWN = HauntingData.DAY * 4L;
    private static final long MAP_REPLACEMENT_DELAY = HauntingData.DAY * 2L;

    public enum Event {SIGHTING, STALK, WHISPER, TORCHES, SIGN, BUILD, POSSESSION}

    public static void registerEvents()
    {
        ServerTickEvents.END_SERVER_TICK.register(HauntingDirector::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DelayedTasks.clear());
        ServerLivingEntityEvents.AFTER_DEATH.register(HauntingDirector::onDeath);
        EntitySleepEvents.START_SLEEPING.register(HauntingDirector::onStartSleeping);
        EntitySleepEvents.MODIFY_WAKE_UP_POSITION.register((entity, sleepingPos, bedState, wakeUpPos) -> entity instanceof ServerPlayer player ? onWakeUp(player, sleepingPos, wakeUpPos) : wakeUpPos);
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {if (player instanceof ServerPlayer serverPlayer) {onBlockBroken(serverPlayer, pos);}});
    }

    /** The overworld game time: always ticking, saved with the world, the same for every dimension. */
    public static long now(MinecraftServer server) {return server.overworld().getGameTime();}

    public static boolean canBeHaunted(ServerPlayer player)
    {
        DFTMConfig config = DFTMConfig.get();
        return config.enabled && player.isAlive() && !player.isSpectator() && (config.hauntCreativePlayers || !player.isCreative());
    }

    private static void onServerTick(MinecraftServer server)
    {
        DelayedTasks.tick(server);
        if (server.getTickCount() % UPDATE_INTERVAL != 0) {return;}
        for (ServerLevel level : server.getAllLevels())
        {
            if (level.hasAttached(DFTMAttachments.TORCH_SWAPS))
            {
                TorchSwaps swaps = TorchSwaps.get(level);
                if (!swaps.isEmpty()) {swaps.tick(level, level.getGameTime());}
            }
        }
        long now = now(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            //only the time spent in the overworld counts, and only there do scheduled events happen
            if (player.level().dimension() == Level.OVERWORLD && canBeHaunted(player)) {updatePlayer(player, now);}
        }
    }

    private static void updatePlayer(ServerPlayer player, long now)
    {
        HauntingData data = HauntingData.get(player);
        if (data.peaceUntil <= now) {data.hauntTicks += UPDATE_INTERVAL;}
        //the map advancement, once it is in their inventory (checked every 5 seconds, only while it matters)
        if (data.hasMapBeenDelivered() && data.sealState == HauntingData.SEALED && (now / UPDATE_INTERVAL) % 5 == 0 && SealMaps.hasSealMap(player)) {DFTMAdvancements.award(player, "the_map");}
        int stage = data.stage(now);
        if (stage == HauntingData.QUIET) {return;}
        if (data.nextEventTime == 0L) {data.nextEventTime = now + nextDelay(stage, player.getRandom()) / 2L;}
        if (now < data.nextEventTime) {return;}
        boolean happened = runScheduledEvent(player, data, stage);
        data.nextEventTime = now + (happened ? nextDelay(stage, player.getRandom()) : RETRY_DELAY);
    }

    private static long nextDelay(int stage, RandomSource random)
    {
        long minutes = switch (stage)
        {
            case HauntingData.WATCHED -> Mth.nextInt(random, 5, 10);
            case HauntingData.HAUNTED -> Mth.nextInt(random, 3, 7);
            default -> Mth.nextInt(random, 2, 5);
        };
        return (long) (minutes * 60L * 20L / DFTMConfig.get().eventFrequency);
    }

    public static boolean isMapDue(ServerPlayer player, HauntingData data, long now)
    {
        if (data.sealState != HauntingData.SEALED) {return false;}
        return !data.hasMapBeenDelivered() || (now - data.mapDeliveredAt > MAP_REPLACEMENT_DELAY && !SealMaps.hasSealMap(player));
    }

    private static boolean runScheduledEvent(ServerPlayer player, HauntingData data, int stage)
    {
        //when the map is due, the next sighting leaves it behind
        long now = now(player.level().getServer());
        if (stage >= HauntingData.HAUNTED && isMapDue(player, data, now) && DFTMConfig.get().sightings && HauntEvents.sighting(player, stage, true)) {return true;}

        List<Event> candidates = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (Event event : Event.values())
        {
            int weight = weight(event, stage);
            if (weight > 0) {candidates.add(event); weights.add(weight);}
        }
        //weighted draw without replacement: if the drawn event can't happen right now, try another one
        RandomSource random = player.getRandom();
        while (!candidates.isEmpty())
        {
            int total = weights.stream().mapToInt(Integer::intValue).sum(), roll = random.nextInt(total), index = 0;
            while (roll >= weights.get(index)) {roll -= weights.get(index); index++;}
            if (run(player, candidates.get(index), stage)) {return true;}
            candidates.remove(index);
            weights.remove(index);
        }
        return false;
    }

    private static int weight(Event event, int stage)
    {
        DFTMConfig config = DFTMConfig.get();
        boolean enabled = switch (event)
        {
            case SIGHTING, STALK -> config.sightings;
            case WHISPER -> true;
            case TORCHES -> config.torchSwaps;
            case SIGN -> config.signs;
            case BUILD -> config.builds;
            case POSSESSION -> !PossessableAnimals.enabled().isEmpty();
        };
        if (!enabled) {return 0;}
        return switch (stage)
        {
            case HauntingData.WATCHED -> switch (event) {case SIGHTING -> 6; case WHISPER, TORCHES -> 3; default -> 0;};
            case HauntingData.HAUNTED -> switch (event) {case SIGHTING -> 5; case WHISPER -> 2; case TORCHES, SIGN -> 3; case BUILD, POSSESSION -> 2; default -> 0;};
            default -> switch (event) {case SIGHTING, STALK, TORCHES, BUILD, POSSESSION -> 3; case SIGN -> 2; case WHISPER -> 1;};
        };
    }

    /** Runs one event right now (also used by the command). */
    public static boolean run(ServerPlayer player, Event event, int stage)
    {
        return switch (event)
        {
            case SIGHTING -> HauntEvents.sighting(player, stage, false);
            case STALK -> HauntEvents.stalk(player);
            case WHISPER -> HauntEvents.whisper(player);
            case TORCHES -> HauntEvents.swapTorches(player, 2 + 2 * Math.max(0, stage - 1));
            case SIGN -> HauntEvents.placeSign(player);
            case BUILD -> HauntEvents.build(player);
            case POSSESSION -> HauntEvents.possessAnimal(player, PossessableAnimals.enabled()) != null;
        };
    }

    private static void onDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source)
    {
        if (!(source.getEntity() instanceof ServerPlayer player) || entity instanceof Player || entity instanceof HerobrineApparitionEntity || entity instanceof HerobrineEntity || entity instanceof UnseenEntity) {return;}
        DFTMConfig config = DFTMConfig.get();
        if (!config.killApparitions || !canBeHaunted(player) || player.level() != entity.level()) {return;}
        HauntingData data = HauntingData.get(player);
        long now = now(player.level().getServer());
        int stage = data.stage(now);
        if (stage == HauntingData.QUIET || data.isOnCooldown(data.lastKillApparition, KILL_APPARITION_COOLDOWN, now)) {return;}
        double chance = config.killApparitionChance * (stage >= HauntingData.HAUNTED ? 1.5D : 1.0D);
        if (entity instanceof PossessedAnimal) {chance = 1.0D;}
        if (player.getRandom().nextDouble() < chance && HauntEvents.killApparition(player, entity.position())) {data.lastKillApparition = now;}
    }

    private static void onStartSleeping(LivingEntity entity, BlockPos bedPos)
    {
        if (!(entity instanceof ServerPlayer player) || !DFTMConfig.get().bedScares || !canBeHaunted(player)) {return;}
        HauntingData data = HauntingData.get(player);
        long now = now(player.level().getServer());
        int stage = data.stage(now);
        if (stage == HauntingData.QUIET || data.isOnCooldown(data.lastBedScare, BED_SCARE_COOLDOWN, now)) {return;}
        double chance = DFTMConfig.get().bedScareChance * (stage >= HauntingData.HAUNTED ? 1.5D : 1.0D);
        if (player.getRandom().nextDouble() >= chance) {return;}
        data.lastBedScare = now;
        //let the screen fade to black first
        DelayedTasks.schedule(Mth.nextInt(player.getRandom(), 30, 55), () ->
        {
            if (player.isSleeping() && player.getSleepingPos().map(bedPos::equals).orElse(false)) {HauntEvents.bedScare(player, bedPos);}
        });
    }

    private static Vec3 onWakeUp(ServerPlayer player, BlockPos bedPos, Vec3 wakeUpPos)
    {
        //only after a full night: not when leaving the bed, nor when woken up by a monster (or by Herobrine himself)
        if (!player.isSleepingLongEnough() || !player.level().isBrightOutside() || !canBeHaunted(player)) {return wakeUpPos;}
        HauntingData data = HauntingData.get(player);
        long now = now(player.level().getServer());
        int stage = data.stage(now);
        if (stage >= HauntingData.HAUNTED && isMapDue(player, data, now))
        {
            DelayedTasks.schedule(20, () -> {if (player.isAlive()) {SealEvents.slipMapIntoPocket(player);}});
            return wakeUpPos;
        }
        DFTMConfig config = DFTMConfig.get();
        if (!config.graves || stage < HauntingData.HAUNTED || data.isOnCooldown(data.lastGrave, GRAVE_COOLDOWN, now) || player.getRandom().nextDouble() >= config.graveChance) {return wakeUpPos;}
        BlockPos grave = GraveDigger.dig(player.level(), bedPos, player);
        if (grave == null) {return wakeUpPos;}
        data.lastGrave = now;
        DelayedTasks.schedule(2, () ->
        {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DFTMSounds.GRAVE_WAKE, SoundSource.HOSTILE, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.dontfollowthemap.grave").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            DFTMAdvancements.award(player, "rest_in_peace");
        });
        return Vec3.atBottomCenterOf(grave);
    }

    private static void onBlockBroken(ServerPlayer player, BlockPos pos)
    {
        DFTMConfig config = DFTMConfig.get();
        if (!config.thief || player.isCreative() || !canBeHaunted(player) || player.level().dimension() != Level.OVERWORLD) {return;}
        HauntingData data = HauntingData.get(player);
        long now = now(player.level().getServer());
        if (data.stage(now) < HauntingData.HAUNTED || data.isOnCooldown(data.lastTheft, THEFT_COOLDOWN, now) || player.getRandom().nextDouble() >= config.thiefChance) {return;}
        //the drops appear during the break: look for them on the next tick
        DelayedTasks.schedule(1, () -> {if (UnseenEntity.comeForDrops(player, pos)) {data.lastTheft = now;}});
    }
}
