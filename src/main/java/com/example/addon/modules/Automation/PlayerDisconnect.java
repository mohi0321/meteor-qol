package com.example.addon.modules.Automation;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class PlayerDisconnect extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> disconnectOnPlayer = sgGeneral.add(
        new BoolSetting.Builder()
            .name("disconnect-on-player")
            .description("Disconnects when another player joins.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> webhookEnabled = sgGeneral.add(
        new BoolSetting.Builder()
            .name("webhook-enabled")
            .description("Send Discord webhook on disconnect.")
            .defaultValue(false)
            .build()
    );

    private final Setting<String> webhookURL = sgGeneral.add(
        new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook URL.")
            .defaultValue("")
            .build()
    );

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private int lastPlayerCount = -1;

    public PlayerDisconnect() {
        super(Enhanced.Automation, "player-disconnect",
            "Disconnects from the server when a player joins.");
    }

    @Override
    public void onActivate() {
        lastPlayerCount = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        if (!disconnectOnPlayer.get()) return;

        int currentPlayerCount = mc.world.getPlayers().size();

        if (lastPlayerCount == -1) {
            lastPlayerCount = currentPlayerCount;
            return;
        }

        if (currentPlayerCount > lastPlayerCount) {
            if (webhookEnabled.get()) {
                sendWebhookNotification(currentPlayerCount);
            }

            mc.getNetworkHandler().getConnection().disconnect(
                Text.literal("Disconnected: player joined (" + currentPlayerCount + ")")
            );

            toggle();
        }

        lastPlayerCount = currentPlayerCount;
    }

    private void sendWebhookNotification(int playerCount) {
        String url = webhookURL.get().trim();
        if (url.isEmpty()) {
            warning("Webhook URL not set.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String server = mc.getCurrentServerEntry() != null
                    ? mc.getCurrentServerEntry().address
                    : "Singleplayer";

                String coords = String.format(
                    "X: %.0f, Y: %.0f, Z: %.0f",
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ()
                );

                String json = String.format("""
                {
                  "username": "Guardian",
                  "embeds": [{
                    "title": "🚨 Player Joined",
                    "description": "A new player joined the server.",
                    "color": 16711680,
                    "fields": [
                      { "name": "Server", "value": "%s", "inline": true },
                      { "name": "Players", "value": "%d", "inline": true },
                      { "name": "Coordinates", "value": "%s", "inline": false }
                    ],
                    "footer": { "text": "PlayerDisconnect Module" }
                  }]
                }
                """, server.replace("\"", "\\\""), playerCount, coords);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 204 && response.statusCode() != 200) {
                    error("Webhook failed: " + response.statusCode());
                }

            } catch (IOException | InterruptedException e) {
                error("Webhook error: " + e.getMessage());
            }
        });
    }
}
