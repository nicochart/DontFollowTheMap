package fr.factionbedrock.dontfollowthemap.client;

import fr.factionbedrock.dontfollowthemap.util.Facing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The hitboxes shown to a haunted player (F3 + B) include a few that belong to nothing: some stand and watch them, some wander, one keeps behind them.
 * The thief is invisible, and no hitbox is drawn for invisible things: whoever looks for it this way finds these instead.
 */
public class FakeHitboxes
{
    /** Width, height and eye height of what could be there: a player, a zombie, a cow, a chicken, a spider. */
    private static final float[][] SIZES = {{0.6F, 1.8F, 1.62F}, {0.6F, 1.95F, 1.74F}, {0.9F, 1.4F, 1.3F}, {0.4F, 0.7F, 0.644F}, {1.4F, 0.9F, 0.65F}};
    private static final int WATCHERS = 3, WANDERERS = 4;
    private static final double FOLLOW_DISTANCE = 5.0D, MAX_DISTANCE = 32.0D;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Fake> FAKES = new ArrayList<>();

    private enum Behavior {WATCH, WANDER, FOLLOW}

    private static final class Fake
    {
        private final Behavior behavior;
        private final float[] size;
        private Vec3 position = Vec3.ZERO, previous = Vec3.ZERO;
        private float yaw;
        private int life;

        private Fake(Behavior behavior) {this.behavior = behavior; this.size = SIZES[RANDOM.nextInt(SIZES.length)];}
    }

    public static int count() {return FAKES.size();}

    public static void tick(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !ClientHaunting.isHaunted() || !minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES))
        {
            FAKES.clear();
            return;
        }
        if (minecraft.isPaused()) {return;}
        if (FAKES.isEmpty())
        {
            for (int i = 0; i < WATCHERS; i++) {FAKES.add(new Fake(Behavior.WATCH));}
            for (int i = 0; i < WANDERERS; i++) {FAKES.add(new Fake(Behavior.WANDER));}
            FAKES.add(new Fake(Behavior.FOLLOW));
        }
        for (Fake fake : FAKES)
        {
            fake.previous = fake.position;
            if (--fake.life <= 0 || fake.position.distanceTo(player.position()) > MAX_DISTANCE) {place(level, player, fake);}
            else {move(level, player, fake);}
        }
    }

    /** Somewhere new around the player (out of their sight for the one behind them). */
    private static void place(ClientLevel level, LocalPlayer player, Fake fake)
    {
        Vec3 spot = null;
        for (int attempt = 0; attempt < 10 && spot == null; attempt++)
        {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D, distance = fake.behavior == Behavior.WATCH ? Mth.nextDouble(RANDOM, 10.0D, 24.0D) : Mth.nextDouble(RANDOM, 6.0D, 20.0D);
            Vec3 around = fake.behavior == Behavior.FOLLOW ? behind(player) : player.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            spot = ground(level, around);
        }
        fake.position = spot != null ? spot : player.position().add(0.0D, -64.0D, 0.0D);
        fake.previous = fake.position;
        fake.yaw = RANDOM.nextFloat() * 360.0F;
        fake.life = Mth.nextInt(RANDOM, 200, 600);
    }

    private static void move(ClientLevel level, LocalPlayer player, Fake fake)
    {
        switch (fake.behavior)
        {
            case WATCH ->
            {
                //too close: gone, to watch from somewhere else
                if (fake.position.distanceTo(player.position()) < 5.0D) {place(level, player, fake);}
                else {fake.yaw = yawTowards(fake.position, player.position());}
            }
            case WANDER ->
            {
                if (RANDOM.nextInt(60) == 0) {fake.yaw += Mth.nextFloat(RANDOM, -90.0F, 90.0F);}
                Vec3 step = fake.position.add(Vec3.directionFromRotation(0.0F, fake.yaw).scale(0.06D));
                Vec3 next = ground(level, step);
                if (next != null && Math.abs(next.y - fake.position.y) <= 1.0D) {fake.position = next;}
                else {fake.yaw += 180.0F;}
            }
            case FOLLOW ->
            {
                fake.yaw = yawTowards(fake.position, player.position());
                //only when the player doesn't look its way
                Vec3 toFake = fake.position.subtract(player.getEyePosition());
                boolean seen = toFake.lengthSqr() > 0.0D && player.getViewVector(1.0F).dot(toFake.normalize()) > 0.4D;
                if (seen) {return;}
                Vec3 target = ground(level, behind(player));
                if (target == null) {return;}
                Vec3 toTarget = target.subtract(fake.position);
                fake.position = toTarget.length() < 0.3D ? target : fake.position.add(toTarget.normalize().scale(0.3D));
            }
        }
    }

    private static Vec3 behind(LocalPlayer player) {return player.position().subtract(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(FOLLOW_DISTANCE));}

    private static float yawTowards(Vec3 from, Vec3 to) {return Facing.yawTowards(to.x - from.x, to.z - from.z);}

    /** Where something could stand, near this point: on a solid floor, with room above. */
    @Nullable private static Vec3 ground(ClientLevel level, Vec3 near)
    {
        BlockPos.MutableBlockPos pos = BlockPos.containing(near.x, near.y + 3.0D, near.z).mutable();
        for (int i = 0; i < 8; i++, pos.move(Direction.DOWN))
        {
            BlockPos below = pos.below();
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {return new Vec3(near.x, pos.getY(), near.z);}
        }
        return null;
    }

    /** While the hitboxes are drawn (EntityHitboxDebugRendererMixin): theirs, drawn just like the real ones. */
    public static void emit(float partialTicks)
    {
        for (Fake fake : FAKES)
        {
            Vec3 feet = fake.previous.lerp(fake.position, partialTicks);
            float halfWidth = fake.size[0] / 2.0F, eyeHeight = fake.size[2];
            AABB box = new AABB(feet.x - halfWidth, feet.y, feet.z - halfWidth, feet.x + halfWidth, feet.y + fake.size[1], feet.z + halfWidth);
            Gizmos.cuboid(box, GizmoStyle.stroke(-1));
            Gizmos.point(feet, -1, 2.0F);
            Gizmos.cuboid(new AABB(box.minX, box.minY + eyeHeight - 0.01D, box.minZ, box.maxX, box.minY + eyeHeight + 0.01D, box.maxZ), GizmoStyle.stroke(-65536));
            Vec3 eyes = feet.add(0.0D, eyeHeight, 0.0D);
            Gizmos.arrow(eyes, eyes.add(Vec3.directionFromRotation(0.0F, fake.yaw).scale(2.0D)), -16776961);
        }
    }
}
