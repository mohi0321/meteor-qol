package com.example.addon.modules.Automation;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class AutoChat extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> message = sgGeneral.add(
        new StringSetting.Builder()
            .name("message")
            .description("The message to send.")
            .defaultValue("Hello World!")
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("The delay in seconds.")
            .defaultValue(20)
            .min(1)
            .build()
    );

    private double timer;

    public AutoChat() {
        super(Enhanced.Automation, "auto-chat", "Automatically sends a message after a delay.");
    }

    @Override
    public void onActivate() {
        timer = (delay.get()*20)+0.1 + Math.random() * 0.9;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!isActive()) return;

        if (timer <= 0) {
            ChatUtils.sendPlayerMsg(message.get());
            timer = (delay.get()*20);
        } else {
            timer--;
        }
    }
}
