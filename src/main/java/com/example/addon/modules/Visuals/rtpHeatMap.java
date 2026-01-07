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

import java.io.FileWriter;
import java.io.IOException;

class rtpHeatMap extends Module {
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
        .description("Define delay between slot click and file write (in seconds)")
        .defaultValue(10)
        .build()
    );

    private boolean readyToWrite = false;
    private int tickCounter = 0;

    public rtpHeatMap() {
        super(Enhanced.Visuals, "rtp-heatmap", "Collects location data from RTPs for a heatmap");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Wenn wir bereit sind für den Klick
        if (!readyToWrite) {
            // RTP-Befehl senden
            ChatUtils.sendPlayerMsg("/rtp");

            // Slot klicken
            if (mc.player.currentScreenHandler != null && mc.interactionManager != null) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    dim.get(),
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
            }

            // Timer starten
            tickCounter = 0;
            readyToWrite = true;
            return;
        }

        // Timer läuft
        tickCounter++;

        // Wenn Verzögerung abgelaufen ist
        if (tickCounter >= delay.get() * 20) {
            // Koordinaten in CSV schreiben
            try (FileWriter writer = new FileWriter("rtp_heatmap.csv", true)) {
                writer.write(mc.player.getX() + "," + mc.player.getZ() + "\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Timer zurücksetzen
            readyToWrite = false;
        }
    }
}
