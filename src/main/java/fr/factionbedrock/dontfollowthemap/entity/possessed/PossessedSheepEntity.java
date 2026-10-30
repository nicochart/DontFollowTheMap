package fr.factionbedrock.dontfollowthemap.entity.possessed;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A sheep Herobrine took over (see {@link Possession}). Shearing it is not a good idea. */
public class PossessedSheepEntity extends Sheep implements PossessedAnimal
{
    private final Possession possession = new Possession(this, PossessableAnimals.SHEEP);

    public PossessedSheepEntity(EntityType<? extends PossessedSheepEntity> type, Level level) {super(type, level);}

    public static AttributeSupplier.Builder createAttributes() {return Possession.attributes(Sheep.createAttributes());}

    @Override public Possession getPossession() {return this.possession;}

    //the sheep goals are created first: Sheep itself needs its grass eating one
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
