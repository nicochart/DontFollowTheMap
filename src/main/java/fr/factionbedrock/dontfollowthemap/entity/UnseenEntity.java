package fr.factionbedrock.dontfollowthemap.entity;

import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.registry.DFTMEntities;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Something invisible that follows the player for a while, taking what falls around them (what they mine, drop or kill)
 * before they can pick it up. Only its footsteps and a little dust give it away, and nothing can touch it until it has what
 * it came for: 3 to 5 stacks. Then it runs away, raising more dust; hitting it makes it drop everything it took.
 * Whatever it keeps is given back when Herobrine is defeated.
 */
public class UnseenEntity extends PathfinderMob
{
    private static final int MAX_FOLLOWING_TICKS = 20 * 60, MAX_FLEEING_TICKS = 20 * 8;
    private static final int MIN_LOOT = 3, MAX_LOOT = 5;
    /** How far from the player the items it goes for may be. */
    private static final double LOOT_RANGE = 8.0D;
    /** Items younger than this have just fallen. */
    private static final int FRESH_ITEM_AGE = 20;
    /** The pickup delay it gives the items it goes for, so that the player doesn't pick them up first. */
    private static final int HEAD_START = 40;
    /** How long it tries to get to an item before giving up on it. */
    private static final int GIVE_UP_TICKS = HEAD_START + 20;
    private static final double FOLLOW_DISTANCE = 4.0D;

    @Nullable private UUID victimId;
    private final List<ItemEntity> targets = new ArrayList<>();
    /** Items it gave up on, out of reach. */
    private final List<ItemEntity> ignored = new ArrayList<>();
    @Nullable private ItemEntity chased;
    private int chasingTicks;
    private final List<ItemStack> carried = new ArrayList<>();
    private int wanted = MIN_LOOT;
    private int fleeingTicks = -1;
    private boolean gone;

    public UnseenEntity(EntityType<? extends UnseenEntity> type, Level level)
    {
        super(type, level);
        this.setInvisible(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** Comes for the items dropped at the given position, then keeps following the player. False if there was nothing to take, nowhere to come from, or if one already follows them. */
    public static boolean comeForDrops(ServerPlayer player, BlockPos brokenPos)
    {
        ServerLevel level = player.level();
        if (!level.getEntitiesOfClass(UnseenEntity.class, player.getBoundingBox().inflate(64.0D), unseen -> unseen.isAfter(player)).isEmpty()) {return false;}
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(brokenPos).inflate(1.0D), item -> item.isAlive() && item.getAge() < 10 && !item.getItem().isEmpty());
        if (drops.isEmpty()) {return false;}
        BlockPos spot = HauntSpots.findHiddenSpotAround(player, brokenPos, 3, 5, 3);
        if (spot == null) {return false;}
        spawn(level, spot, player, drops);
        return true;
    }

    public static UnseenEntity spawn(ServerLevel level, BlockPos spot, ServerPlayer victim, List<ItemEntity> drops)
    {
        UnseenEntity unseen = new UnseenEntity(DFTMEntities.UNSEEN, level);
        unseen.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, victim.getRandom().nextFloat() * 360.0F, 0.0F);
        unseen.victimId = victim.getUUID();
        unseen.wanted = Mth.nextInt(victim.getRandom(), MIN_LOOT, MAX_LOOT);
        drops.forEach(unseen::claim);
        level.addFreshEntity(unseen);
        return unseen;
    }

    public boolean isAfter(Player player) {return player.getUUID().equals(this.victimId);}
    /** Running away with what it took: from then on, it can be caught. */
    public boolean isFleeing() {return this.fleeingTicks >= 0;}
    /** How many stacks it takes before running away (3 to 5, picked at random when it appears). */
    public void setWanted(int wanted) {this.wanted = wanted;}

    @Nullable private ServerPlayer getVictim(ServerLevel level) {return this.victimId != null && level.getPlayerByUUID(this.victimId) instanceof ServerPlayer player ? player : null;}

    @Override public void tick()
    {
        super.tick();
        if (this.level() instanceof ServerLevel level && !this.gone && !this.isRemoved()) {this.serverTick(level);}
    }

    private void serverTick(ServerLevel level)
    {
        ServerPlayer victim = this.getVictim(level);
        if (victim == null || !victim.isAlive() || victim.level() != level)
        {
            this.leave(level, false);
            return;
        }
        this.raiseDust(level);
        if (this.isFleeing()) {this.tickFleeing(level, victim);}
        else {this.tickFollowing(level, victim);}
    }

