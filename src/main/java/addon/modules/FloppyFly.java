package addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import addon.Bar2Dee2;

public class FloppyFly extends Module {
    public final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Double> settingBoost = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("boost").description("Boost speed.").defaultValue(0.1D).sliderMin(0.0D).sliderMax(0.5D).build());

    public final Setting<Double> settingBoost2 = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("boost2test")
            .description("Boost speed.")
            .defaultValue(-0.1D)
            .sliderRange(-0.5D, 0.0D)
            .build());

    // @SuppressWarnings({ "unchecked", "rawtypes" })
    // public final Setting<Double> settingMaxBoost = this.sgGeneral.add((Setting)
    // ((DoubleSetting.Builder) ((DoubleSetting.Builder) (new
    // DoubleSetting.Builder())
    // .name("max-boost"))
    // .description("Max boost speed."))
    // .defaultValue(4.1D)
    // .sliderMin(0.0D).sliderMax(5.0D)
    // .build());

    public final Setting<Integer> maximumSpeed = this.sgGeneral.add(new IntSetting.Builder()
            .name("max-speed")
            .description("Max speed.")
            .defaultValue(Integer.valueOf(37))
            .sliderMin(0).sliderMax(100)
            .build());

    public final Setting<Double> boostPitch = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("boost pitch")
            .description("boost pitch")
            .defaultValue(-4.0D)
            .sliderMin(0.0D).sliderMax(-20.0D)
            .build());

    // Track if the player is currently looking up
    // private boolean isLookingUp = false;

    public FloppyFly() {
        super(Bar2Dee2.BAR, "FloppyFly", "FloppyFly");
    }

    @EventHandler
    public void onClientMove(PlayerMoveEvent event) {
        if (this.mc.options.sneakKey.isPressed() && !this.mc.player.isOnGround())
            this.mc.player.setVelocity(new Vec3d(0.0D, (this.mc.player.getVelocity()).y, 0.0D));
    }

    @EventHandler
    private void TickEvent(TickEvent.Pre e) {

        // double currentVel = Math.abs((this.mc.player.getVelocity()).x) +
        // Math.abs((this.mc.player.getVelocity()).y) +
        // Math.abs((this.mc.player.getVelocity()).z);
        float radianYaw = (float) Math.toRadians(this.mc.player.getYaw());
        float boost = ((Double) this.settingBoost.get()).floatValue();
        float boost2 = ((Double) this.settingBoost2.get()).floatValue();
        if (this.mc.player.isFallFlying()) {
            if (Math.round(Utils.getPlayerSpeed().horizontalLength()) > ((Integer) this.maximumSpeed.get())
                    .intValue()) {
                this.mc.player.addVelocity((MathHelper.sin(radianYaw) * -boost2), 0.0D,
                        (MathHelper.cos(radianYaw) * boost2));
                return;
            }
            if (Math.round(Utils.getPlayerSpeed().horizontalLength()) < ((Integer) this.maximumSpeed.get()).intValue())
                if (this.mc.options.backKey.isPressed()) {
                    this.mc.player.addVelocity((MathHelper.sin(radianYaw) * boost), 0.0D,
                            (MathHelper.cos(radianYaw) * -boost));
                } else if (this.mc.player.getPitch() > boostPitch.get()) {
                    this.mc.player.addVelocity((MathHelper.sin(radianYaw) * -boost), 0.0D,
                            (MathHelper.cos(radianYaw) * boost));
                }
        }
    }
}