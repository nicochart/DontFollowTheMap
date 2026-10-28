package fr.factionbedrock.dontfollowthemap.haunting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.factionbedrock.dontfollowthemap.config.DFTMConfig;
import fr.factionbedrock.dontfollowthemap.registry.DFTMAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
            Codec.LONG.optionalFieldOf("last_kill_apparition", Long.MIN_VALUE).forGetter(data -> data.lastKillApparition),
            Codec.LONG.optionalFieldOf("last_bed_scare", Long.MIN_VALUE).forGetter(data -> data.lastBedScare),
            Codec.LONG.optionalFieldOf("last_grave", Long.MIN_VALUE).forGetter(data -> data.lastGrave),
            Codec.LONG.optionalFieldOf("last_theft", Long.MIN_VALUE).forGetter(data -> data.lastTheft),
            Codec.LONG.optionalFieldOf("map_delivered", -1L).forGetter(data -> data.mapDeliveredAt),
            Codec.LONG.optionalFieldOf("peace_until", 0L).forGetter(data -> data.peaceUntil),
            Codec.INT.optionalFieldOf("sightings", 0).forGetter(data -> data.sightings),
            Codec.INT.optionalFieldOf("seal_state", SEALED).forGetter(data -> data.sealState),
            Codec.INT.optionalFieldOf("defeats", 0).forGetter(data -> data.defeats),
            ItemStack.CODEC.listOf().optionalFieldOf("stolen", List.of()).forGetter(data -> data.stolen)
    ).apply(instance, HauntingData::new));

    public long hauntTicks;
    public long nextEventTime;
    public long lastKillApparition;
    public long lastBedScare;
    public long lastGrave;
    public long lastTheft;
    public long mapDeliveredAt;
    public long peaceUntil;
    public int sightings;
    public int sealState;
    public int defeats;
    public final List<ItemStack> stolen;

    public HauntingData() {this(0L, 0L, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, -1L, 0L, 0, SEALED, 0, List.of());}

    private HauntingData(long hauntTicks, long nextEventTime, long lastKillApparition, long lastBedScare, long lastGrave, long lastTheft, long mapDeliveredAt, long peaceUntil, int sightings, int sealState, int defeats, List<ItemStack> stolen)
    {
        this.hauntTicks = hauntTicks;
        this.nextEventTime = nextEventTime;
        this.lastKillApparition = lastKillApparition;
        this.lastBedScare = lastBedScare;
        this.lastGrave = lastGrave;
        this.lastTheft = lastTheft;
        this.mapDeliveredAt = mapDeliveredAt;
        this.peaceUntil = peaceUntil;
        this.sightings = sightings;
        this.sealState = sealState;
        this.defeats = defeats;
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

    public boolean isOnCooldown(long lastTime, long cooldown, long now) {return lastTime != Long.MIN_VALUE && now - lastTime < cooldown;}

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

    /** Called when the player defeated Herobrine: everything calms down for a while, then the haunting starts over. */
    public void onHerobrineDefeated(long now)
    {
        DFTMConfig config = DFTMConfig.get();
        this.defeats++;
        this.sealState = SEALED;
        this.mapDeliveredAt = -1L;
        this.peaceUntil = now + (long) (config.peaceDaysAfterDefeat * DAY);
        this.hauntTicks = (long) Math.ceil(config.graceDays * DAY);
        this.nextEventTime = 0L;
    }
}
