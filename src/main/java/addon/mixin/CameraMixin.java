package addon.mixin;

import java.util.List;

/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import meteordevelopment.meteorclient.mixininterface.ICamera;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import addon.modules.ClimbFly;
import addon.modules.HighwayDigger;
import addon.modules.HighwayFly;
import addon.modules.Lander;
import addon.modules.LevelFly;

@Mixin(Camera.class)
public abstract class CameraMixin implements ICamera {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    private float yaw;
    @Shadow
    private float pitch;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Unique
    private float tickDelta;

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateHead(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
            float tickDelta, CallbackInfo info) {
        this.tickDelta = tickDelta;
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void onUpdateSetRotationArgs(Args args) {
        final List<Class<? extends Module>> incompats = List.of(
                Freecam.class,
                HighwayBuilder.class,
                FreeLook.class);

        if (incompats.stream().anyMatch((module) -> Modules.get().isActive(module)))
            return;

        LevelFly levelFly = Modules.get().get(LevelFly.class);
        ClimbFly climbFly = Modules.get().get(ClimbFly.class);
        Lander lander = Modules.get().get(Lander.class);
        HighwayFly highwayFly = Modules.get().get(HighwayFly.class);
        HighwayDigger highwayDigger = Modules.get().get(HighwayDigger.class);

        if (highwayFly.isActive()) {
            args.set(0, highwayFly.cameraYaw);
            args.set(1, highwayFly.cameraPitch);
        } else if (highwayDigger.isActive()) {
            args.set(0, highwayDigger.cameraYaw);
            args.set(1, highwayDigger.cameraPitch);
        } else if (levelFly.isActive()) {
            args.set(0, levelFly.cameraYaw);
            args.set(1, levelFly.cameraPitch);
        } else if (lander.isActive()) {
            args.set(0, lander.cameraYaw);
            args.set(1, lander.cameraPitch);
        } else if (climbFly.isActive()) {
            args.set(0, climbFly.cameraYaw);
            args.set(1, climbFly.cameraPitch);
        }
    }

    @Override
    public void setRot(double yaw, double pitch) {
        setRotation((float) yaw, (float) MathHelper.clamp(pitch, -90, 90));
    }
}
