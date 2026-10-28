package fr.factionbedrock.dontfollowthemap.haunting;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs short server side sequences (a few seconds at most) without spawning entities or threads.
 * Not saved: a sequence interrupted by a server stop is simply dropped.
 */
public class DelayedTasks
{
    private record Task(long runAt, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long tick;

    public static void schedule(int delayTicks, Runnable action) {TASKS.add(new Task(tick + Math.max(1, delayTicks), action));}

    public static void tick(MinecraftServer server)
    {
        tick++;
        if (TASKS.isEmpty()) {return;}
        //tasks may schedule other tasks, so the due ones are collected first
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(task -> {if (task.runAt <= tick) {due.add(task); return true;} return false;});
        for (Task task : due) {task.action.run();}
    }

    public static void clear() {TASKS.clear();}
}
