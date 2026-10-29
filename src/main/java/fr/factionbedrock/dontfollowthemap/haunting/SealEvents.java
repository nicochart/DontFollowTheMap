package fr.factionbedrock.dontfollowthemap.haunting;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.block.SealBlock;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineEntity;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.network.FightStartingPayload;
import fr.factionbedrock.dontfollowthemap.registry.DFTMBlocks;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMItems;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import fr.factionbedrock.dontfollowthemap.util.DFTMAdvancements;
import fr.factionbedrock.dontfollowthemap.world.SealShrines;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The map, the seal and what happens when it breaks. */
public class SealEvents
{
    /** From the seal breaking (or the call) to Herobrine's arrival. */
    public static final int SUMMON_DELAY = 70;
    public static final double ARENA_RADIUS = 48.0D;

    /** Drops the seal map where an apparition vanished. */
    public static void leaveMap(ServerLevel level, Vec3 pos, ServerPlayer player)
    {
        ItemStack map = SealMaps.create(level, player.blockPosition());
        if (map == null) {DontFollowTheMap.LOGGER.debug("No seal shrine around {}, the map was not left", player.blockPosition()); return;}
        ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.2D, pos.z, map, 0.0D, 0.15D, 0.0D);
        item.setUnlimitedLifetime();
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.x, pos.y + 0.5D, pos.z, 40, 0.3D, 0.4D, 0.3D, 0.0D);
        level.playSound(null, pos.x, pos.y, pos.z, DFTMSounds.WHISPER, SoundSource.HOSTILE, 1.0F, 0.8F);
        HauntingData.get(player).mapDeliveredAt = HauntingDirector.now(level.getServer());
    }

    /** Slips the seal map into the player's inventory (used when they wake up and never got one). */
    public static boolean slipMapIntoPocket(ServerPlayer player)
    {
        ItemStack map = SealMaps.create(player.level(), player.blockPosition());
        if (map == null) {return false;}
        if (!player.getInventory().add(map)) {player.spawnAtLocation(player.level(), map);}
        player.sendOverlayMessage(Component.translatable("message.dontfollowthemap.map_in_pocket").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DFTMSounds.WHISPER, SoundSource.HOSTILE, 0.7F, 1.0F);
        HauntingData.get(player).mapDeliveredAt = HauntingDirector.now(player.level().getServer());
        return true;
    }

    public static void breakSeal(ServerLevel level, BlockPos sealPos, ServerPlayer player, ItemStack map)
    {
        map.consume(1, player);
        level.setBlock(sealPos, level.getBlockState(sealPos).setValue(SealBlock.STATE, SealBlock.State.OPEN), Block.UPDATE_ALL);
        HauntingData.get(player).sealState = HauntingData.FREED;
        DFTMAdvancements.award(player, "free_him");

        level.playSound(null, sealPos, DFTMSounds.SEAL_BREAK, SoundSource.HOSTILE, 3.0F, 1.0F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, sealPos.getX() + 0.5D, sealPos.getY() + 1.0D, sealPos.getZ() + 0.5D, 30, 0.4D, 0.2D, 0.4D, 0.08D);
        player.sendOverlayMessage(Component.translatable("message.dontfollowthemap.seal_broken").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        startSummoning(level, sealPos);
    }

    /** Right click on an open seal: calls Herobrine back if he fled. */
    public static void callBack(ServerLevel level, BlockPos sealPos, ServerPlayer player)
    {
        if (findHerobrine(level, sealPos) != null)
        {
            player.sendOverlayMessage(Component.translatable("message.dontfollowthemap.already_here").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        HauntingData.get(player).sealState = HauntingData.FREED;
        level.playSound(null, sealPos, DFTMSounds.HEROBRINE_CALL, SoundSource.HOSTILE, 2.0F, 1.0F);
        startSummoning(level, sealPos);
    }

    private static void startSummoning(ServerLevel level, BlockPos sealPos)
    {
        Vec3 center = Vec3.atBottomCenterOf(sealPos.above());
        for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(center) < ARENA_RADIUS * ARENA_RADIUS))
        {
            //the fight music starts now: it has risen by the time he appears
            if (ServerPlayNetworking.canSend(nearby, FightStartingPayload.TYPE)) {ServerPlayNetworking.send(nearby, new FightStartingPayload(sealPos));}
            if (!nearby.isSpectator()) {nearby.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SUMMON_DELAY + 40));}
        }
        for (int i = 1; i <= 3; i++)
        {
            DelayedTasks.schedule(i * 18, () -> level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.5D, center.z, 15, 0.3D, 0.8D, 0.3D, 0.01D));
        }
        DelayedTasks.schedule(SUMMON_DELAY, () -> summonHerobrine(level, sealPos));
    }

    private static void summonHerobrine(ServerLevel level, BlockPos sealPos)
    {
        BlockState seal = level.getBlockState(sealPos);
        if (!seal.is(DFTMBlocks.SEAL) || seal.getValue(SealBlock.STATE) != SealBlock.State.OPEN || findHerobrine(level, sealPos) != null) {return;}
        Vec3 center = Vec3.atBottomCenterOf(sealPos.above());
        LightningBolt lightning = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (lightning != null)
        {
            lightning.snapTo(center);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }
        HerobrineEntity herobrine = DFTMEntities.HEROBRINE.create(level, EntitySpawnReason.EVENT);
        if (herobrine == null) {return;}
        herobrine.snapTo(center.x, center.y, center.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        herobrine.setSealPos(sealPos);
        level.addFreshEntity(herobrine);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1.0D, center.z, 60, 0.4D, 1.0D, 0.4D, 0.05D);
    }

    @Nullable public static HerobrineEntity findHerobrine(ServerLevel level, BlockPos sealPos)
    {
        return level.getEntitiesOfClass(HerobrineEntity.class, new AABB(sealPos).inflate(ARENA_RADIUS + 16.0D), herobrine -> herobrine.isAlive() && sealPos.equals(herobrine.getSealPos())).stream().findFirst().orElse(null);
    }

    /** Herobrine died: the seal shatters, everyone around gets their stolen items back, and every player on the server some peace. */
    public static void onHerobrineDefeated(ServerLevel level, HerobrineEntity herobrine)
    {
        BlockPos sealPos = herobrine.getSealPos();
        if (sealPos != null && level.getBlockState(sealPos).is(DFTMBlocks.SEAL))
        {
            level.setBlock(sealPos, level.getBlockState(sealPos).setValue(SealBlock.STATE, SealBlock.State.SHATTERED), Block.UPDATE_ALL);
            SealShrines.onDefeated(level, sealPos);
        }
        long now = HauntingDirector.now(level.getServer());
        //the other players get the same peace within a second, or as soon as they come back (WorldPeace.catchUp)
        WorldPeace peace = WorldPeace.onHerobrineDefeated(level.getServer(), now);
        Vec3 pos = herobrine.position();
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(pos) < 64.0D * 64.0D))
        {
            HauntingData data = HauntingData.get(player);
            for (ItemStack stolen : data.stolen) {herobrine.spawnAtLocation(level, stolen.copy());}
            data.stolen.clear();
            data.onHerobrineDefeated(now);
            data.worldDefeats = peace.defeats();
            DFTMAdvancements.award(player, "quiet_again");
            player.sendSystemMessage(Component.translatable("message.dontfollowthemap.defeated").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        herobrine.spawnAtLocation(level, new ItemStack(DFTMItems.SEAL_FRAGMENT));
    }
}
