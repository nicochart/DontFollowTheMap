package fr.factionbedrock.dontfollowthemap.entity.possessed;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.entity.HerobrineApparitionEntity;
import fr.factionbedrock.dontfollowthemap.entity.Pursuit;
import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.util.Facing;
import fr.factionbedrock.dontfollowthemap.util.Players;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What Herobrine does with an animal he took over. Same model, same texture, same name: nothing gives it away, except that it stares.
 * It follows its victim at a distance, charges when they come too close (or after staring long enough), strikes once, runs away,
 * and turns back into the plain animal it was once out of sight. A raised shield stops its bite: it bounces off and bites again,
 * and the third time, it turns into Herobrine's stalker. A victim out of its reach (up on a pillar, walled in) gets a leap
 * no animal could make, up to 4 blocks high; higher, it waits below them, staring, for 20 to 30 seconds, then turns into Herobrine's stalker.
 * Every possessed animal forwards its hooks here.
 */
public class Possession
{
    private enum Phase {STARE, ATTACK, FLEE}

    public static final int AMBIENT_SOUND_INTERVAL = 200;
    private static final int MAX_STARE_TICKS = 20 * 60 * 4;
    private static final int CLOSE_STARE_TICKS_BEFORE_ATTACK = 20 * 15;
    private static final int ATTACK_TIMEOUT = 20 * 8;
    private static final int MIN_FLEE_TICKS = 20 * 6;
    /**
     * Out of its reach for UNREACHABLE_TICKS (no complete path), it besieges its victim: a leap onto them when they are up to MAX_LEAP_HEIGHT blocks higher,
     * from up to MAX_LEAP_DISTANCE away, one every LEAP_COOLDOWN ticks; then, after SIEGE_MIN_TICKS to SIEGE_MAX_TICKS, Herobrine's stalker instead of it.
     */
    private static final int UNREACHABLE_TICKS = 40, LEAP_COOLDOWN = 40;
    public static final int SIEGE_MIN_TICKS = 20 * 20, SIEGE_MAX_TICKS = 20 * 30;
    private static final double MAX_LEAP_HEIGHT = 4.5D, MAX_LEAP_DISTANCE = 4.0D;
    /**
     * Its bite (data/dontfollowthemap/damage_type, which armor doesn't stop by itself): 7 hearts with no armor, 3 in full iron, under 2 in diamond.
     * Armor counts more than against an ordinary blow, where it would let 5 hearts through in full iron. 1.5 times more on hard, like any mob's blow.
     */
    private static final ResourceKey<DamageType> BITE = ResourceKey.create(Registries.DAMAGE_TYPE, DontFollowTheMap.id("possessed_bite"));
    private static final float BITE_DAMAGE = 14.0F, BITE_DAMAGE_PER_ARMOR_POINT = 8.0F / 15.0F;
    /** The bites a raised shield may stop before Herobrine comes himself, and the pause after each one. */
    public static final int PARRIED_BITES = 3;
    private static final int PARRIED_BITE_PAUSE = 20;

    private final Animal animal;
    private final PossessableAnimals.Kind<?> kind;
    private Phase phase = Phase.STARE;
    @Nullable private UUID victimId;
    private int phaseTicks;
    private int closeStareTicks;
    /** ATTACK: how long it has chased a victim it can get to, how long they have been out of its reach, its siege below them. */
    private int chaseTicks, unreachableTicks, siegeTicks, siegeLength, leapCooldown;
    /** ATTACK: the bites their shield stopped, and the ticks before the next one. */
    private int parriedBites, biteCooldown;

    public Possession(Animal animal, PossessableAnimals.Kind<?> kind)
    {
        this.animal = animal;
        this.kind = kind;
    }

