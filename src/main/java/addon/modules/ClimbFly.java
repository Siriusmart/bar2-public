package addon.modules;

import java.util.List;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import addon.Bar2Dee2;

public class ClimbFly extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> targetLevel = sgGeneral.add(new DoubleSetting.Builder()
            .name("Target Y level")
            .description("Y level to reach")
            .defaultValue(121)
            .sliderMin(-64).sliderMax(384)
            .build());

    public final Setting<Double> pitchUpAngle = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch up angle")
            .description("Angle to use when pitching up")
            .defaultValue(-40)
            .sliderMin(-90).sliderMax(0)
            .build());
    public final Setting<Double> pitchDownAngle = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch down angle")
            .description("Angle to use when pitching down")
            .defaultValue(-3)
            .sliderMin(0).sliderMax(90)
            .build());
    public final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("Rotation speed")
            .description("Rotation speed when switching pitches")
            .defaultValue(10)
            .sliderMin(0).sliderMax(100)
            .build());
    public final Setting<Double> pitchUpLimit = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch up limit")
            .description("Speed to start pitching up")
            .defaultValue(1.5)
            .sliderMin(0).sliderMax(3)
            .build());
    public final Setting<Double> pitchDownLimit = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch down limit")
            .description("Speed to start pitching down")
            .defaultValue(0.5)
            .sliderMin(0).sliderMax(3)
            .build());
    public final Setting<Boolean> takeOff = sgGeneral.add(new BoolSetting.Builder()
            .name("Auto takeoff")
            .description("Whether to activate takeooff on start (if on ground)")
            .defaultValue(true)
            .build());
    public final Setting<Boolean> debugMessages = sgGeneral.add(new BoolSetting.Builder()
            .name("Debug messages")
            .description("Show debug messages")
            .defaultValue(false)
            .build());

    /*
     * public final Setting<Double> stallPitchUp = this.sgGeneral.add(new
     * DoubleSetting.Builder()
     * .name("Stall limit")
     * .description("Speed to start pitching up if close to ground")
     * .defaultValue(0.8)
     * .sliderMin(0).sliderMax(3)
     * .build());
     * public final Setting<Double> stallHeight = this.sgGeneral.add(new
     * DoubleSetting.Builder()
     * .name("Stall height")
     * .description("Height below which to use the stall limit")
     * .defaultValue(1.5)
     * .sliderMin(0).sliderMax(5)
     * .build());
     */
    public ClimbFly() {
        super(Bar2Dee2.CURRY, "ClimbFly", "Climb to altitude");
    }

    private final List<Class<? extends Module>> incompat = List.of(
            ElytraFly.class,
            Lander.class);

    private final List<Class<? extends Module>> require = List.of(
            FloppyFly.class);

    private boolean pitchUp;
    public float cameraYaw, cameraPitch;

    @Override
    public void onActivate() {
        if (!mc.player.isFallFlying()) {
            if (!takeOff.get()) {
                toggle();
                error("Player not in air.");
                return;
            }

            if (!Modules.get().get(Takeoff.class).isActive())
                Modules.get().get(Takeoff.class).toggle();
        }

        for (Class<? extends Module> module : incompat) {
            if (Modules.get().get(module).isActive())
                Modules.get().get(module).toggle();
        }

        for (Class<? extends Module> module : require) {
            if (!Modules.get().get(module).isActive())
                Modules.get().get(module).toggle();
        }

        cameraPitch = mc.player.getPitch();
        cameraYaw = mc.player.getYaw();
    }

    @Override
    public void onDeactivate() {
        mc.player.setPitch(cameraPitch);
    }

    @EventHandler
    public void onTick(TickEvent.Pre _e) {
        if (Modules.get().isActive(Takeoff.class))
            return;

        if (mc.player.getVelocity().length() > pitchUpLimit.get()) {
            pitchUp = true;
        } else if (mc.player.getVelocity().length() < pitchDownLimit.get()) {
            pitchUp = false;
        }

        if (pitchUp && mc.player.getPitch() > pitchUpAngle.get()) {
            mc.player.setPitch((float) Math.max(pitchUpAngle.get(), mc.player.getPitch() - rotationSpeed.get()));
        } else {
            mc.player.setPitch(pitchUp ? pitchUpAngle.get().floatValue() : pitchDownAngle.get().floatValue());
        }

        if (!mc.player.isFallFlying()) {
            error("Climb failed.");
            toggle();
        } else if (mc.player.getVelocity().length() > pitchDownLimit.get() && mc.player.getY() > targetLevel.get().doubleValue()) {
            if (debugMessages.get())
                info("Target reached.");
            toggle();
        }
    }
}