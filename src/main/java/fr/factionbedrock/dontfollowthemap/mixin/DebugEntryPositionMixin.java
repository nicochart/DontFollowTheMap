package fr.factionbedrock.dontfollowthemap.mixin;

import fr.factionbedrock.dontfollowthemap.client.ScrambledCoordinates;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Collection;

/** The debug screen's coordinates lie while something of Herobrine's is around (client/ScrambledCoordinates). */
@Mixin(DebugEntryPosition.class)
public abstract class DebugEntryPositionMixin
{
    @ModifyArg(method = "display", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;addToGroup(Lnet/minecraft/resources/Identifier;Ljava/util/Collection;)V"), index = 1)
    private Collection<String> dontfollowthemap$scramble(Collection<String> lines)
    {
        return ScrambledCoordinates.isActive() ? ScrambledCoordinates.scramble(lines) : lines;
    }
}
