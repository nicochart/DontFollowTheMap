package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.util.DFTMAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Herobrine passing for a player in a lived-in place. Wearing the skin and the name of another connected player (a default skin
 * and no name when nobody else is there), it goes from one workstation to another: it opens the chests, stays at the crafting
 * table or the furnace, opens and closes the doors on its way, and pays no attention to the player... unless it punches them
 * when they come too close. Hit it: it takes the blow, then freezes, stares, shows its real face for an instant, and is gone.
 */
public class ImitatorEntity extends PathfinderMob
{
    /** What it pretends to use: data/dontfollowthemap/tags/block/imitated_blocks.json. */
    public static final TagKey<Block> IMITATED_BLOCKS = TagKey.create(Registries.BLOCK, DontFollowTheMap.id("imitated_blocks"));
    /** Whose skin it wears: a connected player's UUID, or a random one that picks a default skin. */
    private static final EntityDataAccessor<String> DATA_SKIN_OWNER = SynchedEntityData.defineId(ImitatorEntity.class, EntityDataSerializers.STRING);

    public static final int STARE_TICKS = 30;
    public static final int REVEAL_TICKS = 10;
    private static final int LIFETIME = 20 * 60 * 3;
    private static final int WALKING_TIMEOUT = 20 * 12;
    private static final int PUNCH_COOLDOWN = 20 * 10;
    private static final double REACH = 2.5D;
    private static final double PUNCH_RANGE = 2.5D;
    private static final float PUNCH_CHANCE = 0.4F;
    private static final List<Item> HELD_ITEMS = List.of(Items.OAK_PLANKS, Items.OAK_LOG, Items.COBBLESTONE, Items.TORCH, Items.COAL, Items.IRON_INGOT, Items.BREAD, Items.STICK);

    @Nullable private UUID victimId;
    private final List<BlockPos> workstations = new ArrayList<>();
    /** Where it is going, or what it is using. */
    @Nullable private BlockPos workstation;
    @Nullable private BlockPos lastWorkstation;
    private boolean using;
    @Nullable private BlockPos openedContainer;
    /** Ticks left for the current step: walking to a workstation, using it, or standing around. */
    private int stepTicks;
    @Nullable private Vec3 lookPoint;
    @Nullable private Player watched;
    private boolean punches;
    private int punchCooldown;
    @Nullable private UUID unmaskedBy;
    private int stareTicks;

