package addon.modules;

import java.util.List;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import addon.Bar2Dee2;

public class Lander extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final SettingGroup sgTarget = settings.createGroup("Target");

    public enum TargetMode {
        Relaxed,
        SneakRelaxed,
        Vertical,
        Target;

        public boolean isTargeted() {
            return this == Target || this == Vertical;
        }
    }

    public final Setting<Boolean> debugMessages = sgGeneral.add(new BoolSetting.Builder()
            .name("Debug messages")
            .description("Show debug messages")
            .defaultValue(false)
            .build());

    public final Setting<TargetMode> mode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
            .name("Target mode")
            .description("Landing objective")
            .defaultValue(TargetMode.Vertical)
            .build());
    public final Setting<Double> targetXs = sgTarget.add(new DoubleSetting.Builder()
            .name("Target X")
            .description("Landing objective X")
            .defaultValue(0)
            .sliderMin(-100)
            .sliderMax(100)
            .visible(() -> mode.get() == TargetMode.Target)
            .build());
    public final Setting<Double> targetZs = sgTarget.add(new DoubleSetting.Builder()
            .name("Target Z")
            .description("Landing objective Z")
            .defaultValue(0)
            .sliderMin(-100)
            .sliderMax(100)
            .visible(() -> mode.get() == TargetMode.Target)
            .build());
    public final Setting<Double> maxDeviate = sgTarget.add(new DoubleSetting.Builder()
            .name("Max deviation")
            .description("Tolerance distance for landing")
            .defaultValue(0.1)
            .visible(() -> mode.get().isTargeted())
            .build());
    public final Setting<Double> hSpeed = sgTarget.add(new DoubleSetting.Builder()
            .name("Horizontal speed")
            .description("Maximum horizontal speed")
            .defaultValue(0.1)
            .visible(() -> mode.get().isTargeted())
            .build());
    public final Setting<Boolean> exitOnFluid = sgTarget.add(new BoolSetting.Builder().defaultValue(true)
            .name("Exit on fluid").description("Disable module when landed on fluid").build());

    private double targetX, targetZ;
    public float cameraYaw, cameraPitch;

    public Lander() {
        super(Bar2Dee2.CURRY, "Lander", "Land vertically");
    }

    private final List<Class<? extends Module>> incompat = List.of(
            ElytraFly.class,
            ClimbFly.class,
            Takeoff.class,
            LevelFly.class);

    private final List<Class<? extends Module>> require = List.of(
            FloppyFly.class);

    public boolean retainAngles = false;

    @Override
    public void onActivate() {
        if (!mc.player.isFallFlying()) {
            toggle();
            error("Player not in air.");
            return;
        }

        for (Class<? extends Module> module : incompat) {
            if (Modules.get().get(module).isActive())
                Modules.get().get(module).toggle();
        }

        for (Class<? extends Module> module : require) {
            if (!Modules.get().get(module).isActive())
                Modules.get().get(module).toggle();
        }

        switch (mode.get()) {
            case Vertical:
                targetXs.set(mc.player.getX());
                targetZs.set(mc.player.getZ());
                break;
            default:
                break;
        }

        targetX = targetXs.get();
        targetZ = targetZs.get();

        if (!retainAngles) {
            cameraPitch = mc.player.getPitch();
            cameraYaw = mc.player.getYaw();
            retainAngles = false;
        }
        mc.player.setPitch(-15);
    }

    @Override
    public void onDeactivate() {
        mc.options.sneakKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);

        mc.player.setPitch(cameraPitch);

        if (mc.player.isOnGround() || (exitOnFluid.get() && mc.player.isInFluid()))
            mc.player.stopFallFlying();
    }

    @EventHandler
    public void onTick(TickEvent.Pre _e) {
        if (mc.player.isOnGround() || (exitOnFluid.get() && mc.player.isInFluid())) {
            toggle();

            if (debugMessages.get())
                info("Landing complete.");
            return;
        }

        mc.player.setPitch(-15);

        if (mode.get() == TargetMode.Relaxed)
            return;

        mc.options.sneakKey.setPressed(true);

        if (mode.get() == TargetMode.SneakRelaxed)
            return;

        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();

        mc.options.rightKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);

        double deviation = Math.sqrt(Math.pow(dx, 2) + Math.pow(dz, 2));

        if (deviation < maxDeviate.get()) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        } else {
            deviation = Math.max(deviation, 10);
            mc.player.setVelocity(dx / deviation * hSpeed.get(), mc.player.getVelocity().y,
                    dz / deviation * hSpeed.get());
            return;
        }

        /*
         * double cos = Math.cos(Math.toRadians(mc.player.getYaw()));
         * double sin = Math.sin(Math.toRadians(mc.player.getYaw()));
         * double normalised_x = dx * cos + dz * sin;
         * double normalised_z = dz * cos - dx * sin;
         * 
         * double angle = Math.atan2(normalised_z, -normalised_x);
         * int octant = (int) Math.round((angle < 0 ? angle + 2 * Math.PI : angle) /
         * (Math.PI / 4));
         * 
         * switch (octant % 8) {
         * case 0:
         * mc.options.rightKey.setPressed(true);
         * break;
         * case 1:
         * mc.options.rightKey.setPressed(true);
         * mc.options.forwardKey.setPressed(true);
         * break;
         * case 2:
         * mc.options.forwardKey.setPressed(true);
         * break;
         * case 3:
         * mc.options.forwardKey.setPressed(true);
         * mc.options.leftKey.setPressed(true);
         * break;
         * case 4:
         * mc.options.leftKey.setPressed(true);
         * break;
         * case 5:
         * mc.options.backKey.setPressed(true);
         * mc.options.leftKey.setPressed(true);
         * break;
         * case 6:
         * mc.options.backKey.setPressed(true);
         * break;
         * case 7:
         * mc.options.backKey.setPressed(true);
         * mc.options.rightKey.setPressed(true);
         * break;
         * }
         */
    }
}
