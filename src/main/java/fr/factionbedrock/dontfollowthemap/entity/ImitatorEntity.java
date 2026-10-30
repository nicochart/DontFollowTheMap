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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Herobrine passing for a player in a lived-in place. Wearing the skin and the name of another connected player (a default skin
 * and no name when nobody else is there), it goes from one workstation to another at a player's pace: it opens the chests, stays at
 * the crafting table or the furnace, opens the closed doors on its way and turns around to close them behind itself (never the ones
 * it found open), and pays no attention to the player... unless it punches them when they come too close.
 * Hit it: it takes the blow, then freezes, stares, shows its real face for an instant, and is gone.
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
    /**
     * On the ground, a mob covers about 2.16 × speed² blocks per tick, where a player covers 2.16 × their speed: at √0.1, it walks like a player
     * (4.3 blocks per second). Sprinting adds 30% to the speed of both, which is too much here: it is the square that must gain 30%.
     */
    public static final double WALKING_SPEED = Math.sqrt(0.1D), SPRINTING_SPEED_MODIFIER = 1.0D / Math.sqrt(1.3D);
    /** It sprints to the workstations farther than this, until it gets this close. */
    private static final double SPRINT_DISTANCE = 10.0D, STOP_SPRINTING_DISTANCE = 4.0D;
    /** It opens a closed door on its way this close to it, and closes the ones it opened once this far past them, if still within reach. */
    private static final double OPEN_DOOR_DISTANCE = 1.8D, CLOSE_DOOR_DISTANCE = 2.0D, DOOR_REACH = 4.5D;
    /** Closing a door behind itself: it turns around, swings its hand after TURN_TICKS, and goes on its way after CLOSE_DOOR_TICKS. */
    private static final int TURN_TICKS = 5, CLOSE_DOOR_TICKS = 11;

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
    /** The doors it opened on its way and hasn't closed yet (lower halves). */
    private final List<BlockPos> openedDoors = new ArrayList<>();
    /** The door it turned around to close. */
    @Nullable private BlockPos closingDoor;
    private int closingTicks;

    public ImitatorEntity(EntityType<? extends ImitatorEntity> type, Level level)
    {
        super(type, level);
        this.xpReward = 0;
        //its paths go through the closed doors: it opens them itself (see openDoorsAhead)
        this.getNavigation().setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, WALKING_SPEED).add(Attributes.ATTACK_DAMAGE, 2.0D).add(Attributes.FOLLOW_RANGE, 32.0D);
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
        //it stands on the ground from the start: it can find its way at once (no path is looked for in mid-air)
        imitator.setOnGround(true);
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
        if (this.closingDoor != null)
        {
            this.tickClosingDoor(level);
            return;
        }
        if (this.punches && this.punchCooldown == 0 && this.distanceToSqr(victim) < PUNCH_RANGE * PUNCH_RANGE && this.hasLineOfSight(victim))
        {
            this.punch(level, victim);
            return;
        }
        this.stepTicks--;
        if (this.using) {this.tickUsing(level);}
        else if (this.workstation != null) {this.tickWalking(level);}
        else {this.tickIdle(level);}
        if (!this.using)
        {
            this.openDoorsAhead(level);
            this.closeDoorsBehind(level);
        }
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

    private void walkTo(BlockPos pos)
    {
        //like a player, it sprints when it has a way to go
        boolean far = this.distanceToSqr(Vec3.atBottomCenterOf(pos)) > SPRINT_DISTANCE * SPRINT_DISTANCE;
        this.setSprinting(far);
        this.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, far ? SPRINTING_SPEED_MODIFIER : 1.0D);
    }

    private void stopSprinting()
    {
        this.setSprinting(false);
        this.getNavigation().setSpeedModifier(1.0D);
    }

    private boolean isInReach(BlockPos pos) {return this.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < REACH * REACH;}

    private void tickWalking(ServerLevel level)
    {
        BlockPos target = this.workstation;
        if (this.isInReach(target))
        {
            this.startUsing(level);
            return;
        }
        if (this.isSprinting() && this.distanceToSqr(Vec3.atBottomCenterOf(target)) < STOP_SPRINTING_DISTANCE * STOP_SPRINTING_DISTANCE) {this.stopSprinting();}
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
        this.stopSprinting();
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

    //doors
    /** Opens the closed doors right ahead on its path, with a swing of the hand like a player's click. */
    private void openDoorsAhead(ServerLevel level)
    {
        Path path = this.getNavigation().getPath();
        if (path != null && !path.isDone())
        {
            for (int i = path.getNextNodeIndex(); i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); i++)
            {
                BlockPos pos = path.getNode(i).asBlockPos();
                if (horizontalDistance(this.position(), pos) <= OPEN_DOOR_DISTANCE) {this.openDoor(level, pos);}
            }
        }
        //caught in a doorway someone closed
        if (this.horizontalCollision) {this.openDoor(level, this.blockPosition());}
    }

    private void openDoor(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (!DoorBlock.isWoodenDoor(state) || !(state.getBlock() instanceof DoorBlock door) || door.isOpen(state)) {return;}
        BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.immutable() : pos.below();
        this.getLookControl().setLookAt(lower.getX() + 0.5D, lower.getY() + 1.0D, lower.getZ() + 0.5D);
        this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        door.setOpen(this, level, state, pos, true);
        if (!this.openedDoors.contains(lower)) {this.openedDoors.add(lower);}
    }

    /**
     * Once past a door it opened, it turns around to close it: not while it is still in the doorway (nor while someone else is),
     * not if its way goes back through it, and not if it already is out of reach (then it is left open).
     * The doors it found open stay open.
     */
    private void closeDoorsBehind(ServerLevel level)
    {
        Path path = this.getNavigation().getPath();
        Iterator<BlockPos> doors = this.openedDoors.iterator();
        while (doors.hasNext())
        {
            BlockPos door = doors.next();
            BlockState state = level.getBlockState(door);
            double distance = horizontalDistance(this.position(), door);
            //closed by someone else, broken, or too far: none of its business any more
            if (!(state.getBlock() instanceof DoorBlock block) || !block.isOpen(state) || distance > DOOR_REACH) {doors.remove(); continue;}
            if (distance < CLOSE_DOOR_DISTANCE || isAhead(path, door) || isDoorwayTaken(level, door)) {continue;}
            doors.remove();
            this.closingDoor = door;
            this.closingTicks = 0;
            this.getNavigation().stop();
            this.stopSprinting();
            return;
        }
    }

    /** Turned around towards the door: the swing of the hand that closes it, then on its way again. */
    private void tickClosingDoor(ServerLevel level)
    {
        BlockPos door = this.closingDoor;
        this.getLookControl().setLookAt(door.getX() + 0.5D, door.getY() + 1.0D, door.getZ() + 0.5D, 45.0F, 45.0F);
        this.setYRot(this.getYHeadRot());
        this.setYBodyRot(this.getYHeadRot());
        if (++this.closingTicks == TURN_TICKS)
        {
            BlockState state = level.getBlockState(door);
            if (state.getBlock() instanceof DoorBlock block && block.isOpen(state) && !isDoorwayTaken(level, door))
            {
                this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                block.setOpen(this, level, state, door, false);
            }
        }
        if (this.closingTicks < CLOSE_DOOR_TICKS) {return;}
        this.closingDoor = null;
        if (this.workstation != null && !this.using) {this.walkTo(this.workstation);}
    }

    private static double horizontalDistance(Vec3 from, BlockPos pos) {return Math.sqrt(Mth.square(pos.getX() + 0.5D - from.x) + Mth.square(pos.getZ() + 0.5D - from.z));}

    /** Whether the rest of the path goes through that door. */
    private static boolean isAhead(@Nullable Path path, BlockPos door)
    {
        if (path == null || path.isDone()) {return false;}
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++)
        {
            BlockPos node = path.getNode(i).asBlockPos();
            if (node.equals(door) || node.equals(door.above())) {return true;}
        }
        return false;
    }

    /** Someone (or something) stands in the doorway: a player wouldn't close the door on them. */
    private boolean isDoorwayTaken(ServerLevel level, BlockPos door)
    {
        return !level.getEntitiesOfClass(LivingEntity.class, AABB.encapsulatingFullBlocks(door, door.above()), entity -> entity != this && entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    private void punch(ServerLevel level, ServerPlayer victim)
    {
        this.stopUsing(level);
        this.getNavigation().stop();
        this.stopSprinting();
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
        this.stopSprinting();
        this.closingDoor = null;
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
