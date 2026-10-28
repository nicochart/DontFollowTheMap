package fr.factionbedrock.dontfollowthemap.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayList;
import java.util.List;

/** Writes waxed (non editable) sign text, both in the world and during world generation. */
public class SignWriter
{
    public static void write(SignBlockEntity sign, HolderLookup.Provider registries, List<Component> lines, boolean glowing)
    {
        List<Component> messages = new ArrayList<>(lines);
        while (messages.size() < 4) {messages.add(Component.empty());}
        SignText text = new SignText(messages, messages, DyeColor.BLACK, glowing);
        if (sign.getLevel() != null)
        {
            sign.setText(text, net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
            sign.setWaxed(true);
        }
        else
        {
            //block entities of chunks being generated have no level yet, and setText would try to notify it
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            output.store("front_text", SignText.CODEC, text);
            output.putBoolean("is_waxed", true);
            sign.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
        }
    }

    /** Centers the given lines vertically on the sign. */
    public static List<Component> centered(List<Component> lines)
    {
        List<Component> result = new ArrayList<>();
        if (lines.size() <= 2) {result.add(Component.empty());}
        result.addAll(lines);
        return result;
    }
}