    /** The plain animal's attributes, plus what it needs to follow its victim. */
    public static AttributeSupplier.Builder attributes(AttributeSupplier.Builder plainAttributes)
    {
        return plainAttributes.add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    /** What its bite takes from a player with that much armor, before enchantments and effects (on normal). */
    public static float biteDamage(int armor) {return BITE_DAMAGE - BITE_DAMAGE_PER_ARMOR_POINT * Math.min(armor, 20);}

    /** Called once the animal created its own goals (some animals need theirs to exist): the possession drives everything instead. */
    public static void replaceGoals(Animal animal, GoalSelector goals)
    {
        goals.removeAllGoals(goal -> true);
        goals.addGoal(0, new FloatGoal(animal));
    }

    /** Still just staring (it hasn't charged yet). */
    public boolean isStaring() {return this.phase == Phase.STARE;}

    void setVictim(Player victim) {this.victimId = victim.getUUID();}

    public SoundEvent ambientSound() {return this.kind.ambientSound();}

    public void tick(ServerLevel level)
    {
        this.phaseTicks++;
        //spawned from an egg: it picks whoever is around
        if (this.victimId == null && level.getNearestPlayer(this.animal, 32.0D) instanceof Player nearest) {this.victimId = nearest.getUUID();}
        ServerPlayer victim = Players.inLevel(level, this.victimId);
        if (victim == null || !Players.isInTheGame(victim) || this.animal.distanceToSqr(victim) > 48.0D * 48.0D)
        {
            this.release();
            return;
        }

        switch (this.phase)
        {
            case STARE ->
            {
                this.stareAt(victim);
                double distance = this.animal.distanceTo(victim);
                if (distance > 14.0D) {this.animal.getNavigation().moveTo(victim, 0.9D);}
                else if (distance < 10.0D) {this.animal.getNavigation().stop();}
                if (distance < 10.0D) {this.closeStareTicks++;}
                if (distance < 3.5D || this.closeStareTicks > CLOSE_STARE_TICKS_BEFORE_ATTACK) {this.setPhase(Phase.ATTACK);}
                else if (this.phaseTicks > MAX_STARE_TICKS) {this.release();}
            }
            case ATTACK ->
            {
                this.stareAt(victim);
                if (this.animal.onGround() && this.phaseTicks % 10 == 0)
                {
                    this.unreachableTicks = Pursuit.canWalkTo(this.animal, victim) ? 0 : Math.min(UNREACHABLE_TICKS, this.unreachableTicks + 10);
                }
                if (this.unreachableTicks < UNREACHABLE_TICKS)
                {
                    this.siegeTicks = 0;
                    this.chaseTicks++;
                    if (this.phaseTicks % 5 == 1) {this.animal.getNavigation().moveTo(victim, 2.0D);}
                }
                else if (this.besiege(level, victim)) {return;}
                if (this.biteCooldown > 0) {this.biteCooldown--;}
                else if (this.animal.isWithinMeleeAttackRange(victim) && this.animal.hasLineOfSight(victim))
                {
                    level.playSound(null, this.animal.getX(), this.animal.getY(), this.animal.getZ(), this.kind.attackSound(), SoundSource.HOSTILE, 1.0F, 1.0F);
                    if (this.bite(level, victim)) {this.setPhase(Phase.FLEE);}
                    //into their shield: it tries again, but Herobrine doesn't wait for ever
                    else if (++this.parriedBites >= PARRIED_BITES) {HerobrineApparitionEntity.replace(level, this.animal, victim);}
                    else {this.biteCooldown = PARRIED_BITE_PAUSE;}
                    return;
                }
                if (this.chaseTicks > ATTACK_TIMEOUT) {this.setPhase(Phase.FLEE);}
            }
            case FLEE ->
            {
                if (this.phaseTicks % 20 == 1 || this.animal.getNavigation().isDone())
                {
                    Vec3 away = DefaultRandomPos.getPosAway(this.animal, 16, 7, victim.position());
                    if (away != null) {this.animal.getNavigation().moveTo(away.x, away.y, away.z, 2.0D);}
                }
                boolean outOfSight = this.animal.distanceTo(victim) > 16.0D || HauntSpots.isHiddenFrom(victim, this.animal.getEyePosition());
                if ((this.phaseTicks > MIN_FLEE_TICKS && outOfSight) || this.phaseTicks > MIN_FLEE_TICKS * 4) {this.release();}
            }
        }
    }

    /**
     * Its victim out of its reach: it leaps up onto them if they are not too high, and otherwise waits below them, staring, until Herobrine
     * comes for them himself (it turns into his stalker). True once it is gone.
     */
    private boolean besiege(ServerLevel level, ServerPlayer victim)
    {
        if (this.siegeTicks == 0) {this.siegeLength = Mth.nextInt(this.animal.getRandom(), SIEGE_MIN_TICKS, SIEGE_MAX_TICKS);}
        if (++this.siegeTicks >= this.siegeLength)
        {
            HerobrineApparitionEntity.replace(level, this.animal, victim);
            return true;
        }
        if (this.leapCooldown > 0) {this.leapCooldown--;}
        else if (this.animal.onGround() && this.canLeapTo(victim))
        {
            this.leapTo(victim);
            this.leapCooldown = LEAP_COOLDOWN;
        }
        //right below them
        else if (this.phaseTicks % 20 == 1) {this.animal.getNavigation().moveTo(victim, 1.0D);}
        return false;
    }

    //(at the foot of a pillar, the edge of its top block can hide them: it knows where they are anyway)
    private boolean canLeapTo(Player victim)
    {
        double rise = victim.getY() - this.animal.getY();
        return rise > 0.5D && rise <= MAX_LEAP_HEIGHT && victim.position().subtract(this.animal.position()).horizontalDistance() <= MAX_LEAP_DISTANCE;
    }

    /** A leap no animal could make, landing a little above their feet. */
    private void leapTo(Player victim)
    {
        Vec3 way = victim.position().subtract(this.animal.position());
        //fast enough to rise that high against gravity (0.08 per tick), with some margin for the air's drag
        double up = Math.sqrt(2.0D * 0.08D * (way.y + 0.6D)) * 1.1D;
        //the air slows it down by 9% a tick: this much ground is covered by the time it is up there, for each unit of speed
        double covered = (1.0D - Math.pow(0.91D, up / 0.08D)) / 0.09D;
        Vec3 across = way.multiply(1.0D, 0.0D, 1.0D).scale(1.0D / covered);
        this.animal.getNavigation().stop();
        this.animal.setDeltaMovement(across.x, up, across.z);
        this.animal.needsSync = true;
    }

    /** True if it bit them, false if a shield held up against it stopped it. */
    private boolean bite(ServerLevel level, Player victim)
    {
        float damage = biteDamage(victim.getArmorValue());
        //its bite goes through armor, which would take it through shields too: the shield gets an ordinary blow, which it stops (and wears out from)
        if (Players.isShieldedFrom(victim, this.animal))
        {
            DamageSource blow = this.animal.damageSources().mobAttack(this.animal);
            victim.hurtServer(level, blow, damage);
            //it bounces off (its path is over: back where it started, the same path again would count as already walked)
            this.animal.getNavigation().stop();
            this.animal.knockback(0.5D, victim.getX() - this.animal.getX(), victim.getZ() - this.animal.getZ(), blow, 0.0F);
            return false;
        }
        DamageSource bite = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BITE), this.animal);
        if (victim.hurtServer(level, bite, damage))
        {
            //the cow's charge throws them back
            double knockback = this.animal.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
            float yaw = this.animal.getYRot() * Mth.DEG_TO_RAD;
            if (knockback > 0.0D) {victim.knockback(knockback * 0.5D, Mth.sin(yaw), -Mth.cos(yaw), bite, damage);}
        }
        this.animal.setLastHurtMob(victim);
        return true;
    }

    /** Head and body turned towards the victim. */
    private void stareAt(Player victim)
    {
        this.animal.getLookControl().setLookAt(victim, 30.0F, 90.0F);
        if (this.animal.getNavigation().isDone())
        {
            float targetYaw = Facing.yawTowards(victim.getX() - this.animal.getX(), victim.getZ() - this.animal.getZ());
            this.animal.setYBodyRot(Mth.approachDegrees(this.animal.yBodyRot, targetYaw, 6.0F));
        }
    }

    private void setPhase(Phase phase)
    {
        this.phase = phase;
        this.phaseTicks = 0;
        this.chaseTicks = 0;
        this.unreachableTicks = 0;
        this.siegeTicks = 0;
        this.parriedBites = 0;
        this.biteCooldown = 0;
        this.animal.getNavigation().stop();
    }

    /** Herobrine lets go: a perfectly normal animal again. */
    private void release()
    {
        if (!this.animal.isRemoved()) {this.kind.release(this.animal);}
    }

    /** Hitting it makes it charge whoever hit it. Returns whether it was hurt. */
    public boolean onHurt(DamageSource source, boolean hurt)
    {
        if (hurt && this.animal.isAlive() && source.getEntity() instanceof Player player) {this.provoke(player);}
        return hurt;
    }

    /** Shearing, milking or saddling it is not a good idea either. Returns the interaction result. */
    public InteractionResult onInteract(Player player, InteractionResult result)
    {
        if (result.consumesAction() && !this.animal.level().isClientSide()) {this.provoke(player);}
        return result;
    }

    private void provoke(Player player)
    {
        if (this.phase != Phase.STARE) {return;}
        this.victimId = player.getUUID();
        this.setPhase(Phase.ATTACK);
    }

    public void save(ValueOutput output)
    {
        output.putString("Phase", this.phase.name());
        output.putInt("PhaseTicks", this.phaseTicks);
        output.putInt("CloseStareTicks", this.closeStareTicks);
        output.storeNullable("Victim", UUIDUtil.CODEC, this.victimId);
    }

    public void load(ValueInput input)
    {
        try {this.phase = Phase.valueOf(input.getStringOr("Phase", Phase.STARE.name()));}
        catch (IllegalArgumentException e) {this.phase = Phase.STARE;}
        this.phaseTicks = input.getIntOr("PhaseTicks", 0);
        this.closeStareTicks = input.getIntOr("CloseStareTicks", 0);
        this.victimId = input.read("Victim", UUIDUtil.CODEC).orElse(null);
    }
}
