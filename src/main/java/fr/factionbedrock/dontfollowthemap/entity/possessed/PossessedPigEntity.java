package fr.factionbedrock.dontfollowthemap.entity.possessed;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A pig Herobrine took over (see {@link Possession}). Even saddled, it won't let anyone ride it. */
public class PossessedPigEntity extends Pig implements PossessedAnimal
{
    private final Possession possession = new Possession(this, PossessableAnimals.PIG);

    public PossessedPigEntity(EntityType<? extends PossessedPigEntity> type, Level level) {super(type, level);}

    public static AttributeSupplier.Builder createAttributes() {return Possession.attributes(Pig.createAttributes());}

    @Override public Possession getPossession() {return this.possession;}

    @Override protected void registerGoals() {super.registerGoals(); Possession.replaceGoals(this, this.goalSelector);}
    @Override protected void customServerAiStep(ServerLevel level) {super.customServerAiStep(level); this.possession.tick(level);}
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {return this.possession.onHurt(source, super.hurtServer(level, source, damage));}

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        //trying to ride it counts as provoking it
        if (this.isSaddled() && !player.isSecondaryUseActive()) {return this.possession.onInteract(player, InteractionResult.SUCCESS);}
        return this.possession.onInteract(player, super.mobInteract(player, hand));
    }

    @Override public boolean isFood(ItemStack stack) {return false;}
    @Override public boolean canFallInLove() {return false;}
    @Override public boolean removeWhenFarAway(double distSqr) {return false;}
    @Override protected SoundEvent getAmbientSound() {return this.possession.ambientSound();}
    @Override public int getAmbientSoundInterval() {return Possession.AMBIENT_SOUND_INTERVAL;}

    @Override protected void addAdditionalSaveData(ValueOutput output) {super.addAdditionalSaveData(output); this.possession.save(output);}
    @Override protected void readAdditionalSaveData(ValueInput input) {super.readAdditionalSaveData(input); this.possession.load(input);}
}
