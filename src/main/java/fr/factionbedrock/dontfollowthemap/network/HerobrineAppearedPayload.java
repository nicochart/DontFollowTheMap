package fr.factionbedrock.dontfollowthemap.network;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent to the players around when Herobrine appears all of a sudden (on what they killed, by their bed, unmasked...): their screen jolts. */
public record HerobrineAppearedPayload() implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<HerobrineAppearedPayload> TYPE = new CustomPacketPayload.Type<>(DontFollowTheMap.id("herobrine_appeared"));
    public static final StreamCodec<ByteBuf, HerobrineAppearedPayload> CODEC = StreamCodec.unit(new HerobrineAppearedPayload());

    @Override public Type<HerobrineAppearedPayload> type() {return TYPE;}
}
