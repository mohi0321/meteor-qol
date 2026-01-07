package com.example.addon.modules.Automation;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoAuction extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> item = sgGeneral.add(
        new StringSetting.Builder()
            .name("item")
            .description("The item to auction.")
            .defaultValue("minecraft:enchanted_book")
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("The delay between auctions in seconds.")
            .defaultValue(10)
            .min(0)
            .max(60)
            .build()
    );

    private final Setting<Boolean> repeat = sgGeneral.add(
        new BoolSetting.Builder()
            .name("repeat")
            .description("Repeat auctions automatically.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> priceSetting = sgGeneral.add(
        new IntSetting.Builder()
            .name("price")
            .description("Auction price.")
            .defaultValue(1000)
            .min(1)
            .build()
    );

    public AutoAuction() {
        super(Enhanced.Automation, "auto-auction", "Automatically auction items.");
    }

    private enum Step {
        OPEN_AH,
        PRESS_CREATE,
        FIND_ITEM,
        PLACE_ITEM,
        ENTER_PRICE,
        EXIT_SIGN,
        CONFIRM_AUCTION
    }

    private Step step = Step.OPEN_AH;
    private int timer = 0;

    @Override
    public void onActivate() {
        step = Step.OPEN_AH;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Timer abarbeiten
        if (timer > 0) {
            timer--;
            return;
        }

        switch (step) {
            case OPEN_AH -> {
                ChatUtils.sendPlayerMsg("/ah");
                step = Step.PRESS_CREATE;
                timer = 20;
            }
            case PRESS_CREATE -> {
                if (mc.player.currentScreenHandler == null) {
                    step = Step.OPEN_AH;
                    timer = 20;
                    return;
                }

                if (mc.interactionManager == null) return;

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    53,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                step = Step.FIND_ITEM;
                timer = 20;
            }
            case FIND_ITEM -> {
                Identifier id = Identifier.tryParse(item.get());
                if (id == null) {
                    ChatUtils.error("Invalid item ID: " + item.get());
                    toggle();
                    return;
                }

                Item targetItem = Registries.ITEM.get(id);
                FindItemResult result = InvUtils.find(targetItem);

                if (!result.found()) {
                    ChatUtils.error("Item not found: " + item.get());
                    toggle();
                    return;
                }

                if (mc.interactionManager == null || mc.player.currentScreenHandler == null) return;

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    result.slot(),
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                step = Step.PLACE_ITEM;
                timer = 20;
            }
            case PLACE_ITEM -> {
                if (mc.interactionManager == null || mc.player.currentScreenHandler == null) return;

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    6,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                step = Step.ENTER_PRICE;
                timer = 20;
            }
            case ENTER_PRICE -> {
                if (mc.currentScreen instanceof SignEditScreen screen) {
                    // Preis als Zeichen eingeben
                    String text = String.valueOf(priceSetting.get());
                    for (char c : text.toCharArray()) {
                        screen.charTyped(new CharInput(c, 0)); // 2 Argumente: char + modifiers
                    }

                    // Schild schließen / ESC
                    screen.keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE, 0, 0)); // 3 Argumente: keyCode, scanCode, modifiers
                }

                step = Step.EXIT_SIGN;
                timer = 20;
            }



            case EXIT_SIGN -> {
                step = Step.CONFIRM_AUCTION;
                timer = 20;
            }
            case CONFIRM_AUCTION -> {
                if (mc.interactionManager == null || mc.player.currentScreenHandler == null) return;

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    6,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );

                if (repeat.get()) {
                    step = Step.OPEN_AH;
                    timer = delay.get() * 20;
                } else {
                    toggle();
                }
            }
        }
    }
}
