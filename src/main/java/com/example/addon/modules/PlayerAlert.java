package com.example.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import com.example.addon.AddonTemplate;

public class PlayerAlert extends Module {
    private static final long ALERT_COOLDOWN = 5000; // 5 seconds between alerts per player
    private java.util.Map<String, Long> lastAlertTime = new java.util.HashMap<>();

    private final Settings settings = new Settings();

    private final BoolSetting enableTeleport = settings.getDefaultCategory().add(new BoolSetting.Builder()
        .name("teleport-on-alert")
        .description("Automatically teleport to spawn when a player is detected.")
        .defaultValue(false)
        .build());

    private final IntSetting teleportDelay = settings.getDefaultCategory().add(new IntSetting.Builder()
        .name("teleport-delay")
        .description("Delay in seconds before teleporting (after player detected).")
        .defaultValue(5)
        .min(1)
        .max(60)
        .build());

    private long lastTeleportTime = 0;

    public PlayerAlert() {
        super(AddonTemplate.CATEGORY, "player-alert", "Alerts you when a player enters render distance.");
    }

    @Override
    public Settings getSettings() {
        return settings;
    }

    @Override
    public void onDeactivate() {
        lastAlertTime.clear();
        lastTeleportTime = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long currentTime = System.currentTimeMillis();
        boolean playerDetected = false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            // Skip yourself
            if (player == mc.player) continue;

            playerDetected = true;
            String playerName = player.getName().getString();
            double distance = mc.player.distanceTo(player);

            // Check if we've already alerted about this player recently
            long lastAlert = lastAlertTime.getOrDefault(playerName, 0L);
            if (currentTime - lastAlert >= ALERT_COOLDOWN) {
                // Send toast notification
                ToastManager toastManager = mc.getToastManager();
                SystemToast toast = SystemToast.create(
                    mc,
                    SystemToast.Type.TUTORIAL_HINT,
                    Text.literal("Player Detected!"),
                    Text.literal(playerName + " (" + String.format("%.1f", distance) + "m)")
                );
                toastManager.add(toast);

                // Update last alert time
                lastAlertTime.put(playerName, currentTime);
            }
        }

        // Handle teleport on player detection
        if (enableTeleport.get() && playerDetected) {
            long delayMs = (long) teleportDelay.get() * 1000;
            if (currentTime - lastTeleportTime >= delayMs) {
                mc.player.networkHandler.sendChatCommand("spawn");
                lastTeleportTime = currentTime;
            }
        }
    }
}
