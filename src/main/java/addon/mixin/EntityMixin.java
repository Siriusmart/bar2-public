package addon.mixin;

/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import net.minecraft.entity.Entity;
import addon.modules.ClimbFly;
import addon.modules.HighwayDigger;
import addon.modules.HighwayFly;
import addon.modules.Lander;
import addon.modules.LevelFly;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void updateChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != mc.player)
            return;

        final List<Class<? extends Module>> incompats = List.of(
                Freecam.class,
                HighwayBuilder.class,
                FreeLook.class);

        if (incompats.stream().anyMatch((module) -> Modules.get().isActive(module)))
            return;

        FreeLook freeLook = Modules.get().get(FreeLook.class);
        LevelFly levelFly = Modules.get().get(LevelFly.class);
        ClimbFly climiFly = Modules.get().get(ClimbFly.class);
        Lander lander = Modules.get().get(Lander.class);
        HighwayFly highwayFly = Modules.get().get(HighwayFly.class);
        HighwayDigger highwayDigger = Modules.get().get(HighwayDigger.class);

        if (highwayFly.isActive()) {
            float sensitivity = freeLook.sensitivity.get().floatValue();
            highwayFly.cameraPitch += (float) (cursorDeltaY / sensitivity);
            highwayFly.cameraYaw += (float) (cursorDeltaX / sensitivity);

            if (Math.abs(levelFly.cameraPitch) > 90.0F)
                levelFly.cameraPitch = levelFly.cameraPitch > 0.0F ? 90.0F : -90.0F;
            ci.cancel();
        } else if (highwayDigger.isActive()) {
            float sensitivity = freeLook.sensitivity.get().floatValue();
            highwayDigger.cameraPitch += (float) (cursorDeltaY / sensitivity);
            highwayDigger.cameraYaw += (float) (cursorDeltaX / sensitivity);

            if (Math.abs(highwayDigger.cameraPitch) > 90.0F)
                highwayDigger.cameraPitch = highwayDigger.cameraPitch > 0.0F ? 90.0F : -90.0F;
            ci.cancel();
        } else if (levelFly.isActive()) {
            float sensitivity = freeLook.sensitivity.get().floatValue();
            levelFly.cameraPitch += (float) (cursorDeltaY / sensitivity);
            levelFly.cameraYaw += (float) (cursorDeltaX / sensitivity);
            mc.player.setYaw(levelFly.cameraYaw);

            if (Math.abs(levelFly.cameraPitch) > 90.0F)
                levelFly.cameraPitch = levelFly.cameraPitch > 0.0F ? 90.0F : -90.0F;
            ci.cancel();
        } else if (lander.isActive()) {
            float sensitivity = freeLook.sensitivity.get().floatValue();
            lander.cameraPitch += (float) (cursorDeltaY / sensitivity);
            lander.cameraYaw += (float) (cursorDeltaX / sensitivity);
            mc.player.setYaw(lander.cameraYaw);

            if (Math.abs(lander.cameraPitch) > 90.0F)
                lander.cameraPitch = lander.cameraPitch > 0.0F ? 90.0F : -90.0F;
            ci.cancel();
        } else if (climiFly.isActive()) {
            float sensitivity = freeLook.sensitivity.get().floatValue();
            climiFly.cameraPitch += (float) (cursorDeltaY / sensitivity);
            climiFly.cameraYaw += (float) (cursorDeltaX / sensitivity);
            mc.player.setYaw(climiFly.cameraYaw);

            if (Math.abs(climiFly.cameraPitch) > 90.0F)
                climiFly.cameraPitch = climiFly.cameraPitch > 0.0F ? 90.0F : -90.0F;
            ci.cancel();
        }
    }
}