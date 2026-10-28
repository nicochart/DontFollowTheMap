package fr.factionbedrock.dontfollowthemap.haunting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.factionbedrock.dontfollowthemap.registry.DFTMAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Torches temporarily turned into redstone torches in a level. Saved with the level so that a torch is always put back,
 * even if the game was closed in between (the swap is reverted as soon as its chunk is loaded again).
 */
public class TorchSwaps
{
    public record Swap(BlockPos pos, BlockState original, long revertAt)
    {
        public static final Codec<Swap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Swap::pos),
                BlockState.CODEC.fieldOf("original").forGetter(Swap::original),
                Codec.LONG.fieldOf("revert_at").forGetter(Swap::revertAt)
        ).apply(instance, Swap::new));
    }

    public static final Codec<TorchSwaps> CODEC = Swap.CODEC.listOf().xmap(TorchSwaps::new, swaps -> swaps.swaps);

    private final List<Swap> swaps;

    public TorchSwaps() {this(List.of());}

    private TorchSwaps(List<Swap> swaps) {this.swaps = new ArrayList<>(swaps);}

    public static TorchSwaps get(ServerLevel level) {return level.getAttachedOrCreate(DFTMAttachments.TORCH_SWAPS);}

    public boolean isEmpty() {return this.swaps.isEmpty();}

    public boolean contains(BlockPos pos)
    {
        for (Swap swap : this.swaps) {if (swap.pos.equals(pos)) {return true;}}
        return false;
    }

    public void add(Swap swap) {this.swaps.add(swap);}

    /** Puts back the torches whose time has come. Swaps in unloaded chunks wait for their chunk. */
    public void tick(ServerLevel level, long now)
    {
        Iterator<Swap> iterator = this.swaps.iterator();
        while (iterator.hasNext())
        {
            Swap swap = iterator.next();
            if (now < swap.revertAt || !level.isLoaded(swap.pos)) {continue;}
            BlockState current = level.getBlockState(swap.pos);
            //if the player broke or replaced the redstone torch meanwhile, there is nothing to put back
            if (current.is(Blocks.REDSTONE_TORCH) || current.is(Blocks.REDSTONE_WALL_TORCH)) {level.setBlock(swap.pos, swap.original, Block.UPDATE_ALL);}
            iterator.remove();
        }
    }
}
