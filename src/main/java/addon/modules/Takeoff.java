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
import addon.Bar2Dee2;

public class Takeoff extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch")
            .description("Inital pitch")
            .defaultValue(-3)
            .sliderRange(-5, 5)
            .build());

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("Delay")
            .description("Delay between jumping and deploying elytra")
            .defaultValue(2)
            .sliderRange(2, 20)
            .build());

    public Takeoff() {
        super(Bar2Dee2.CURRY, "Takeoff", "Take off with elytra");
    }

    private final List<Class<? extends Module>> incompat = List.of(
            Lander.class,
            ElytraFly.class);

    private final List<Class<? extends Module>> require = List.of(
            FloppyFly.class);

    int tick, deploy;

    @Override
    public void onActivate() {
        if (!mc.player.isOnGround()) {
            error("Player is not on ground.");
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

        deploy = delay.get();
        tick = 0;

        mc.player.setPitch(pitch.get().floatValue());
        mc.options.jumpKey.setPressed(true);
    }

    @Override
    public void onDeactivate() {
        mc.options.jumpKey.setPressed(false);
    }

    @EventHandler
    public void onTick(TickEvent.Pre _e) {
        if (tick > deploy + 1) {
            if (Modules.get().isActive(Takeoff.class))
                toggle();
            return;
        }

        if (tick == 1)
            mc.options.jumpKey.setPressed(false);
        else if (tick >= deploy + 1)
            mc.options.jumpKey.setPressed(false);
        else if (tick >= deploy)
            mc.options.jumpKey.setPressed(true);

        tick++;
    }
}