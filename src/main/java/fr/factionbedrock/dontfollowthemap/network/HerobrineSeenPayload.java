package fr.factionbedrock.dontfollowthemap.network;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent by a client when its player starts or stops seeing, on their screen, an apparition that is after them (client/HerobrineSight):
 * the server can't tell by itself, it doesn't know their field of view. The stalker runs at them once seen.
 */
public record HerobrineSeenPayload(int entityId, boolean seen) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<HerobrineSeenPayload> TYPE = new CustomPacketPayload.Type<>(DontFollowTheMap.id("herobrine_seen"));
    public static final StreamCodec<ByteBuf, HerobrineSeenPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, HerobrineSeenPayload::entityId, ByteBufCodecs.BOOL, HerobrineSeenPayload::seen, HerobrineSeenPayload::new);

    @Override public Type<HerobrineSeenPayload> type() {return TYPE;}
}
