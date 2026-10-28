package fr.factionbedrock.dontfollowthemap.block;

import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.item.SealMaps;
import fr.factionbedrock.dontfollowthemap.registry.DFTMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The seal at the heart of the shrine. Showing it the map breaks it and frees Herobrine.
 * Once he fled, the open seal can call him back; once he is defeated, the seal is shattered for good.
 */
public class SealBlock extends Block
{
    public static final EnumProperty<State> STATE = EnumProperty.create("state", State.class);

    public SealBlock(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(STATE, State.SEALED));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {builder.add(STATE);}

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        if (state.getValue(STATE) != State.SEALED || !SealMaps.isSealMap(stack)) {return InteractionResult.TRY_WITH_EMPTY_HAND;}
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {SealEvents.breakSeal(serverLevel, pos, serverPlayer, stack);}
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer)
        {
            switch (state.getValue(STATE))
            {
                case SEALED ->
                {
                    serverPlayer.sendOverlayMessage(Component.translatable("block.dontfollowthemap.seal.sealed"));
                    serverLevel.playSound(null, pos, DFTMSounds.SEAL_REFUSE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                case OPEN -> SealEvents.callBack(serverLevel, pos, serverPlayer);
                case SHATTERED -> serverPlayer.sendOverlayMessage(Component.translatable("block.dontfollowthemap.seal.shattered"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        State sealState = state.getValue(STATE);
        if (sealState == State.OPEN)
        {
            for (int i = 0; i < 2; i++) {level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05D, pos.getZ() + random.nextDouble(), 0.0D, 0.04D, 0.0D);}
        }
        else if (sealState == State.SEALED && random.nextInt(4) == 0)
        {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + 1.1D, pos.getZ() + random.nextDouble(), 0.0D, 0.0D, 0.0D);
        }
    }

    public enum State implements StringRepresentable
    {
        SEALED("sealed"), OPEN("open"), SHATTERED("shattered");

        private final String name;

        State(String name) {this.name = name;}

        @Override public String getSerializedName() {return this.name;}
    }
}
