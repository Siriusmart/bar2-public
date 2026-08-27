package addon.modules;

import java.util.List;
import java.util.Optional;

import org.joml.Vector2d;
import org.joml.Vector2i;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.shapes.Shapes;
import addon.Bar2Dee2;
import addon.modules.Lander.TargetMode;

public class HighwayFly extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final SettingGroup sgDigger = settings.createGroup("Digger");
    public final SettingGroup sgHighway = settings.createGroup("Highway");

    public enum FlyMode {
        Floppy,
        ElytraFly
    }

    public enum DigMode {
        Full(true, true),
        Minimal(true, false),
        None(false, false);

        public final boolean dig, all;

        DigMode(boolean dig, boolean all) {
            this.dig = dig;
            this.all = all;
        }
    }

    public final Setting<Boolean> autoDetect = sgHighway.add(new BoolSetting.Builder().defaultValue(true)
            .name("Auto detect").description("Automatically configure settings").build());
    public final Setting<Integer> lookAhead = sgHighway.add(new IntSetting.Builder().defaultValue(32)
            .sliderMin(0)
            .sliderMax(64)
            .visible(() -> autoDetect.get())
            .name("Look ahead").description("Maximum distance to look ahead to determine the dimensions of a highway")
            .build());
    public final Setting<Double> minHealth = sgHighway.add(new DoubleSetting.Builder().defaultValue(0.5)
            .sliderMin(0)
            .sliderMax(1)
            .visible(() -> autoDetect.get())
            .name("Min health").description("Minimum highway health to be considered a highway")
            .build());
    public final Setting<Integer> maxWidth = sgHighway.add(new IntSetting.Builder().defaultValue(6)
            .sliderMin(0)
            .sliderMax(10)
            .visible(() -> autoDetect.get())
            .name("Maximum width").description("Maximum width before stop checking a highway")
            .build());

    public final Setting<Integer> highwayLevel = sgHighway.add(new IntSetting.Builder()
            .name("Highway Y")
            .description("Y level of highway to travel on")
            .defaultValue(121)
            .visible(() -> !autoDetect.get())
            .sliderMin(-64).sliderMax(384)
            .build());
    public final Setting<Integer> octantDirection = sgHighway.add(new IntSetting.Builder()
            .name("Octant")
            .description("Octant direction to travel on")
            .defaultValue(0)
            .visible(() -> !autoDetect.get())
            .sliderMin(0).sliderMax(7)
            .build());
    public final Setting<Integer> originX = sgHighway.add(new IntSetting.Builder()
            .name("Origin X")
            .description("X position of origin reference point")
            .defaultValue(0)
            .visible(() -> !autoDetect.get())
            .sliderMin(-100).sliderMax(100)
            .build());
    public final Setting<Integer> originZ = sgHighway.add(new IntSetting.Builder()
            .name("Origin Z")
            .description("Z position of origin reference point")
            .defaultValue(0)
            .visible(() -> !autoDetect.get())
            .sliderMin(-100).sliderMax(100)
            .build());
    public final Setting<Integer> leftWidth = sgHighway.add(new IntSetting.Builder()
            .name("Left width")
            .description("Width of highway to the left of player")
            .defaultValue(3)
            .visible(() -> !autoDetect.get())
            .sliderMin(0).sliderMax(10)
            .build());
    public final Setting<Integer> rightWidth = sgHighway.add(new IntSetting.Builder()
            .name("Right width")
            .description("Width of highway to the right of player")
            .defaultValue(3)
            .visible(() -> !autoDetect.get())
            .sliderMin(-10).sliderMax(0)
            .build());
    public final Setting<Integer> originX2 = sgHighway.add(new IntSetting.Builder()
            .name("Origin X2")
            .description("X position of origin reference point (2)")
            .defaultValue(0)
            .visible(() -> !autoDetect.get() && octantDirection.get() % 2 != 0)
            .sliderMin(-100).sliderMax(100)
            .build());
    public final Setting<Integer> originZ2 = sgHighway.add(new IntSetting.Builder()
            .name("Origin Z2")
            .description("Z position of origin reference point (2)")
            .defaultValue(0)
            .visible(() -> !autoDetect.get() && octantDirection.get() % 2 != 0)
            .sliderMin(-100).sliderMax(100)
            .build());
    public final Setting<Integer> leftWidth2 = sgHighway.add(new IntSetting.Builder()
            .name("Left width 2")
            .description("Width of highway to the left of player (2)")
            .defaultValue(3)
            .visible(() -> !autoDetect.get())
            .sliderMin(0).sliderMax(10)
            .build());
    public final Setting<Integer> rightWidth2 = sgHighway.add(new IntSetting.Builder()
            .name("Right width 2")
            .description("Width of highway to the right of player (2)")
            .defaultValue(3)
            .visible(() -> !autoDetect.get())
            .sliderMin(-10).sliderMax(0)
            .build());

    public final Setting<FlyMode> flyMode = sgGeneral.add(new EnumSetting.Builder<FlyMode>()
            .name("Fly mode")
            .description("Which module to use for elytra boosting")
            .defaultValue(FlyMode.ElytraFly)
            .build());
    public final Setting<Integer> maxRetries = sgGeneral.add(new IntSetting.Builder()
            .name("Max retries")
            .description("Number of times to restart Baritone before giving up")
            .defaultValue(3)
            .sliderMin(0).sliderMax(10)
            .build());
    public final Setting<DigMode> digMode = sgDigger.add(new EnumSetting.Builder<DigMode>()
            .name("Digger mode")
            .description("How much should the digger dig")
            .defaultValue(DigMode.Full)
            .build());
    public final Setting<Integer> clearToFlyLookForward = sgDigger.add(new IntSetting.Builder()
            .name("Clear to fly look forward")
            .description("How far in front to look for a spot to take off")
            .defaultValue(32)
            .visible(() -> !digMode.get().dig)
            .sliderMin(0).sliderMax(64)
            .build());
    public final Setting<Integer> diggerLookBack = sgDigger.add(new IntSetting.Builder()
            .name("Digger look back")
            .description("How much obstruction behind should the digger clean up")
            .defaultValue(0)
            .visible(() -> digMode.get().dig)
            .sliderMin(-32).sliderMax(0)
            .build());
    public final Setting<Integer> diggerLookForward = sgDigger.add(new IntSetting.Builder()
            .name("Digger look ahead")
            .description("How much obstruction ahead should the digger clean up")
            .defaultValue(4)
            .visible(() -> digMode.get().dig)
            .sliderMin(0).sliderMax(32)
            .build());
    public final Setting<Integer> antiCollisionLookForward = sgGeneral.add(new IntSetting.Builder()
            .name("Anti-collision look ahead")
            .description("How far to look ahead for collisions")
            .defaultValue(3)
            .sliderMin(0).sliderMax(5)
            .build());
    public final Setting<Integer> antiCollisionLookForwardDiag = sgGeneral.add(new IntSetting.Builder()
            .name("Anti-collision look ahead (diagonal)")
            .description("How far to look ahead for collisions for diagonal highways")
            .defaultValue(2)
            .sliderMin(0).sliderMax(5)
            .build());
    public final Setting<Boolean> kickStop = sgGeneral.add(new BoolSetting.Builder()
            .name("Kick stop")
            .description("Stop player movement on fly end")
            .defaultValue(true)
            .build());
    public final Setting<Boolean> debugMessages = sgGeneral.add(new BoolSetting.Builder()
            .name("Debug messages")
            .description("Show debug messages")
            .defaultValue(false)
            .build());

    private int oct, level;
    private BlockPos effOrigin, o1, o2;
    private int lw1, rw1, lw2, rw2, effWidth;
    public float cameraYaw, cameraPitch;

    public HighwayFly() {
        super(Bar2Dee2.BAR, "HighwayFly", "Automated highway travel");
    }

    public static Vector2i extractXZ(BlockPos v3) {
        return new Vector2i(v3.getX(), v3.getZ());
    }

    public static Vector2i extractXZ(Vec3i v3) {
        return new Vector2i(v3.getX(), v3.getZ());
    }

    public static Vector2d extractXZ(Vec3 v3) {
        return new Vector2d(v3.x(), v3.z());
    }

    public static Vector2d as2d(Vector2i v2) {
        return new Vector2d(v2.x(), v2.y());
    }

    public static double scalarProd(Vector2d one, Vector2d two) {
        return one.x() * two.x() + one.y() * two.y();
    }

    public static int scalarProd(Vector2i one, Vector2i two) {
        return one.x() * two.x() + one.y() * two.y();
    }

    public static Vec3i divide(Vec3i p, int q) {
        return new Vec3i(p.getX() / q, p.getY() / q, p.getZ() / q);
    }

    public static Vec3i unitVector(int octant) {
        switch (octant % 8) {
            case 0:
                return new Vec3i(-1, 0, 0);
            case 1:
                return new Vec3i(-1, 0, 1);
            case 2:
                return new Vec3i(0, 0, 1);
            case 3:
                return new Vec3i(1, 0, 1);
            case 4:
                return new Vec3i(1, 0, 0);
            case 5:
                return new Vec3i(1, 0, -1);
            case 6:
                return new Vec3i(0, 0, -1);
            case 7:
                return new Vec3i(-1, 0, -1);
            default:
                throw new RuntimeException("unreachable");
        }
    }

    // pointing in left direction
    public static Vec3i normalVector(int octant) {
        switch (octant % 8) {
            case 0:
                return new Vec3i(0, 0, 1);
            case 1:
                return new Vec3i(1, 0, 1);
            case 2:
                return new Vec3i(1, 0, 0);
            case 3:
                return new Vec3i(1, 0, -1);
            case 4:
                return new Vec3i(0, 0, -1);
            case 5:
                return new Vec3i(-1, 0, -1);
            case 6:
                return new Vec3i(-1, 0, 0);
            case 7:
                return new Vec3i(-1, 0, 1);
            default:
                throw new RuntimeException("unreachable");
        }
    }

    public boolean checkClearSingle(BlockPos base) {
        return mc.level.getBlockState(base.above(1)).isAir()
                && mc.level.getBlockState(base.above(2)).isAir() && mc.level.getBlockState(base.above(3)).isAir();
    }

    public Optional<Vector2i> checkClearRow(BlockPos origin) {
        boolean found = false;

        int limit = diggerLookForward.get();
        Vec3i unit = unitVector(oct);

        int min = 0, max = 0;

        for (int i = diggerLookBack.get(); i <= limit; i++) {
            if (!checkClearSingle(origin.offset(unit.multiply(i)))) {
                if (!found)
                    min = i;

                found = true;
                max = i;
            }
        }

        if (found)
            return Optional.of(new Vector2i(min, max));
        return Optional.empty();
    }

    public Optional<Vector2i> checkClear(BlockPos origin) {
        boolean found = false;
        int min = 0, max = 0;
        Vec3i normal = normalVector(oct);

        if (oct % 2 == 0) {
            int offset = digMode.get().all ? 0 : effWidth % 2 == 0 ? effWidth / 2 - 1 : Math.floorDiv(effWidth, 2);
            for (int i = rw1 + offset; i < lw1 - offset; i++) {
                Optional<Vector2i> res = checkClearRow(origin.offset(normal.multiply(i)));
                if (res.isPresent()) {
                    if (!found) {
                        found = true;
                        min = res.get().x();
                        max = res.get().y();
                    } else {
                        min = Math.min(res.get().x(), min);
                        max = Math.max(res.get().y(), max);
                    }
                }
            }
        } else {
            BlockPos origin1 = intersectionWith(o1, origin);
            BlockPos origin2 = intersectionWith(o2, origin);

            int offset1 = Math.ceilDiv(lw1 - rw1 - 3, 2);
            int offset2 = Math.ceilDiv(lw2 - rw2 - 3, 2);

            for (int i = rw1 + offset1; i < lw1 - offset1; i++) {
                Optional<Vector2i> res = checkClearRow(origin1.offset(normal.multiply(i)));
                if (res.isPresent()) {
                    if (!found) {
                        found = true;
                        min = res.get().x();
                        max = res.get().y();
                    } else {
                        min = Math.min(res.get().x(), min);
                        max = Math.max(res.get().y(), max);
                    }
                }
            }

            for (int i = rw2 + offset2; i < lw2 - offset2; i++) {
                Optional<Vector2i> res = checkClearRow(origin2.offset(normal.multiply(i)));
                if (res.isPresent()) {
                    if (!found) {
                        found = true;
                        min = res.get().x();
                        max = res.get().y();
                    } else {
                        min = Math.min(res.get().x(), min);
                        max = Math.max(res.get().y(), max);
                    }
                }
            }
        }

        if (found)
            return Optional.of(new Vector2i(min, max));
        return Optional.empty();
    }

    public boolean checkHealthSingle(BlockPos base) {
        return !mc.level.getBlockState(base).isAir() && mc.level.getBlockState(base.above(1)).isAir()
                && mc.level.getBlockState(base.above(2)).isAir() && mc.level.getBlockState(base.above(3)).isAir();
    }

    public float checkHealth(BlockPos origin, int octant) {
        int healthy = 0;
        Vec3i unit = unitVector(octant);

        int limit = lookAhead.get();

        for (int i = 0; i < limit; i++) {
            if (checkHealthSingle(origin.offset(unit.multiply(i))))
                healthy++;
        }

        return ((float) healthy) / limit;
    }

    public Optional<Vector2i> testHighway(BlockPos origin, int octant) {
        // exclusive
        int left = 0;
        // inclusive
        int right = 0;

        Vec3i normal = normalVector(octant);
        float threshold = minHealth.get().floatValue();

        while (checkHealth(origin.offset(normal.multiply(left)), octant) >= threshold && left - right <= maxWidth.get())
            left++;
        while (checkHealth(origin.offset(normal.multiply(right - 1)), octant) >= threshold
                && left - right <= maxWidth.get())
            right--;

        if (left == right)
            return Optional.empty();

        return Optional.of(new Vector2i(left, right));
    }

    public static double trueYaw(double yaw) {
        double out = 90 - yaw;
        if (out < 0)
            out += 360;

        return out;
    }

    public static double mcYaw(double trueYaw) {
        return trueYaw(trueYaw);
    }

    public static int getOctant(double trueYaw) {
        int octant1 = (int) Math.round(trueYaw / 45);
        if (octant1 < 0)
            octant1 += 8;

        return octant1 % 8;
    }

    public static int octantTrueYaw(int octant) {
        return octant * 45;
    }

    public BlockPos origin2(int octant, BlockPos origin1) {
        int x = origin1.getX();
        int y = origin1.getY();
        int z = origin1.getZ();

        switch (octant) {
            case 1:
                x -= 1;
                break;
            case 3:
                z += 1;
                break;
            case 5:
                x += 1;
                break;
            case 7:
                z -= 1;
                break;
            default:
                throw new RuntimeException("even octants don't have origin2");
        }
        
        // TODO convert to expression

        return new BlockPos(x, y, z);
    }

    // second pick
    public int getOctant2(double trueYaw, int octant1) {
        int octant2 = octant1 + (trueYaw > octantTrueYaw(octant1) ? 1 : -1);
        if (octant2 < 0)
            octant2 += 8;
        return octant2 % 8;
    }

    // (octant, left offset, right offset)
    public Optional<Vec3i> getDetail(BlockPos base, float trueYaw) {
        int octant1 = getOctant(trueYaw);

        Optional<Vector2i> test1 = testHighway(base, octant1);
        if (test1.isPresent())
            return Optional.of(new Vec3i(octant1, test1.get().x(), test1.get().y()));

        int octant2 = getOctant2(trueYaw, octant1);

        Optional<Vector2i> test2 = testHighway(base, octant2);
        if (test2.isPresent())
            return Optional.of(new Vec3i(octant2, test2.get().x(), test2.get().y()));

        return Optional.empty();
    }

    private IBaritone baritone = null;

    private final List<Class<? extends Module>> incompat = List.of(
            Lander.class,
            LevelFly.class,
            ClimbFly.class);

    private final List<Class<? extends Module>> toDisable = List.of(
            Lander.class,
            LevelFly.class,
            ClimbFly.class,
            Scaffold.class,
            HighwayDigger.class);

    // whether the fly modules should follow this module's fly mode, kept true
    // until the very end of onDeactivate so the handover to Lander uses it too
    private static boolean driving = false;

    // module providing the boost while flying
    public static Class<? extends Module> boostModule() {
        if (driving && Modules.get().get(HighwayFly.class).flyMode.get() == FlyMode.ElytraFly)
            return ElytraFly.class;

        return FloppyFly.class;
    }

    // module that would fight the one returned by boostModule
    public static Class<? extends Module> conflictingBoostModule() {
        return boostModule() == FloppyFly.class ? ElytraFly.class : FloppyFly.class;
    }

    // to be called by the fly modules once their own incompatibilities are handled
    public static void applyBoostModules() {
        Module conflicting = Modules.get().get(conflictingBoostModule());
        if (conflicting.isActive())
            conflicting.toggle();

        Module boost = Modules.get().get(boostModule());
        if (!boost.isActive())
            boost.toggle();
    }

    private boolean floppyFlyRestore, elytraFlyRestore;

    @Override
    public void onActivate() {
        driving = true;
        floppyFlyRestore = Modules.get().isActive(FloppyFly.class);
        elytraFlyRestore = Modules.get().isActive(ElytraFly.class);

        if (!BaritoneUtils.IS_AVAILABLE) {
            error("Baritone not found.");
            toggle();
            return;
        }

        if (baritone == null)
            baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        for (Class<? extends Module> module : incompat) {
            if (Modules.get().get(module).isActive())
                Modules.get().get(module).toggle();
        }

        applyBoostModules();

        if (autoDetect.get()) {
            if (!mc.player.onGround()) {
                error("Player not on ground.");
                toggle();
                return;
            }

            o1 = mc.player.blockPosition().below(1);
            Optional<Vec3i> found = getDetail(o1, (float) trueYaw(mc.player.getYRot()));
            if (found.isEmpty()) {
                error("No highways found.");
                toggle();
                return;
            }

            if (found.get().getX() % 2 != 0) {
                o2 = origin2(found.get().getX(), o1);
                Optional<Vec3i> origin2res = getDetail(o2, (float) trueYaw(mc.player.getYRot()));

                if (!origin2res.isPresent()) {
                    o2 = o2.offset(normalVector(found.get().getX()));
                    origin2res = getDetail(o2, (float) trueYaw(mc.player.getYRot()));
                }

                if (origin2res.isEmpty() || origin2res.get().getX() != found.get().getX()) {
                    error("No highways found.");
                    toggle();
                    return;
                }

                originX2.set(o2.getX());
                originZ2.set(o2.getZ());
                leftWidth2.set(origin2res.get().getY());
                rightWidth2.set(origin2res.get().getZ());
            }

            originX.set(o1.getX());
            originZ.set(o1.getZ());
            highwayLevel.set(o1.getY());
            octantDirection.set(found.get().getX());
            leftWidth.set(found.get().getY());
            rightWidth.set(found.get().getZ());

        } else {
            o1 = new BlockPos(originX.get(), highwayLevel.get(), originZ.get());
            o2 = new BlockPos(originX2.get(), highwayLevel.get(), originZ2.get());
        }

        oct = octantDirection.get();
        level = highwayLevel.get();

        lw1 = leftWidth.get();
        rw1 = rightWidth.get();
        lw2 = leftWidth2.get();
        rw2 = rightWidth2.get();

        if (oct % 2 != 0 && lw1 - rw1 == lw2 - rw2) {
            BlockPos edge1 = o1.offset(normalVector(oct).multiply(lw1 - 1));
            BlockPos edge2 = o2.offset(normalVector(oct).multiply(lw2 - 1));

            if (edge1.offset(divide(normalVector(oct).offset(unitVector(oct)), 2)).equals(edge2)) {
                lw2 -= 1;
                leftWidth2.set(lw2);
            } else {
                lw1 -= 1;
                leftWidth.set(lw1);
            }

            warning("Highway width bogus, settings have been modified.");
        }

        cameraPitch = mc.player.getXRot();
        cameraYaw = mc.player.getYRot();

        effWidth = oct % 2 == 0 ? lw1 - rw1 : Math.max(lw1 - rw1, lw2 - rw2);

        if (oct % 2 != 0 && effWidth < 3) {
            error("Diagonal highways less than 3 blocks wide are not supported.");
            toggle();
            return;
        }

        effOrigin = gefEffOrigin();
        state = State.GotoClear;
        state.start(this);
    }

    @Override
    public void onDeactivate() {
        mc.options.keyJump.setDown(false);
        mc.options.keyUp.setDown(false);
        mc.options.keyShift.setDown(false);

        for (Class<? extends Module> module : toDisable) {
            if (module == Lander.class)
                continue;

            if (Modules.get().get(module).isActive())
                Modules.get().get(module).toggle();
        }

        baritone.getPathingBehavior().cancelEverything();

        if (floppyFlyRestore != Modules.get().isActive(FloppyFly.class)) {
            Modules.get().get(FloppyFly.class).toggle();
        }

        if (elytraFlyRestore != Modules.get().isActive(ElytraFly.class)) {
            Modules.get().get(ElytraFly.class).toggle();
        }

        if (!mc.player.isFallFlying()) {
            mc.player.setXRot(cameraPitch);
            mc.player.setYRot(cameraYaw);
        } else {
            Lander lander = Modules.get().get(Lander.class);
            lander.cameraPitch = cameraPitch;
            lander.cameraYaw = cameraYaw;
            lander.retainAngles = true;
            if (!lander.isActive())
                lander.toggle();
        }

        driving = false;
    }

    public BlockPos gefEffOrigin() {
        if (oct % 2 == 0) {
            return o1.offset(normalVector(oct).multiply(Math.floorDiv(lw1 + rw1 - 1, 2)));
        }

        if (lw1 - rw1 == lw2 - rw2) {
            BlockPos center1 = o1.offset(normalVector(oct).multiply(Math.floorDiv(lw1 + rw1 - 1, 2)));
            BlockPos center2 = o2.offset(normalVector(oct).multiply(Math.floorDiv(lw2 + rw2 - 1, 2)));
            BlockPos diff = center2.subtract(center1);
            int dotproduct = scalarProd(extractXZ(diff), extractXZ(normalVector(oct)));

            return dotproduct > 0 ? center1 : center2;
        } else if ((lw1 - rw1) % 2 == 0) {
            return o2.offset(normalVector(oct).multiply(Math.floorDiv(lw2 + rw2 - 1, 2)));
        } else {
            return o1.offset(normalVector(oct).multiply(Math.floorDiv(lw1 + rw1 - 1, 2)));
        }
    }

    public BlockPos intersection() {
        BlockPos blockPos = mc.player.blockPosition().below(1);
        Vec3i normal = normalVector(oct);
        int multiplier = scalarProd(extractXZ(effOrigin.subtract(blockPos)), extractXZ(normal));

        BlockPos intersect;

        if (oct % 2 == 0) {
            intersect = blockPos.offset(normal.multiply(multiplier));
        } else if (multiplier % 2 == 0) {
            intersect = blockPos.offset(normal.multiply(multiplier / 2));
        } else {
            blockPos = blockPos.offset(divide(normal.offset(unitVector(oct)), 2));
            multiplier = scalarProd(extractXZ(effOrigin.subtract(blockPos)), extractXZ(normal));
            intersect = blockPos.offset(normal.multiply(multiplier / 2));
        }

        return intersect.atY(level);
    }

    public BlockPos intersectionWith(BlockPos specificOrigin) {
        BlockPos blockPos = mc.player.blockPosition().below(1);
        return intersectionWith(specificOrigin, blockPos);
    }

    public BlockPos intersectionWith(BlockPos specificOrigin, BlockPos blockPos) {
        Vec3i normal = normalVector(oct);
        int multiplier = scalarProd(extractXZ(specificOrigin.subtract(blockPos)), extractXZ(normal));

        BlockPos intersect;

        if (oct % 2 == 0) {
            intersect = blockPos.offset(normal.multiply(multiplier));
        } else if (multiplier % 2 == 0) {
            intersect = blockPos.offset(normal.multiply(multiplier / 2));
        } else {
            blockPos = blockPos.offset(divide(normal.offset(unitVector(oct)), 2));
            multiplier = scalarProd(extractXZ(specificOrigin.subtract(blockPos)), extractXZ(normal));
            intersect = blockPos.offset(normal.multiply(multiplier / 2));
        }

        return intersect.atY(level);
    }

    public BlockPos diggerIntersect(BlockPos blockPos) {
        if (oct % 2 == 0)
            return intersectionWith(effOrigin, blockPos);
        if (Math.min(lw1 - rw1, lw2 - rw2) % 2 == 0)
            return intersectionWith(effOrigin, blockPos);
        if (lw1 - rw1 < lw2 - rw2)
            return intersectionWith(o2.offset(normalVector(oct).multiply(Math.floorDiv(lw2 + rw2 - 1, 2))), blockPos);
        else
            return intersectionWith(o1.offset(normalVector(oct).multiply(Math.floorDiv(lw1 + rw1 - 1, 2))), blockPos);
    }

    private boolean canMine(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.getDestroySpeed(mc.level, pos) < 0)
            return false;
        return state.getShape(mc.level, pos) != Shapes.empty();
    }

    private boolean blockInFront(int height) {
        return blockInFront(height, mc.player.blockPosition());
    }

    private boolean blockInFront(int height, BlockPos pos) {
        if (oct % 2 == 0) {
            int lookAhead = antiCollisionLookForward.get();
            for (int i = 1; i <= lookAhead; i++) {
                if (canMine(
                        pos.atY(level + height)
                                .offset(unitVector(oct).multiply(i))))
                    return true;
            }
        } else {
            int lookAhead = antiCollisionLookForwardDiag.get();
            for (int i = 0; i < lookAhead; i++) {
                if (canMine(
                        pos.atY(level + height)
                                .offset(unitVector(oct).multiply(i))
                                .offset(divide(unitVector(oct).offset(normalVector(oct)), 2)))
                        ||
                        canMine(
                                pos.atY(level + height)
                                        .offset(unitVector(oct).multiply(i))
                                        .offset(divide(
                                                unitVector(oct).subtract(normalVector(oct)), 2))))
                    return true;
            }
        }

        return false;
    }

    private Optional<BlockPos> digStartPos() {
        BlockPos intersect = intersectionWith(o1);
        BlockPos effIntersect = oct % 2 == 1 && !digMode.get().all ? intersection()
                : diggerIntersect(mc.player.blockPosition());
        Optional<Vector2i> minmax = checkClear(intersect);

        if (minmax.isEmpty())
            return Optional.empty();

        Vec3i unit = HighwayFly.unitVector(oct);
        digUntil = effIntersect.offset(unit.multiply(minmax.get().y() + oct % 2)).above(1);
        return Optional.of(effIntersect.offset(unit.multiply(minmax.get().x() - 1)).above(1));
    }

    private State state;
    private BlockPos digUntil;

    private enum State {
        Digging {
            @Override
            void start(HighwayFly module) {
                Scaffold scaffold = Modules.get().get(Scaffold.class);
                if (!scaffold.isActive())
                    scaffold.toggle();

                HighwayDigger digger = Modules.get().get(HighwayDigger.class);
                digger.height.set(3);
                int diggerWidth;

                if (module.digMode.get().all)
                    diggerWidth = module.effWidth;
                else if (module.oct % 2 == 1)
                    diggerWidth = 3;
                else if (module.effWidth % 2 == 1)
                    diggerWidth = 1;
                else
                    diggerWidth = 2;

                digger.width.set(diggerWidth);
                module.mc.player.setYRot((float) HighwayFly.mcYaw(HighwayFly.octantTrueYaw(module.oct)));

                if (!digger.isActive())
                    digger.toggle();
            }

            @Override
            void tick(HighwayFly module) {
                if (HighwayFly.scalarProd(
                        HighwayFly.extractXZ(module.mc.player.blockPosition().subtract(module.digUntil)),
                        HighwayFly.extractXZ(HighwayFly.unitVector(module.oct))) >= 0) {

                    if (module.digStartPos().isPresent())
                        return;

                    Scaffold scaffold = Modules.get().get(Scaffold.class);
                    HighwayDigger digger = Modules.get().get(HighwayDigger.class);
                    if (scaffold.isActive())
                        scaffold.toggle();
                    if (digger.isActive())
                        digger.toggle();

                    if (module.debugMessages.get())
                        module.info("Stared GOTOCLEAR because dig complete.");
                    module.state = GotoClear;
                    module.state.start(module);
                }
            }
        },
        GotoClear {
            BlockPos targetPos;
            int barAttempt = 0;
            boolean started;
            boolean isClearToFly;
            boolean nextIsDig;

            @Override
            void start(HighwayFly module) {
                if (module.mc.player.getDeltaMovement().horizontalDistance() != 0 && !module.mc.player.isInLiquid()) {
                    started = false;
                    return;
                }
                started = true;

                if (!module.digMode.get().dig) {
                    nextIsDig = false;
                    targetPos = findClearToFly(module).above(1);
                    module.baritone.getPathingBehavior().cancelEverything();
                    module.baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetPos));
                    return;
                }

                nextIsDig = true;

                Optional<BlockPos> startPos = module.digStartPos();

                if (startPos.isEmpty()) {
                    if (module.debugMessages.get())
                        module.info("Started GOTOINTERSECT because minmax is empty.");
                    module.state = GotoIntersect;
                    module.state.start(module);
                    return;
                }

                targetPos = startPos.get();

                if (scalarProd(extractXZ(targetPos.subtract(module.mc.player.blockPosition())),
                        extractXZ(normalVector(module.oct))) == 0
                        && scalarProd(extractXZ(targetPos.subtract(module.mc.player.blockPosition())),
                                extractXZ(unitVector(module.oct))) >= 0) {
                    if (module.debugMessages.get())
                        module.info("Stared DIGGING because no baritone needed.");
                    module.state = Digging;
                    module.state.start(module);
                    return;
                }

                module.baritone.getPathingBehavior().cancelEverything();
                module.baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetPos));
            }

            @Override
            void tick(HighwayFly module) {
                if (!started)
                    if (module.mc.player.getDeltaMovement().horizontalDistance() == 0 || module.mc.player.isInLiquid()) {
                        started = true;
                        start(module);
                    } else
                        return;

                if (!module.mc.player.isFallFlying() && !module.baritone.getCustomGoalProcess().isActive()) {
                    if (!module.mc.player.blockPosition().equals(targetPos)) {
                        if (++barAttempt > module.maxRetries.get()) {
                            module.toggle();
                            module.error("Given up restarting Baritone at " + module.maxRetries.get() + "retries.");
                            return;
                        }

                        module.baritone.getPathingBehavior().cancelEverything();
                        start(module);
                        return;
                    }

                    if (nextIsDig) {
                        if (module.debugMessages.get())
                            module.info("Started DIGGING because reached goal.");
                        module.state = Digging;
                    } else {
                        if (isClearToFly) {
                            if (module.debugMessages.get())
                                module.info("Started FLYING because reached goal.");
                            module.state = Flying;
                        } else {
                            if (module.debugMessages.get())
                                module.info("Continue GOTOCLEAR because no clear space found.");
                        }
                    }

                    module.state.start(module);
                }
            }

            boolean isClearToFly(HighwayFly module, BlockPos pos) {
                return !(module.canMine(pos.above(1)) || module.canMine(pos.above(2)) || module.canMine(pos.above(3))
                        || module.blockInFront(2, pos)
                        || module.blockInFront(3, pos));
            }

            BlockPos findClearToFly(HighwayFly module) {
                Vec3i unit = unitVector(module.oct);
                BlockPos cursor = module.intersection();

                int limit = module.clearToFlyLookForward.get();

                for (int i = 0; i < limit; i++) {
                    cursor = cursor.offset(unit);

                    if (isClearToFly(module, cursor)) {
                        isClearToFly = true;
                        return cursor;
                    }
                }

                isClearToFly = false;

                return cursor.offset(unit);
            }
        },
        Landing {
            @Override
            void start(HighwayFly module) {
                Lander lander = Modules.get().get(Lander.class);
                lander.mode.set(TargetMode.Vertical);
                lander.exitOnFluid.set(true);
                if (!lander.isActive())
                    lander.toggle();
            }

            @Override
            void tick(HighwayFly module) {
                if (module.mc.player.blockPosition().getY() == module.level + 1
                        && module.mc.level.getBlockState(module.mc.player.blockPosition().atY(module.level)).isAir()
                        && !Modules.get().isActive(Scaffold.class))
                    Modules.get().get(Scaffold.class).toggle();

                if (Modules.get().isActive(Scaffold.class)
                        && !module.mc.level.getBlockState(module.mc.player.blockPosition().atY(module.level)).isAir()) {
                    Modules.get().get(Scaffold.class).toggle();
                }

                if (!Modules.get().isActive(Lander.class)) {
                    if (Modules.get().isActive(Scaffold.class)) {
                        Modules.get().get(Scaffold.class).toggle();
                    }

                    if (module.debugMessages.get())
                        module.info("Started GOTOCLEAR because lander inactive.");

                    module.state = GotoClear;
                    module.state.start(module);
                    return;
                }
            }
        },
        Flying {
            boolean started;
            Vector2i normal;
            int grace;
            // elytra fly mode only, whether the climb to cruising height is done
            boolean ascended;

            @Override
            public void start(HighwayFly module) {
                if (!module.mc.player.onGround()) {
                    started = false;
                    return;
                }

                started = true;

                module.mc.player.setYRot((float) HighwayFly.mcYaw(HighwayFly.octantTrueYaw(module.oct)));
                normal = HighwayFly.extractXZ(HighwayFly.normalVector(module.oct));
                ascended = false;

                if (module.flyMode.get() == FlyMode.ElytraFly) {
                    // elytra fly flies level by itself, all it needs is a takeoff
                    Takeoff takeoff = Modules.get().get(Takeoff.class);
                    if (takeoff.isActive())
                        takeoff.toggle();
                    takeoff.toggle();
                } else {
                    LevelFly levelFly = Modules.get().get(LevelFly.class);
                    levelFly.yLevel.set(module.level + 2);
                    levelFly.takeOff.set(true);
                    if (levelFly.isActive())
                        levelFly.toggle();
                    if (!levelFly.isActive())
                        levelFly.toggle();
                }

                grace = 10;
            }

            @Override
            public void tick(HighwayFly module) {
                if (!started) {
                    if (!module.mc.player.onGround())
                        return;
                    start(module);
                }

                if (grace != 0)
                    --grace;

                if ((grace == 0 && !module.mc.player.isFallFlying()) || module.mc.player.isInLiquid()) {
                    if (module.debugMessages.get())
                        module.info("Started GOTOCLEAR because not flying.");
                    LevelFly levelFly = Modules.get().get(LevelFly.class);
                    if (levelFly.isActive())
                        levelFly.toggle();
                    module.mc.player.stopFallFlying();
                    stopFlying(module);
                    module.state = State.GotoClear;
                    module.state.start(module);
                    return;
                }

                if (module.flyMode.get() == FlyMode.ElytraFly)
                    elytraFlyTick(module);

                boolean blockInFront = module.blockInFront(2);
                // sunk more than a block under the target height, only once the
                // climb is done, as the launch pad itself sits a block under it
                boolean yLevel = module.flyMode.get() == FlyMode.ElytraFly
                        ? ascended && module.mc.player.getY() < module.level + 1
                        : module.mc.player.blockPosition().getY() <= module.level;
                // while climbing there is no forward input yet, so the velocity
                // based checks below would fire on a perfectly healthy takeoff
                boolean climbing = module.flyMode.get() == FlyMode.ElytraFly && !ascended;
                boolean lowVelo = grace == 0 && !climbing
                        && module.mc.player.getDeltaMovement().horizontalDistance() <= 0.5;
                boolean badDir = !climbing && module.mc.player.isFallFlying()
                        && module.mc.player.getDeltaMovement().horizontalDistance() > 0.5
                        && Math.abs(HighwayFly.scalarProd(HighwayFly.as2d(normal),
                                HighwayFly.extractXZ(module.mc.player.getDeltaMovement())))
                                / module.mc.player.getDeltaMovement().horizontalDistance() > 0.1;
                if (blockInFront || yLevel || lowVelo || badDir) {

                    if (module.debugMessages.get()) {
                        if (blockInFront)
                            module.info("Started LANDING because blockInFront.");
                        else if (yLevel)
                            module.info("Started LANDING because yLevel.");
                        else if (lowVelo)
                            module.info("Started LANDING because lowVelo.");
                        else if (badDir)
                            module.info("Started LANDING because badDir.");
                    }

                    stopFlying(module);
                    LevelFly levelFly = Modules.get().get(LevelFly.class);
                    if (levelFly.isActive())
                        levelFly.toggle();
                    module.state = State.Landing;
                    module.state.start(module);
                }
            }

            // elytra fly is driven by movement keys rather than pitch, so climb
            // on jump until cruising height, then hold the height and go forward
            private void elytraFlyTick(HighwayFly module) {
                if (!module.mc.player.isFallFlying() || Modules.get().isActive(Takeoff.class))
                    return;

                double target = module.level + 2;
                double y = module.mc.player.getY();

                if (!ascended) {
                    ascended = y >= target;
                    module.mc.options.keyJump.setDown(!ascended);
                    module.mc.options.keyUp.setDown(ascended);

                    if (!ascended)
                        return;
                }

                // elytra fly moves a fixed step per tick, so only correct the height
                // when a whole step would not carry the player past the target
                double step = 0.5 * Modules.get().get(ElytraFly.class).verticalSpeed.get();

                module.mc.options.keyJump.setDown(target - y > step);
                module.mc.options.keyShift.setDown(y - target > step);
                module.mc.options.keyUp.setDown(true);
                // elytra fly derives its velocity from yaw, so it has to stay put
                module.mc.player.setYRot((float) HighwayFly.mcYaw(HighwayFly.octantTrueYaw(module.oct)));
            }

            private void stopFlying(HighwayFly module) {
                releaseKeys(module);

                if (module.kickStop.get())
                    module.mc.player.setDeltaMovement(0, module.mc.player.getDeltaMovement().y(), 0);
            }
        },
        GotoIntersect {
            BlockPos targetPos;
            int barAttempt;

            @Override
            public void start(HighwayFly module) {
                barAttempt = 0;
                targetPos = module.intersection().above(1);
                module.baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetPos));
            }

            @Override
            public void tick(HighwayFly module) {
                if (!module.baritone.getCustomGoalProcess().isActive()) {
                    if (!module.mc.player.blockPosition().equals(targetPos)) {
                        if (++barAttempt > module.maxRetries.get()) {
                            module.toggle();
                            module.error("Given up restarting Baritone at " + module.maxRetries.get() + "retries.");
                            return;
                        }

                        module.baritone.getPathingBehavior().cancelEverything();
                        start(module);
                        return;
                    }

                    if (module.debugMessages.get())
                        module.info("Started FLYING because reached goal.");
                    module.state = Flying;
                    module.state.start(module);
                }
            }
        };

        abstract void start(HighwayFly module);

        abstract void tick(HighwayFly module);

        void releaseKeys(HighwayFly module) {
            module.mc.options.keyJump.setDown(false);
            module.mc.options.keyUp.setDown(false);
            module.mc.options.keyShift.setDown(false);
        }
    }

    private boolean isPaused = false;

    public boolean isPaused() {
        return isPaused;
    }

    @EventHandler
    public void onTick(TickEvent.Pre _e) {
        if (state == null)
            throw new RuntimeException("unreachable");

        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            if (!isPaused) {
                isPaused = true;
                warning("No elytra equipped, waiting.");
            }
            return;
        }

        if (isPaused) {
            isPaused = false;
            if (debugMessages.get())
                info("Elytra equipped, resuming module.");
        }

        state.tick(this);
    }
}