    /** The dust it raises as it walks: a little while it follows the player, more once it runs away. */
    private void raiseDust(ServerLevel level)
    {
        boolean fleeing = this.isFleeing();
        if (this.tickCount % (fleeing ? 3 : 10) != 0 || this.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4D) {return;}
        BlockState ground = level.getBlockState(this.getBlockPosBelowThatAffectsMyMovement());
        if (!ground.isAir()) {level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.05D, this.getZ(), fleeing ? 2 : 1, 0.15D, 0.0D, 0.15D, 0.0D);}
    }

    private void tickFollowing(ServerLevel level, ServerPlayer victim)
    {
        if (this.tickCount % 2 == 0)
        {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, victim.getBoundingBox().inflate(LOOT_RANGE, 4.0D, LOOT_RANGE), item -> this.isFreshLoot(item, victim))) {this.claim(item);}
        }
        //picked up by the player, or left far behind
        this.targets.removeIf(item -> !item.isAlive() || item.distanceToSqr(victim) > LOOT_RANGE * LOOT_RANGE * 4.0D);
        this.ignored.removeIf(item -> !item.isAlive());
        ItemEntity target = this.targets.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target != this.chased)
        {
            this.chased = target;
            this.chasingTicks = 0;
        }
        if (target != null)
        {
            if (this.distanceToSqr(target) < 1.7D) {this.steal(level, victim, target);}
            else if (++this.chasingTicks > GIVE_UP_TICKS || ((this.tickCount % 5 == 0 || this.getNavigation().isDone()) && !this.chase(target))) {this.giveUp(target);}
        }
        else if (this.tickCount % 10 == 0)
        {
            //nothing to take: it stays right behind the player
            Vec3 behind = victim.position().subtract(Vec3.directionFromRotation(0.0F, victim.getYRot()).scale(FOLLOW_DISTANCE));
            if (this.distanceToSqr(behind) > 2.0D * 2.0D) {this.getNavigation().moveTo(behind.x, behind.y, behind.z, 1.2D);}
        }
        if (this.carried.size() >= this.wanted || this.tickCount > MAX_FOLLOWING_TICKS) {this.startFleeing(level);}
    }

    /** What has just fallen around the player: mined, dropped by them, or dropped by what they killed (not thrown by someone else). */
    private boolean isFreshLoot(ItemEntity item, ServerPlayer victim)
    {
        if (!item.isAlive() || item.getAge() >= FRESH_ITEM_AGE || item.getItem().isEmpty() || this.targets.contains(item) || this.ignored.contains(item)) {return false;}
        Entity thrower = item.getOwner();
        return thrower == null || thrower == victim;
    }

    /** Walks to the item. False if it can't get there. */
    private boolean chase(ItemEntity item)
    {
        //right onto it: from the next block, it could stay just too far to take it
        Path path = this.getNavigation().createPath(item, 0);
        //no path at all just means it can't look for one right now (in mid-air...): only a path falling short means out of reach
        if (path == null) {return true;}
        if (!path.canReach()) {return false;}
        this.getNavigation().moveTo(path, 1.6D);
        return true;
    }

    /** Out of reach (in a hole, behind glass...): it leaves it to the player. */
    private void giveUp(ItemEntity item)
    {
        this.targets.remove(item);
        this.ignored.add(item);
        this.chased = null;
        item.setPickUpDelay(0);
    }

    private void claim(ItemEntity item)
    {
        if (this.targets.contains(item)) {return;}
        this.targets.add(item);
        item.setPickUpDelay(HEAD_START);
    }

    private void steal(ServerLevel level, ServerPlayer victim, ItemEntity item)
    {
        ItemStack stack = item.getItem().copy();
        level.playSound(null, item.getX(), item.getY(), item.getZ(), DFTMSounds.UNSEEN_STEAL, SoundSource.HOSTILE, 0.6F, 1.0F);
        item.discard();
        this.targets.remove(item);
        this.carried.add(stack.copy());
        //kept with the player's data right away: nothing is lost if the game closes now
        HauntingData.get(victim).addStolen(stack);
    }

    private void startFleeing(ServerLevel level)
    {
        //it found nothing: it just leaves
        if (this.carried.isEmpty())
        {
            this.leave(level, false);
            return;
        }
        this.fleeingTicks = 0;
        this.targets.clear();
        this.getNavigation().stop();
    }

    private void tickFleeing(ServerLevel level, ServerPlayer victim)
    {
        this.fleeingTicks++;
        if (this.fleeingTicks % 20 == 1 || this.getNavigation().isDone())
        {
            Vec3 away = DefaultRandomPos.getPosAway(this, 20, 7, victim.position());
            if (away != null) {this.getNavigation().moveTo(away.x, away.y, away.z, 1.7D);}
        }
        if (this.fleeingTicks > MAX_FLEEING_TICKS || this.distanceTo(victim) > 24.0D) {this.leave(level, false);}
    }

    private void leave(ServerLevel level, boolean withSmoke)
    {
        this.gone = true;
        if (withSmoke)
        {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.9D, this.getZ(), 20, 0.25D, 0.6D, 0.25D, 0.02D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), DFTMSounds.VANISH, SoundSource.HOSTILE, 1.0F, 1.2F);
        }
        this.discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {return super.hurtServer(level, source, damage);}
        //nothing can touch it before it has what it came for
        if (!this.isFleeing() || this.gone || !(source.getEntity() instanceof Player)) {return false;}
        //caught: it lets go of everything it took
        ServerPlayer victim = this.getVictim(level);
        for (ItemStack stack : this.carried)
        {
            if (victim != null) {HauntingData.get(victim).removeStolen(stack);}
            this.spawnAtLocation(level, stack);
        }
        this.carried.clear();
        this.leave(level, true);
        return false;
    }

    //it doesn't bump into anyone: that would give it away
    @Override public boolean isPushable() {return false;}
    @Override protected void doPush(Entity entity) {}

    @Override public boolean isInvisible() {return true;}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override public boolean canBeLeashed() {return false;}
    @Override public boolean shouldShowName() {return false;}
    @Override protected boolean shouldDropLoot(ServerLevel level) {return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect) {return false;}
}
