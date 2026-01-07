package com.example.addon.modules.Visuals;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

import java.io.FileWriter;
import java.io.IOException;

public class rtpHeatMap extends Module {
    private final SettingGroup sgDimension = settings.createGroup("Dimension");
    private final SettingGroup sgDelay = settings.createGroup("Delay");

    private final Setting<Integer> dim = sgDimension.add(new IntSetting.Builder()
        .name("dimension")
        .description("Set Slot for Dimension")
        .defaultValue(2)
        .build()
    );

    public final Setting<Integer> delay = sgDelay.add(new IntSetting.Builder()
        .name("Delay")
        .description("Define Delay between operations (in ticks)")
        .defaultValue(10)
        .build()
    );

    public rtpHeatMap() {
        super(Enhanced.Visuals, "rtp-heatmap", "Collects location data from RTPs for a heatmap");
    }

    private enum State { IDLE, WAITING, DELAY }
    private State state = State.IDLE;
    private Vec3d pp;
    private int tickDelayCounter = 0;

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case IDLE -> {
                ChatUtils.sendPlayerMsg("/rtp");
                pp = mc.player.getPos();
                tickDelayCounter = delay.get()*20;
                state = State.DELAY;
            }

            case DELAY -> {
                if (tickDelayCounter > 0) {
                    tickDelayCounter--;
                } else {
                    state = State.WAITING;
                }
            }

            case WAITING -> {
                boolean moved = !mc.player.getPos().equals(pp);
                if (moved) {
                    clickSlot();
                    writeData();
                    tickDelayCounter = delay.get()*20;
                    state = State.DELAY;
                }
            }
        }
    }

    private void clickSlot() {
        if (mc.player != null && mc.player.currentScreenHandler != null && mc.interactionManager != null) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                dim.get(),
                0,
                SlotActionType.PICKUP,
                mc.player
            );
        }
    }

    private void writeData() {
        try (FileWriter writer = new FileWriter("rtp_heatmap.csv", true)) {
            if (mc.player != null) {
                writer.write(mc.player.getX() + " " + mc.player.getZ() + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