    public ImitatorEntity(EntityType<? extends ImitatorEntity> type, Level level)
    {
        super(type, level);
        this.xpReward = 0;
        this.getNavigation().setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.ATTACK_DAMAGE, 2.0D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** Starts pretending next to the given workstations, for this victim. */
    public static ImitatorEntity spawn(ServerLevel level, BlockPos pos, ServerPlayer victim, List<BlockPos> workstations)
    {
        ImitatorEntity imitator = new ImitatorEntity(DFTMEntities.IMITATOR, level);
        RandomSource random = victim.getRandom();
        float yaw = random.nextFloat() * 360.0F;
        imitator.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        imitator.setYHeadRot(yaw);
        imitator.setYBodyRot(yaw);
        imitator.victimId = victim.getUUID();
        imitator.workstations.addAll(workstations);
        imitator.punches = random.nextFloat() < PUNCH_CHANCE;
        imitator.disguiseAs(pickImitated(victim, level.getServer().getPlayerList().getPlayers(), random));
        if (random.nextFloat() < 0.7F) {imitator.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HELD_ITEMS.get(random.nextInt(HELD_ITEMS.size()))));}
        imitator.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        level.addFreshEntity(imitator);
        return imitator;
    }

    /** Another player to pass for, preferably one far from the victim (they can't be seen in two places at once). Null when the victim is alone. */
    @Nullable public static ServerPlayer pickImitated(ServerPlayer victim, List<ServerPlayer> players, RandomSource random)
    {
        List<ServerPlayer> others = players.stream().filter(player -> player != victim && !player.isSpectator()).toList();
        List<ServerPlayer> farAway = others.stream().filter(player -> player.level() != victim.level() || player.distanceToSqr(victim) > 64.0D * 64.0D).toList();
        List<ServerPlayer> candidates = farAway.isEmpty() ? others : farAway;
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /** Takes the skin and the name of that player, or one of the default skins and no name when there is nobody to pass for. */
    public void disguiseAs(@Nullable Player player)
    {
        if (player != null)
        {
            this.entityData.set(DATA_SKIN_OWNER, player.getStringUUID());
            this.setCustomName(Component.literal(player.getGameProfile().name()));
            this.setCustomNameVisible(true);
        }
        else
        {
            this.entityData.set(DATA_SKIN_OWNER, UUID.randomUUID().toString());
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        }
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_OWNER, "");
    }

    /** Whose skin it wears (see the renderer): null for the default skin. */
    @Nullable public UUID getSkinOwner()
    {
        String owner = this.entityData.get(DATA_SKIN_OWNER);
        if (owner.isEmpty()) {return null;}
        try {return UUID.fromString(owner);}
        catch (IllegalArgumentException e) {return null;}
    }

    public boolean isImitatingFor(Player player) {return player.getUUID().equals(this.victimId);}
    public boolean isUnmasked() {return this.unmaskedBy != null;}
    /** Whether it punches the player who comes too close (decided at random when it appears). */
    public void setPunches(boolean punches) {this.punches = punches;}

    @Override protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        //on its way, it opens the doors and closes them behind itself, as a player would
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
    }

    @Override protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        ServerPlayer victim = this.victimId != null && level.getPlayerByUUID(this.victimId) instanceof ServerPlayer player ? player : null;
        if (victim == null || !victim.isAlive() || victim.isSpectator() || this.distanceToSqr(victim) > 96.0D * 96.0D)
        {
            this.discard();
            return;
        }
        if (this.unmaskedBy != null)
        {
            this.tickUnmasked(level);
            return;
        }
        //its time is up: it leaves when nobody is looking
        if (this.tickCount > LIFETIME && HauntSpots.isHiddenFrom(victim, this.getEyePosition()))
        {
            this.discard();
            return;
        }
        if (this.punchCooldown > 0) {this.punchCooldown--;}
        if (this.punches && this.punchCooldown == 0 && this.distanceToSqr(victim) < PUNCH_RANGE * PUNCH_RANGE && this.hasLineOfSight(victim))
        {
            this.punch(level, victim);
            return;
        }
        this.stepTicks--;
        if (this.using) {this.tickUsing(level);}
        else if (this.workstation != null) {this.tickWalking(level);}
        else {this.tickIdle(level);}
    }

    private void tickIdle(ServerLevel level)
    {
        if (this.stepTicks <= 0)
        {
            this.watched = null;
            this.goToNextWorkstation(level);
            return;
        }
        //looking around, or at whoever it just punched
        if (this.watched != null) {this.getLookControl().setLookAt(this.watched, 30.0F, 30.0F);}
        else
        {
            if (this.lookPoint == null || this.stepTicks % 40 == 0) {this.lookPoint = this.getEyePosition().add(this.random.nextGaussian() * 4.0D, this.random.nextGaussian(), this.random.nextGaussian() * 4.0D);}
            this.getLookControl().setLookAt(this.lookPoint);
        }
    }

    private void goToNextWorkstation(ServerLevel level)
    {
        this.workstations.removeIf(pos -> !level.getBlockState(pos).is(IMITATED_BLOCKS));
        List<BlockPos> choices = this.workstations.stream().filter(pos -> !pos.equals(this.lastWorkstation)).toList();
        if (choices.isEmpty()) {choices = this.workstations;}
        //nothing left to use: it just stands there
        if (choices.isEmpty())
        {
            this.stepTicks = 40;
            return;
        }
        this.workstation = choices.get(this.random.nextInt(choices.size()));
        this.stepTicks = WALKING_TIMEOUT;
        this.walkTo(this.workstation);
    }

    private void walkTo(BlockPos pos) {this.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.7D);}

    private boolean isInReach(BlockPos pos) {return this.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < REACH * REACH;}

    private void tickWalking(ServerLevel level)
    {
        BlockPos target = this.workstation;
        if (this.isInReach(target))
        {
            this.startUsing(level);
            return;
        }
        //out of reach: it forgets about that one
        if (this.stepTicks <= 0)
        {
            this.workstations.remove(target);
            this.workstation = null;
            this.stepTicks = 20;
            return;
        }
        if (this.getNavigation().isDone() && this.stepTicks % 20 == 0) {this.walkTo(target);}
    }

    private void startUsing(ServerLevel level)
    {
        this.getNavigation().stop();
        this.using = true;
        this.stepTicks = Mth.nextInt(this.random, 60, 160);
        this.getLookControl().setLookAt(Vec3.atCenterOf(this.workstation));
        //the right click
        this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        if (setContainerOpen(level, this.workstation, true)) {this.openedContainer = this.workstation;}
    }

    private void tickUsing(ServerLevel level)
    {
        if (this.stepTicks <= 0 || !level.getBlockState(this.workstation).is(IMITATED_BLOCKS))
        {
            this.stopUsing(level);
            return;
        }
        this.getLookControl().setLookAt(Vec3.atCenterOf(this.workstation));
    }

    private void stopUsing(ServerLevel level)
    {
        if (this.openedContainer != null)
        {
            setContainerOpen(level, this.openedContainer, false);
            this.openedContainer = null;
        }
        if (this.using) {this.lastWorkstation = this.workstation;}
        this.using = false;
        this.workstation = null;
        this.lookPoint = null;
        this.stepTicks = Mth.nextInt(this.random, 20, 70);
    }

    /** Opens or closes a chest or a barrel the way a player does: lid or open state, and sound. False if it is neither, or if a player has it open. */
    private static boolean setContainerOpen(ServerLevel level, BlockPos pos, boolean open)
    {
        BlockState state = level.getBlockState(pos);
        SoundEvent sound;
        if (state.getBlock() instanceof ChestBlock chest)
        {
            if (ChestBlockEntity.getOpenCount(level, pos) > 0) {return false;}
            level.blockEvent(pos, chest, 1, open ? 1 : 0);
            sound = open ? chest.getOpenChestSound() : chest.getCloseChestSound();
        }
        else if (state.getBlock() instanceof BarrelBlock)
        {
            if (state.getValue(BarrelBlock.OPEN) == open) {return false;}
            //no neighbour update: an observer must not notice anything
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            sound = open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
        }
        else {return false;}
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        return true;
    }

    private void punch(ServerLevel level, ServerPlayer victim)
    {
        this.stopUsing(level);
        this.getNavigation().stop();
        this.getLookControl().setLookAt(victim, 180.0F, 180.0F);
        this.swingForAttack(InteractionHand.MAIN_HAND);
        this.doHurtTarget(level, victim);
        this.punchCooldown = PUNCH_COOLDOWN;
        //it looks at them for a moment, then goes back to what it was doing
        this.watched = victim;
        this.stepTicks = 40;
    }

    /** Caught: it stops pretending, and nothing can hurt it any more. */
    private void unmask(ServerLevel level, Player player)
    {
        this.unmaskedBy = player.getUUID();
        this.stopUsing(level);
        this.getNavigation().stop();
    }

    private void tickUnmasked(ServerLevel level)
    {
        //it doesn't move by itself any more (the knockback of the blow still plays out)
        this.getNavigation().stop();
        Player player = level.getPlayerByUUID(this.unmaskedBy);
        if (player != null)
        {
            this.getLookControl().setLookAt(player, 180.0F, 180.0F);
            this.setYBodyRot(this.getYHeadRot());
        }
        if (++this.stareTicks < STARE_TICKS) {return;}
        //its real face, for an instant
        if (player instanceof ServerPlayer serverPlayer)
        {
            HerobrineApparitionEntity.spawn(level, this.position(), serverPlayer, HerobrineApparitionEntity.Mode.FLASH, REVEAL_TICKS);
            DFTMAdvancements.award(serverPlayer, "unmasked");
        }
        HauntEvents.appearSuddenly(level, this.position());
        this.discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {return super.hurtServer(level, source, damage);}
        //only the first blow of a player gets through: taken like anyone would take it (damage, knockback), but never deadly
        if (this.unmaskedBy != null || !(source.getEntity() instanceof Player player) || player.isSpectator()) {return false;}
        boolean hurt = super.hurtServer(level, source, Math.min(damage, this.getHealth() - 1.0F));
        this.unmask(level, player);
        return hurt;
    }

    //hit, it sounds like a player
    @Override protected SoundEvent getHurtSound(DamageSource source) {return SoundEvents.PLAYER_HURT;}
    @Override protected SoundEvent getDeathSound() {return SoundEvents.PLAYER_DEATH;}

    @Override public void remove(RemovalReason reason)
    {
        //whatever happens, it doesn't leave a chest or a barrel open behind it
        if (this.openedContainer != null && this.level() instanceof ServerLevel level && level.isLoaded(this.openedContainer)) {setContainerOpen(level, this.openedContainer, false);}
        this.openedContainer = null;
        super.remove(reason);
    }

    @Override public boolean isPushable() {return this.unmaskedBy == null && super.isPushable();}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override public boolean canBeLeashed() {return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect) {return false;}
    @Override protected boolean shouldDropLoot(ServerLevel level) {return false;}
}
