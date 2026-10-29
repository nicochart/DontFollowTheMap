package fr.factionbedrock.dontfollowthemap.network;

import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Tells a player's client whether they are haunted (past the quiet stage, and not out of reach like a creative player): it plays tricks on them then. */
public record HauntedPayload(boolean haunted) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<HauntedPayload> TYPE = new CustomPacketPayload.Type<>(DontFollowTheMap.id("haunted"));
    public static final StreamCodec<ByteBuf, HauntedPayload> CODEC = ByteBufCodecs.BOOL.map(HauntedPayload::new, HauntedPayload::haunted);

    @Override public Type<HauntedPayload> type() {return TYPE;}
}
