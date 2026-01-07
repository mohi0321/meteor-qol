package com.example.addon.modules.Visuals;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;

public class InventoryInteractionDebug extends Module {
    public InventoryInteractionDebug() {
        super(Enhanced.Visuals, "inventory-debug", "Debugs inventory interactions by printing slot IDs to chat.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ClickSlotC2SPacket packet) {
            ChatUtils.info("Slot ID: " + packet.slot());
        }
    }
}

