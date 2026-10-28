package fr.factionbedrock.dontfollowthemap.entity.possessed;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A cow Herobrine took over (see {@link Possession}). It charges harder than the others, and milking it is not a good idea. */
public class PossessedCowEntity extends Cow implements PossessedAnimal
{
    private final Possession possession = new Possession(this, PossessableAnimals.COW);

    public PossessedCowEntity(EntityType<? extends PossessedCowEntity> type, Level level) {super(type, level);}

    public static AttributeSupplier.Builder createAttributes() {return Possession.attributes(AbstractCow.createAttributes(), 5.0D).add(Attributes.ATTACK_KNOCKBACK, 1.0D);}

    @Override public Possession getPossession() {return this.possession;}

    @Override protected void registerGoals() {super.registerGoals(); Possession.replaceGoals(this, this.goalSelector);}
    @Override protected void customServerAiStep(ServerLevel level) {super.customServerAiStep(level); this.possession.tick(level);}
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {return this.possession.onHurt(source, super.hurtServer(level, source, damage));}
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {return this.possession.onInteract(player, super.mobInteract(player, hand));}

    @Override public boolean isFood(ItemStack stack) {return false;}
    @Override public boolean canFallInLove() {return false;}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override protected SoundEvent getAmbientSound() {return this.possession.ambientSound();}
    @Override public int getAmbientSoundInterval() {return Possession.AMBIENT_SOUND_INTERVAL;}

    @Override protected void addAdditionalSaveData(ValueOutput output) {super.addAdditionalSaveData(output); this.possession.save(output);}
    @Override protected void readAdditionalSaveData(ValueInput input) {super.readAdditionalSaveData(input); this.possession.load(input);}
}
