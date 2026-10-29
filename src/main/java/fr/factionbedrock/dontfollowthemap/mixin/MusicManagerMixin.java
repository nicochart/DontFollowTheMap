package fr.factionbedrock.dontfollowthemap.mixin;

import fr.factionbedrock.dontfollowthemap.client.HerobrineSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's music: none during Herobrine's fight, which has its own (HerobrineSounds). */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin
{
    @Shadow @Nullable private SoundInstance currentMusic;

    @Shadow public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dontfollowthemap$silenceDuringTheFight(CallbackInfo ci)
    {
        if (!HerobrineSounds.isFightMusicOn()) {return;}
        if (this.currentMusic != null) {this.stopPlaying();}
        ci.cancel();
    }
}
