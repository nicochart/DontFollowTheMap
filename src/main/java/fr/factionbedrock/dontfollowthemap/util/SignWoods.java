package fr.factionbedrock.dontfollowthemap.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** What the signs he leaves are made of: mostly the wood that grows around, like the player's own signs would be. */
public class SignWoods
{
    /** The signs of one wood: standing, and on a wall. */
    public record Wood(Block standing, Block wall)
    {
        public Block sign(boolean onWall) {return onWall ? this.wall : this.standing;}
    }

    public static final Wood OAK = new Wood(Blocks.OAK_SIGN, Blocks.OAK_WALL_SIGN), SPRUCE = new Wood(Blocks.SPRUCE_SIGN, Blocks.SPRUCE_WALL_SIGN),
            BIRCH = new Wood(Blocks.BIRCH_SIGN, Blocks.BIRCH_WALL_SIGN), JUNGLE = new Wood(Blocks.JUNGLE_SIGN, Blocks.JUNGLE_WALL_SIGN),
            ACACIA = new Wood(Blocks.ACACIA_SIGN, Blocks.ACACIA_WALL_SIGN), DARK_OAK = new Wood(Blocks.DARK_OAK_SIGN, Blocks.DARK_OAK_WALL_SIGN),
            PALE_OAK = new Wood(Blocks.PALE_OAK_SIGN, Blocks.PALE_OAK_WALL_SIGN), MANGROVE = new Wood(Blocks.MANGROVE_SIGN, Blocks.MANGROVE_WALL_SIGN),
            CHERRY = new Wood(Blocks.CHERRY_SIGN, Blocks.CHERRY_WALL_SIGN), POPLAR = new Wood(Blocks.POPLAR_SIGN, Blocks.POPLAR_WALL_SIGN),
            BAMBOO = new Wood(Blocks.BAMBOO_SIGN, Blocks.BAMBOO_WALL_SIGN), CRIMSON = new Wood(Blocks.CRIMSON_SIGN, Blocks.CRIMSON_WALL_SIGN),
            WARPED = new Wood(Blocks.WARPED_SIGN, Blocks.WARPED_WALL_SIGN);
    /** The woods a player may have brought from anywhere in the Overworld. */
    private static final List<Wood> OVERWORLD_WOODS = List.of(OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, PALE_OAK, MANGROVE, CHERRY, POPLAR, BAMBOO);

    /** A sign of the local wood, or once in five times of any wood of the Overworld. */
    public static Block localSign(ServerLevel level, BlockPos pos, RandomSource random, boolean onWall)
    {
        Wood wood = random.nextInt(5) == 0 ? OVERWORLD_WOODS.get(random.nextInt(OVERWORLD_WOODS.size())) : localWood(level.getBiome(pos), random);
        return wood.sign(onWall);
    }

    /** The wood that grows in that biome: oak where no tree grows. */
    public static Wood localWood(Holder<Biome> biome, RandomSource random)
    {
        if (biome.is(Biomes.CHERRY_GROVE)) {return CHERRY;}
        if (biome.is(Biomes.MANGROVE_SWAMP)) {return MANGROVE;}
        if (biome.is(Biomes.PALE_GARDEN)) {return PALE_OAK;}
        if (biome.is(Biomes.DARK_FOREST)) {return DARK_OAK;}
        if (biome.is(Biomes.DAPPLED_FOREST)) {return POPLAR;}
        if (biome.is(Biomes.BAMBOO_JUNGLE)) {return random.nextBoolean() ? BAMBOO : JUNGLE;}
        if (biome.is(BiomeTags.IS_JUNGLE)) {return JUNGLE;}
        if (biome.is(BiomeTags.IS_SAVANNA)) {return ACACIA;}
        if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {return BIRCH;}
        if (biome.is(Biomes.FOREST) || biome.is(Biomes.FLOWER_FOREST)) {return random.nextInt(3) == 0 ? BIRCH : OAK;}
        //the taigas, the snowy lands and the windswept ones
        if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.SPAWNS_SNOW_FOXES) || biome.is(Biomes.WINDSWEPT_HILLS) || biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS) || biome.is(Biomes.WINDSWEPT_FOREST)) {return SPRUCE;}
        if (biome.is(Biomes.CRIMSON_FOREST)) {return CRIMSON;}
        if (biome.is(Biomes.WARPED_FOREST)) {return WARPED;}
        if (biome.is(BiomeTags.IS_NETHER)) {return random.nextBoolean() ? CRIMSON : WARPED;}
        return OAK;
    }
}
