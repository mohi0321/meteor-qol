package com.example.addon.modules.Visuals;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class VillagerESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<DetectionMode> detectionMode = sgGeneral.add(
        new EnumSetting.Builder<DetectionMode>()
            .name("detection-mode")
            .defaultValue(DetectionMode.Both)
            .build()
    );

    private final Setting<Boolean> showTracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> villagerTracerColor = sgRender.add(
        new ColorSetting.Builder()
            .name("villager-color")
            .defaultValue(new SettingColor(0, 255, 0, 180))
            .visible(() -> showTracers.get())
            .build()
    );

    private final Setting<SettingColor> zombieVillagerTracerColor = sgRender.add(
        new ColorSetting.Builder()
            .name("zombie-color")
            .defaultValue(new SettingColor(255, 0, 0, 180))
            .visible(() -> showTracers.get())
            .build()
    );

    private final Setting<Boolean> enableWebhook = sgWebhook.add(
        new BoolSetting.Builder()
            .name("webhook")
            .defaultValue(false)
            .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(
        new StringSetting.Builder()
            .name("webhook-url")
            .defaultValue("")
            .visible(enableWebhook::get)
            .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("notification-mode")
            .defaultValue(Mode.Both)
            .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(
        new BoolSetting.Builder()
            .name("toggle-on-find")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(
        new BoolSetting.Builder()
            .name("disconnect")
            .defaultValue(false)
            .build()
    );

    private final Set<Integer> detected = new HashSet<>();
    private final HttpClient http = HttpClient.newHttpClient();

    public VillagerESP() {
        super(Enhanced.Visuals, "villager-esp", "Detects villagers and zombie villagers.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        Set<Integer> found = new HashSet<>();
        int villagers = 0;
        int zombies = 0;

        Vec3d playerEye = mc.player.getEyePos();

        for (Entity e : mc.world.getEntities()) {
            boolean valid = false;
            Color color = null;

            if (e instanceof VillagerEntity &&
                (detectionMode.get() == DetectionMode.Villagers || detectionMode.get() == DetectionMode.Both)) {
                valid = true;
                villagers++;
                color = new Color(villagerTracerColor.get());
            }

            if (e instanceof ZombieVillagerEntity &&
                (detectionMode.get() == DetectionMode.ZombieVillagers || detectionMode.get() == DetectionMode.Both)) {
                valid = true;
                zombies++;
                color = new Color(zombieVillagerTracerColor.get());
            }

            if (!valid) continue;

            found.add(e.getId());

            if (showTracers.get()) {
                Vec3d pos = e.getLerpedPos(event.tickDelta)
                    .add(0, e.getHeight() / 2.0, 0);

                event.renderer.line(
                    playerEye.x, playerEye.y, playerEye.z,
                    pos.x, pos.y, pos.z,
                    color
                );
            }
        }

        if (!found.isEmpty() && !found.equals(detected)) {
            detected.addAll(found);
            notifyFound(villagers, zombies);
        }

        if (found.isEmpty()) detected.clear();
    }

    private void notifyFound(int v, int z) {
        String msg = buildMessage(v, z);

        if (notificationMode.get() != Mode.Toast)
            info("(highlight)%s", msg);

        if (notificationMode.get() != Mode.Chat)
            mc.getToastManager().add(new MeteorToast(Items.EMERALD, title, msg));

        if (enableWebhook.get()) sendWebhook(msg);
        if (toggleOnFind.get()) toggle();
        if (enableDisconnect.get()) disconnectFromServer(msg);
    }

    private String buildMessage(int v, int z) {
        if (v > 0 && z > 0) return v + " villagers & " + z + " zombie villagers detected!";
        if (v > 0) return v == 1 ? "Villager detected!" : v + " villagers detected!";
        return z == 1 ? "Zombie villager detected!" : z + " zombie villagers detected!";
    }

    private void sendWebhook(String message) {
        if (webhookUrl.get().isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                String json = "{\"content\":\"" + message + "\"}";
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        });
    }

    private void disconnectFromServer(String reason) {
        if (mc.getNetworkHandler() != null)
            mc.getNetworkHandler().getConnection()
                .disconnect(Text.literal(reason));
    }

    public enum Mode { Chat, Toast, Both }
    public enum DetectionMode { Villagers, ZombieVillagers, Both }
}
