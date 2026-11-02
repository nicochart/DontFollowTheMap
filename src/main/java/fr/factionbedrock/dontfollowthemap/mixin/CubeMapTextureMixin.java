package fr.factionbedrock.dontfollowthemap.mixin;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The title screen's panorama: the mod's own (assets/dontfollowthemap/textures/gui/title/background), unless turned off in the config
 * (DFTMConfig.customTitleScreenPanorama, read when the textures are loaded: a resource reload or a restart applies a change).
 */
@Mixin(CubeMapTexture.class)
public class CubeMapTextureMixin
{
    private static final String PANORAMA = "textures/gui/title/background/panorama";

    @ModifyVariable(method = "loadContents", at = @At("STORE"), name = "location")
    private Identifier dontfollowthemap$hauntedPanorama(Identifier location)
    {
        if (DFTMConfig.get().customTitleScreenPanorama && location.equals(Identifier.withDefaultNamespace(PANORAMA))) {return DontFollowTheMap.id(PANORAMA);}
        return location;
    }
}
