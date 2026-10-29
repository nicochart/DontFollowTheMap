package fr.factionbedrock.dontfollowthemap.network;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent to the players around a seal as Herobrine's summoning starts (he appears a few seconds later):
 * their fight music starts right away, so that it has risen by the time he is there.
 */
public record FightStartingPayload(BlockPos seal) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<FightStartingPayload> TYPE = new CustomPacketPayload.Type<>(DontFollowTheMap.id("fight_starting"));
    public static final StreamCodec<ByteBuf, FightStartingPayload> CODEC = BlockPos.STREAM_CODEC.map(FightStartingPayload::new, FightStartingPayload::seal);

    @Override public Type<FightStartingPayload> type() {return TYPE;}
}
