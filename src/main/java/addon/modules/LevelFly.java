package addon.modules;

import java.util.List;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import addon.Bar2Dee2;

public class LevelFly extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> yLevel = sgGeneral.add(new IntSetting.Builder()
            .name("Y level")
            .description("Y level to travel on")
            .defaultValue(121)
            .sliderMin(-64)
            .sliderMax(384)
            .build());

    public final Setting<Double> pitchUpAngle = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch up angle")
            .description("Angle to use when pitching up")
            .defaultValue(-10)
            .sliderMin(-90).sliderMax(90)
            .build());
    public final Setting<Double> pitchDownAngle = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch down angle")
            .description("Angle to use when pitching down")
            .defaultValue(-3)
            .sliderMin(-90).sliderMax(90)
            .build());
    public final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("Rotation speed")
            .description("Rotation speed when switching pitches")
            .defaultValue(50)
            .sliderMin(0).sliderMax(100)
            .build());

    public final Setting<Boolean> takeOff = sgGeneral.add(new BoolSetting.Builder()
            .name("Auto takeoff")
            .description("Activate takeoff on start (if on ground)")
            .defaultValue(true)
            .build());

    public final Setting<Boolean> recover = sgGeneral.add(new BoolSetting.Builder()
            .name("Recover")
            .description("Recover altitude if below")
            .defaultValue(true)
            .build());

    public LevelFly() {
        super(Bar2Dee2.CURRY, "LevelFly", "Fly at a constant height");
    }

    private float upperBound, lowerBound;
    private boolean pitchingDown;
    public float cameraYaw, cameraPitch;

    private final List<Class<? extends Module>> incompat = List.of(
            Lander.class,
            ElytraFly.class);

    private final List<Class<? extends Module>> require = List.of(
            FloppyFly.class);

    private final List<Class<? extends Module>> toDisable = List.of(
            ClimbFly.class,
            Takeoff.class);

    @Override
    public void onActivate() {
        if (mc.player.getY() < upperBound && !(takeOff.get() || (mc.player.isFallFlying() && recover.get()))) {
            error("Player must be above upper bounds.");
            toggle();
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

        cameraPitch = mc.player.getPitch();
        cameraYaw = mc.player.getYaw();
        upperBound = (float) yLevel.get() - 0.3f;
        lowerBound = upperBound + 0.5f;

        pitchingDown = true;

        if (takeOff.get() && !mc.player.isFallFlying()) {
            ClimbFly climbFly = Modules.get().get(ClimbFly.class);
            climbFly.takeOff.set(true);
            climbFly.targetLevel.set(yLevel.get().doubleValue());
            climbFly.toggle();
        } else {
            mc.player.setPitch(-40);
        }
    }

    @Override
    public void onDeactivate() {
        for (Class<? extends Module> module : toDisable) {
            if (Modules.get().get(module).isActive())
                Modules.get().get(module).toggle();
        }
        mc.player.setPitch(cameraPitch);
    }

    @EventHandler
    public void onTick(TickEvent.Pre _e) {
        if (Modules.get().isActive(ClimbFly.class))
            return;

        if (pitchingDown && mc.player.getY() <= lowerBound) {
            pitchingDown = false;
        } else if (!pitchingDown && mc.player.getY() >= upperBound) {
            pitchingDown = true;
        }

        if (mc.player.getPos().getY() < upperBound - 1 && recover.get()) {
            if (!Modules.get().isActive(ClimbFly.class))
                Modules.get().get(ClimbFly.class).toggle();
            return;
        }

        mc.player.setPitch((pitchingDown ? pitchUpAngle.get() : pitchDownAngle.get()).floatValue());
        cameraPitch = MathHelper.clamp(cameraPitch, -90, 90);
    }
}