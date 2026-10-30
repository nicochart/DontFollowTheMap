package fr.factionbedrock.dontfollowthemap.haunting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import fr.factionbedrock.dontfollowthemap.registry.DFTMAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per player haunting progression, saved with the player and kept on death.
 * Times are overworld game times (see {@link HauntingDirector#now}).
 */
public class HauntingData
{
    public static final long DAY = 24000L;
    public static final int MAX_STOLEN_STACKS = 54;

    //stages
    public static final int QUIET = 0, WATCHED = 1, HAUNTED = 2, HUNTED = 3;
    //seal states
    public static final int SEALED = 0, FREED = 1;

    public static final Codec<HauntingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("haunt_ticks", 0L).forGetter(data -> data.hauntTicks),
            Codec.LONG.optionalFieldOf("next_event", 0L).forGetter(data -> data.nextEventTime),
            Codec.LONG.optionalFieldOf("next_whisper", 0L).forGetter(data -> data.nextWhisperTime),
            //lenient: a reaction removed by an update only forgets the cooldowns, not the rest
            Codec.unboundedMap(Reaction.CODEC, Codec.LONG).lenientOptionalFieldOf("last_reactions", Map.of()).forGetter(data -> data.lastReactions),
            Codec.LONG.optionalFieldOf("map_delivered", -1L).forGetter(data -> data.mapDeliveredAt),
            Codec.LONG.optionalFieldOf("peace_until", 0L).forGetter(data -> data.peaceUntil),
            Codec.INT.optionalFieldOf("sightings", 0).forGetter(data -> data.sightings),
            Codec.INT.optionalFieldOf("seal_state", SEALED).forGetter(data -> data.sealState),
            Codec.INT.optionalFieldOf("defeats", 0).forGetter(data -> data.defeats),
            Codec.INT.optionalFieldOf("world_defeats", 0).forGetter(data -> data.worldDefeats),
            ItemStack.CODEC.listOf().optionalFieldOf("stolen", List.of()).forGetter(data -> data.stolen)
    ).apply(instance, HauntingData::new));

    public long hauntTicks;
    public long nextEventTime;
    public long nextWhisperTime;
    /** When each reaction last happened to this player. */
    private final Map<Reaction, Long> lastReactions = new EnumMap<>(Reaction.class);
    public long mapDeliveredAt;
    public long peaceUntil;
    public int sightings;
    public int sealState;
    /** Herobrine's defeats this player took part in. */
    public int defeats;
    /** Herobrine's defeats on the server this player got the peace of (see {@link WorldPeace}). */
    public int worldDefeats;
    public final List<ItemStack> stolen;

    public HauntingData() {this(0L, 0L, 0L, Map.of(), -1L, 0L, 0, SEALED, 0, 0, List.of());}

    private HauntingData(long hauntTicks, long nextEventTime, long nextWhisperTime, Map<Reaction, Long> lastReactions, long mapDeliveredAt, long peaceUntil, int sightings, int sealState, int defeats, int worldDefeats, List<ItemStack> stolen)
    {
        this.hauntTicks = hauntTicks;
        this.nextEventTime = nextEventTime;
        this.nextWhisperTime = nextWhisperTime;
        this.lastReactions.putAll(lastReactions);
        this.mapDeliveredAt = mapDeliveredAt;
        this.peaceUntil = peaceUntil;
        this.sightings = sightings;
        this.sealState = sealState;
        this.defeats = defeats;
        this.worldDefeats = worldDefeats;
        this.stolen = new ArrayList<>(stolen);
    }

    public static HauntingData get(ServerPlayer player) {return player.getAttachedOrCreate(DFTMAttachments.HAUNTING);}

    public int stage(long now)
    {
        if (this.peaceUntil > now) {return QUIET;}
        DFTMConfig config = DFTMConfig.get();
        double days = (double) this.hauntTicks / DAY;
        if (days < config.graceDays) {return QUIET;}
        else if (days < config.mapDays) {return WATCHED;}
        else if (this.sealState == FREED || days >= config.mapDays + config.escalationDays) {return HUNTED;}
        else {return HAUNTED;}
    }

    /** Sets the progression to the beginning of a stage (used by the command). */
    public void setStage(int stage, long now)
    {
        DFTMConfig config = DFTMConfig.get();
        this.peaceUntil = 0L;
        this.nextEventTime = now + 100L;
        this.nextWhisperTime = 0L;
        double days = switch (stage)
        {
            case WATCHED -> config.graceDays;
            case HAUNTED -> config.mapDays;
            case HUNTED -> config.mapDays + config.escalationDays;
            default -> 0.0D;
        };
        this.hauntTicks = (long) Math.ceil(days * DAY);
        if (stage < HAUNTED) {this.mapDeliveredAt = -1L;}
    }

    /** Whether this reaction happened to the player too recently to happen again. */
    public boolean isOnCooldown(Reaction reaction, long now)
    {
        Long last = this.lastReactions.get(reaction);
        return last != null && now - last < reaction.cooldown();
    }

    /** This reaction happened now: it can't happen again before its cooldown is over. */
    public void startCooldown(Reaction reaction, long now) {this.lastReactions.put(reaction, now);}

    public boolean hasMapBeenDelivered() {return this.mapDeliveredAt >= 0L;}

    public void addStolen(ItemStack stack)
    {
        if (stack.isEmpty()) {return;}
        for (ItemStack stolenStack : this.stolen)
        {
            if (ItemStack.isSameItemSameComponents(stolenStack, stack) && stolenStack.getCount() < stolenStack.getMaxStackSize())
            {
                int moved = Math.min(stack.getCount(), stolenStack.getMaxStackSize() - stolenStack.getCount());
                stolenStack.grow(moved);
                stack.shrink(moved);
                if (stack.isEmpty()) {return;}
            }
        }
        if (this.stolen.size() < MAX_STOLEN_STACKS) {this.stolen.add(stack.copy());}
    }

    /** Takes back (part of) a stack that had been stolen. */
    public void removeStolen(ItemStack stack)
    {
        int remaining = stack.getCount();
        for (int i = this.stolen.size() - 1; i >= 0 && remaining > 0; i--)
        {
            ItemStack stolenStack = this.stolen.get(i);
            if (!ItemStack.isSameItemSameComponents(stolenStack, stack)) {continue;}
            int removed = Math.min(remaining, stolenStack.getCount());
            stolenStack.shrink(removed);
            remaining -= removed;
            if (stolenStack.isEmpty()) {this.stolen.remove(i);}
        }
    }

    /** Called when the player defeated Herobrine (they were there). */
    public void onHerobrineDefeated(long now)
    {
        this.defeats++;
        this.makePeace(now);
    }

    /**
     * Herobrine was defeated at that time (by this player or anyone else): everything calms down for a while, then the haunting starts over.
     * For a player who comes back after the peace is over, only the starting over is left.
     */
    public void makePeace(long defeatedAt)
    {
        DFTMConfig config = DFTMConfig.get();
        this.sealState = SEALED;
        this.mapDeliveredAt = -1L;
        this.peaceUntil = defeatedAt + (long) (config.peaceDaysAfterDefeat * DAY);
        //back to the end of the grace period (a player still in it stays where they are)
        this.hauntTicks = Math.min(this.hauntTicks, (long) Math.ceil(config.graceDays * DAY));
        this.nextEventTime = 0L;
        this.nextWhisperTime = 0L;
    }
}
