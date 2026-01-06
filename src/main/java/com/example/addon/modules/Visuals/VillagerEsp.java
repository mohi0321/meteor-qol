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
import meteordevelopment.meteorclient.settings.Setting;

public class VillagerEsp extends Module {
    private final List<Entity> villagerList = new ArrayList<>();
    private final List<Entity> zombieVillagerList = new ArrayList<>();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showVillager= sgGeneral.add(new BoolSetting.Builder()
        .name("Villagers")
        .description("Enable Villager ESP")
        .defaultValue(true)
        .build()

    );
    private final Setting<SettingColor> villagerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("Villager ESP Color")
        .description("Change the ESP Color of the Villagers Bounding Box")
        .defaultValue(new Color(9, 224, 92))
        .build()
    );
    private final Setting<SettingColor> zombieVillagerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("Zombie Villager ESP Color")
        .description("Change the ESP Color of the Zombie Villagers Bounding Box")
        .defaultValue(new Color(194, 41, 49))
        .build()
    );

    private final Setting<Boolean> showZombieVillager = sgGeneral.add(new BoolSetting.Builder()
        .name("Zombie Villagers")
        .description("Enable Zombie Villager ESP")
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
        mc.world.getEntities().forEach(entity -> {
            if (showVillager.get() && entity instanceof VillagerEntity) villagerList.add(entity);
            if (showZombieVillager.get() && entity instanceof ZombieVillagerEntity) zombieVillagerList.add(entity);
        });

    }

    @EventHandler
    private void onRender(Render3DEvent event){

        for (Entity villager: villagerList){
            event.renderer.box(villager.getBoundingBox(), villagerColor.get(), villagerColor.get(), ShapeMode.Lines, 0);

        }
        for (Entity zombieVillager: zombieVillagerList){
            event.renderer.box(zombieVillager.getBoundingBox(), zombieVillagerColor.get(), zombieVillagerColor.get(), ShapeMode.Lines, 0);
        }
    }

}
