package fr.factionbedrock.dontfollowthemap.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.factionbedrock.dontfollowthemap.entity.possessed.PossessableAnimals;
import fr.factionbedrock.dontfollowthemap.haunting.GraveDigger;
import fr.factionbedrock.dontfollowthemap.haunting.HauntEvents;
import fr.factionbedrock.dontfollowthemap.haunting.HauntSpots;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingData;
import fr.factionbedrock.dontfollowthemap.haunting.HauntingDirector;
import fr.factionbedrock.dontfollowthemap.haunting.SealEvents;
import fr.factionbedrock.dontfollowthemap.haunting.WorldPeace;
import fr.factionbedrock.dontfollowthemap.world.SealShrines;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * /dontfollowthemap (operators only): status [player], stage <player> <0-3>, trigger <event> [player],
 * trigger possession [entity] [player], trigger whisper [sound] [player], reset <player>, locate-seal, generate-seal [position].
 */
public class DFTMCommand
{
    private static final List<String> EXTRA_EVENTS = List.of("map", "grave", "thief", "tunnel");
    private static final DynamicCommandExceptionType UNKNOWN_EVENT = new DynamicCommandExceptionType(name -> Component.translatable("commands.dontfollowthemap.unknown_event", name));
    private static final DynamicCommandExceptionType UNKNOWN_WHISPER = new DynamicCommandExceptionType(name -> Component.translatable("commands.dontfollowthemap.unknown_whisper", name));
    private static final Dynamic2CommandExceptionType NOT_POSSESSABLE = new Dynamic2CommandExceptionType((type, possessable) -> Component.translatable("commands.dontfollowthemap.not_possessable", type, possessable));
    private static final SimpleCommandExceptionType NO_SEAL = new SimpleCommandExceptionType(Component.translatable("commands.dontfollowthemap.locate_seal.none"));
    private static final SimpleCommandExceptionType NO_ROOM_FOR_SEAL = new SimpleCommandExceptionType(Component.translatable("commands.dontfollowthemap.generate_seal.no_room"));
    private static final SimpleCommandExceptionType SEAL_IN_OVERWORLD_ONLY = new SimpleCommandExceptionType(Component.translatable("commands.dontfollowthemap.generate_seal.overworld"));

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> register(dispatcher, buildContext));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext)
    {
        dispatcher.register(Commands.literal("dontfollowthemap").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                        .executes(context -> status(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> status(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("stage")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stage", IntegerArgumentType.integer(HauntingData.QUIET, HauntingData.HUNTED))
                                        .executes(context -> setStage(context, EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "stage"))))))
                .then(Commands.literal("trigger")
                        //an animal of the given type around the player, or any possessable one when no type is given
                        .then(Commands.literal("possession")
                                .executes(context -> triggerPossession(context, PossessableAnimals.ALL, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE)).suggests(DFTMCommand::suggestPossessableAnimals)
                                        .executes(context -> triggerPossession(context, List.of(possessableAnimal(context)), context.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> triggerPossession(context, List.of(possessableAnimal(context)), EntityArgument.getPlayer(context, "player"))))))
                        //a whisper of the given sound, or of any of them
                        .then(Commands.literal("whisper")
                                .executes(context -> triggerWhisper(context, null, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("sound", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(whisperNames(), builder))
                                        .executes(context -> triggerWhisper(context, whisperSound(context), context.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> triggerWhisper(context, whisperSound(context), EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.argument("event", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(eventNames(), builder))
                                .executes(context -> trigger(context, context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "event")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> trigger(context, EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "event"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> reset(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("locate-seal").executes(DFTMCommand::locateSeal))
                //a new seal shrine replaces the current one: somewhere around (built once its chunks are loaded), or right here, now
                .then(Commands.literal("generate-seal")
                        .executes(context -> generateSeal(context, null))
                        .then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context -> generateSeal(context, BlockPosArgument.getBlockPos(context, "position"))))));
    }

    private static int locateSeal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        SealShrines.Shrine shrine = SealShrines.get(context.getSource().getServer()).active();
        if (shrine == null) {throw NO_SEAL.create();}
        return showSeal(context, shrine, "commands.dontfollowthemap.locate_seal." + shrine.stage().getSerializedName());
    }

    private static int generateSeal(CommandContext<CommandSourceStack> context, @Nullable BlockPos position) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerLevel overworld = source.getServer().overworld();
        if (source.getLevel() != overworld) {throw SEAL_IN_OVERWORLD_ONLY.create();}
        SealShrines.Shrine shrine;
        if (position == null)
        {
            shrine = SealShrines.plan(overworld, BlockPos.containing(source.getPosition()));
            if (shrine == null) {throw NO_ROOM_FOR_SEAL.create();}
        }
        else
        {
            if (!SealShrines.isLoaded(overworld, position)) {throw BlockPosArgument.ERROR_NOT_LOADED.create();}
            shrine = SealShrines.buildAt(overworld, position);
        }
        return showSeal(context, shrine, "commands.dontfollowthemap.generate_seal." + shrine.stage().getSerializedName());
    }

    /** Where it is, like /locate does: coordinates to click (to teleport there) and the distance. */
    private static int showSeal(CommandContext<CommandSourceStack> context, SealShrines.Shrine shrine, String key)
    {
        BlockPos center = shrine.center();
        String y = shrine.stage() == SealShrines.Stage.BUILT ? String.valueOf(center.getY()) : "~";
        Vec3 from = context.getSource().getPosition();
        int distance = Mth.floor(Math.sqrt(Mth.square(center.getX() + 0.5D - from.x) + Mth.square(center.getZ() + 0.5D - from.z)));
        Component coordinates = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", center.getX(), y, center.getZ()))
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + center.getX() + " " + y + " " + center.getZ()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
        context.getSource().sendSuccess(() -> Component.translatable(key, coordinates, distance), false);
        return distance;
    }

    /** The events triggered by name: the scheduled ones (except possession, which has its own syntax), then the others. */
    private static List<String> eventNames()
    {
        List<String> names = new ArrayList<>();
        for (HauntingDirector.Event event : HauntingDirector.Event.values()) {if (event != HauntingDirector.Event.POSSESSION) {names.add(event.name().toLowerCase(Locale.ROOT));}}
        names.addAll(EXTRA_EVENTS);
        return names;
    }

    private static List<String> whisperNames()
    {
        List<String> names = new ArrayList<>();
        for (HauntEvents.Whisper whisper : HauntEvents.Whisper.values()) {names.add(whisper.name().toLowerCase(Locale.ROOT));}
        return names;
    }

    private static HauntEvents.Whisper whisperSound(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        String name = StringArgumentType.getString(context, "sound");
        try {return HauntEvents.Whisper.valueOf(name.toUpperCase(Locale.ROOT));}
        catch (IllegalArgumentException e) {throw UNKNOWN_WHISPER.create(name);}
    }

    private static int triggerWhisper(CommandContext<CommandSourceStack> context, @Nullable HauntEvents.Whisper whisper, ServerPlayer player)
    {
        if (whisper == null) {HauntEvents.whisper(player);}
        else {HauntEvents.whisper(player, whisper);}
        String name = whisper == null ? "whisper" : "whisper " + whisper.name().toLowerCase(Locale.ROOT);
        context.getSource().sendSuccess(() -> Component.translatable("commands.dontfollowthemap.trigger.success", name, player.getDisplayName()), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player)
    {
        HauntingData data = HauntingData.get(player);
        long now = HauntingDirector.now(context.getSource().getServer());
        long nextEventSeconds = Math.max(0L, data.nextEventTime - now) / 20L;
        long nextWhisperSeconds = Math.max(0L, data.nextWhisperTime - now) / 20L;
        String days = String.format(Locale.ROOT, "%.2f", (double) data.hauntTicks / HauntingData.DAY);
        context.getSource().sendSuccess(() -> Component.translatable("commands.dontfollowthemap.status", player.getDisplayName(),
                Component.translatable("stage.dontfollowthemap." + data.stage(now)), days, data.sightings, data.hasMapBeenDelivered(), data.defeats, data.stolen.size(), nextEventSeconds, nextWhisperSeconds), false);
        return data.stage(now);
    }

    private static int setStage(CommandContext<CommandSourceStack> context, ServerPlayer player, int stage)
    {
        HauntingData.get(player).setStage(stage, HauntingDirector.now(context.getSource().getServer()));
        context.getSource().sendSuccess(() -> Component.translatable("commands.dontfollowthemap.stage", player.getDisplayName(), Component.translatable("stage.dontfollowthemap." + stage)), true);
        return stage;
    }

    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer player)
    {
        HauntingData fresh = new HauntingData();
        //a fresh start, not the peace of the last defeat
        fresh.worldDefeats = WorldPeace.get(context.getSource().getServer()).defeats();
        player.setAttached(fr.factionbedrock.dontfollowthemap.registry.DFTMAttachments.HAUNTING, fresh);
        context.getSource().sendSuccess(() -> Component.translatable("commands.dontfollowthemap.reset", player.getDisplayName()), true);
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> context, ServerPlayer player, String name) throws CommandSyntaxException
    {
        int stage = Math.max(HauntingData.WATCHED, HauntingData.get(player).stage(HauntingDirector.now(context.getSource().getServer())));
        boolean happened;
        switch (name)
        {
            case "map" -> happened = SealEvents.slipMapIntoPocket(player);
            case "grave" ->
            {
                BlockPos home = HauntSpots.findHome(player);
                BlockPos grave = GraveDigger.dig(player.level(), home != null ? home : player.blockPosition(), player);
                if (grave != null) {player.teleportTo(grave.getX() + 0.5D, grave.getY(), grave.getZ() + 0.5D);}
                happened = grave != null;
            }
            case "thief" ->
            {
                //it can only come for drops: it waits for the next block the player breaks
                HauntingDirector.forceNextTheft(player);
                context.getSource().sendSuccess(() -> Component.translatable("commands.dontfollowthemap.trigger.thief", player.getDisplayName()), true);
                return 1;
            }
            case "tunnel" ->
            {
                //the tunnel the player stands in, whether they dug it or not: the wall goes up behind them
                if (!HauntEvents.wallUpTunnel(player, player.getDirection()))
                {
                    context.getSource().sendFailure(Component.translatable("commands.dontfollowthemap.trigger.no_tunnel", player.getDisplayName()));
                    return 0;
                }
                happened = true;
            }
            default ->
            {
                HauntingDirector.Event event;
                try {event = HauntingDirector.Event.valueOf(name.toUpperCase(Locale.ROOT));}
                catch (IllegalArgumentException e) {throw UNKNOWN_EVENT.create(name);}
                happened = HauntingDirector.run(player, event, stage);
            }
        }
        if (happened) {context.getSource().sendSuccess(() -> Component.translatable("commands.dontfollowthemap.trigger.success", name, player.getDisplayName()), true);}
        else {context.getSource().sendFailure(Component.translatable("commands.dontfollowthemap.trigger.failure", name, player.getDisplayName()));}
        return happened ? 1 : 0;
    }

    private static int triggerPossession(CommandContext<CommandSourceStack> context, List<PossessableAnimals.Kind<?>> animals, ServerPlayer player)
    {
        Animal possessed = HauntEvents.possessAnimal(player, animals);
        if (possessed == null)
        {
            context.getSource().sendFailure(Component.translatable("commands.dontfollowthemap.trigger.no_animal", animalNames(animals), player.getDisplayName()));
            return 0;
        }
        int distance = Mth.floor(possessed.distanceTo(player));
        context.getSource().sendSuccess(() -> Component.translatable("commands.dontfollowthemap.trigger.possession", possessed.getDisplayName(), player.getDisplayName(), distance), true);
        return 1;
    }

    /** The animal type given to the command, if Herobrine can take it over. */
    private static PossessableAnimals.Kind<?> possessableAnimal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        EntityType<?> type = ResourceArgument.getResource(context, "entity", Registries.ENTITY_TYPE).value();
        PossessableAnimals.Kind<?> kind = PossessableAnimals.byPlainType(type);
        if (kind == null) {throw NOT_POSSESSABLE.create(type.getDescription(), animalNames(PossessableAnimals.ALL));}
        return kind;
    }

    private static CompletableFuture<Suggestions> suggestPossessableAnimals(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder)
    {
        return SharedSuggestionProvider.suggestResource(PossessableAnimals.ALL.stream().map(kind -> BuiltInRegistries.ENTITY_TYPE.getKey(kind.plainType())), builder);
    }

    private static Component animalNames(List<PossessableAnimals.Kind<?>> animals)
    {
        return ComponentUtils.formatList(animals, (PossessableAnimals.Kind<?> kind) -> kind.plainType().getDescription());
    }
}
