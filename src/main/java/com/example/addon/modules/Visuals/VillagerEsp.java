package com.example.addon.modules.Visuals;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import meteordevelopment.meteorclient.settings.ColorSetting;
import net.minecraft.entity.Entity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.settings.Setting;
import org.joml.Vector3f;

public class VillagerEsp extends Module {
    private final List<Entity> villagerList = new ArrayList<>();
    private final List<Entity> zombieVillagerList = new ArrayList<>();
    private final SettingGroup sgVillager = settings.createGroup("Villager");
    private final SettingGroup sgZombie = settings.createGroup("Zombie");
    private final SettingGroup sgTracer = settings.createGroup("Tracer");

    private final Setting<Boolean> showVillager= sgVillager.add(new BoolSetting.Builder()
        .name("Villagers")
        .description("Enable Villager ESP")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> villagerColor = sgVillager.add(new ColorSetting.Builder()
        .name("Villager ESP Color")
        .description("Change the ESP Color of the Villagers Bounding Box")
        .defaultValue(new Color(9, 224, 92))
        .build()
    );

    private final Setting<SettingColor> zombieVillagerColor = sgZombie.add(new ColorSetting.Builder()
        .name("Zombie Villager ESP Color")
        .description("Change the ESP Color of the Zombie Villagers Bounding Box")
        .defaultValue(new Color(194, 41, 49))
        .build()
    );

    private final Setting<Boolean> showZombieVillager = sgZombie.add(new BoolSetting.Builder()
        .name("Zombie Villagers")
        .description("Enable Zombie Villager ESP")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showTracers = sgTracer.add(new BoolSetting.Builder()
        .name("Show Tracers")
        .defaultValue(true)
        .build()
    );

    public VillagerEsp() {
        super(Enhanced.Visuals, "VillagerESP", "locate Zombie and regular Villagers");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event){
        if (mc == null || mc.player == null || mc.world == null) return;
        villagerList.clear();
        zombieVillagerList.clear();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof VillagerEntity && showVillager.get()) villagerList.add(entity);
            else if (entity instanceof ZombieVillagerEntity && showZombieVillager.get()) zombieVillagerList.add(entity);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc == null || mc.player == null || mc.world == null) return;

        // Kamera-Position
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        // Blickrichtung als Vector3f → Umwandlung zu Vec3d
        Vector3f dir = mc.gameRenderer.getCamera().getHorizontalPlane();
        Vec3d camDir = new Vec3d(dir.x(), dir.y(), dir.z());

        // Startpunkt leicht vor der Kamera, damit Tracer nicht wegclippt
        Vec3d start = camPos.add(camDir.multiply(0.1));

        // Normale Villager
        for (Entity villager : villagerList) {
            // Bounding Box
            event.renderer.box(
                villager.getBoundingBox(),
                villagerColor.get(),
                villagerColor.get(),
                ShapeMode.Lines,
                0
            );

            // Tracer
            if (showTracers.get()) {
                event.renderer.line(
                    start.x, start.y, start.z,
                    villager.getX(),
                    villager.getY() + villager.getHeight() / 2,
                    villager.getZ(),
                    villagerColor.get(),
                    villagerColor.get()
                );
            }
        }

        // Zombie Villager
        for (Entity zombieVillager : zombieVillagerList) {
            // Bounding Box
            event.renderer.box(
                zombieVillager.getBoundingBox(),
                zombieVillagerColor.get(),
                zombieVillagerColor.get(),
                ShapeMode.Lines,
                0
            );

            // Tracer
            if (showTracers.get()) {
                event.renderer.line(
                    start.x, start.y, start.z,
                    zombieVillager.getX(),
                    zombieVillager.getY() + zombieVillager.getHeight() / 2,
                    zombieVillager.getZ(),
                    zombieVillagerColor.get(),
                    zombieVillagerColor.get()
                );
            }
        }
    }


}
