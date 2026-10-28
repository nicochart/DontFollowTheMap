package fr.factionbedrock.dontfollowthemap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import fr.factionbedrock.dontfollowthemap.DontFollowTheMap;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server side settings, read from config/dontfollowthemap.json (created with the default values on first launch).
 * Durations are in Minecraft days (20 minutes of play each). Only time spent in the Overworld counts.
 */
public class DFTMConfig
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DFTMConfig INSTANCE = new DFTMConfig();

    //global
    public boolean enabled = true;
    public boolean hauntCreativePlayers = false;
    public double eventFrequency = 1.0D;

    //progression
    public double graceDays = 1.0D;
    public double mapDays = 3.0D;
    public double escalationDays = 3.0D;
    public double peaceDaysAfterDefeat = 10.0D;

    //events
    public boolean sightings = true;
    public boolean killApparitions = true;
    public double killApparitionChance = 0.06D;
    public boolean bedScares = true;
    public double bedScareChance = 0.15D;
    public boolean torchSwaps = true;
    public boolean signs = true;
    public boolean builds = true;
    public boolean possessedSheep = true;
    public boolean possessedCows = true;
    public boolean possessedPigs = true;
    public boolean possessedChickens = true;
    public boolean thief = true;
    public double thiefChance = 0.03D;
    public boolean graves = true;
    public double graveChance = 0.15D;

    public static DFTMConfig get() {return INSTANCE;}

    public static void load()
    {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(DontFollowTheMap.MODID + ".json");
        if (Files.exists(path))
        {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
            {
                DFTMConfig loaded = GSON.fromJson(reader, DFTMConfig.class);
                if (loaded != null) {INSTANCE = loaded.sanitized();}
            }
            catch (IOException | JsonParseException e)
            {
                DontFollowTheMap.LOGGER.error("Could not read {}, using the default settings", path, e);
                return;
            }
        }
        //(re)write the file so that options added by an update appear in it
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {GSON.toJson(INSTANCE, writer);}
        catch (IOException e) {DontFollowTheMap.LOGGER.error("Could not write {}", path, e);}
    }

    private DFTMConfig sanitized()
    {
        this.eventFrequency = Math.clamp(this.eventFrequency, 0.1D, 10.0D);
        this.graceDays = Math.max(0.0D, this.graceDays);
        this.mapDays = Math.max(this.graceDays, this.mapDays);
        this.escalationDays = Math.max(0.0D, this.escalationDays);
        this.peaceDaysAfterDefeat = Math.max(0.0D, this.peaceDaysAfterDefeat);
        this.killApparitionChance = Math.clamp(this.killApparitionChance, 0.0D, 1.0D);
        this.bedScareChance = Math.clamp(this.bedScareChance, 0.0D, 1.0D);
        this.thiefChance = Math.clamp(this.thiefChance, 0.0D, 1.0D);
        this.graveChance = Math.clamp(this.graveChance, 0.0D, 1.0D);
        return this;
    }
}
