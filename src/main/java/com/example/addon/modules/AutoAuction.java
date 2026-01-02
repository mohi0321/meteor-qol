package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class AutoAuction extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Settings
    private final Setting<String> itemSetting = sgGeneral.add(new StringSetting.Builder()
        .name("item")
        .description("Item to sell. Format: 'item_id' (e.g. minecraft:diamond)")
        .defaultValue("minecraft:enchanted_book")
        .build()
    );

    private final Setting<String> priceSetting = sgGeneral.add(new StringSetting.Builder()
        .name("price")
        .description("Price to sell items for.")
        .defaultValue("799")
        .build()
    );

    private final Setting<Integer> delaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in seconds between auction actions.")
        .defaultValue(10)
        .min(1)
        .sliderMax(60)
        .build()
    );

    private enum State {
        WAITING,
        OPENING_AH,
        WAIT_FOR_AH_GUI,
        SELECT_ITEM,
        WAIT_FOR_SIGN,
        SET_PRICE,
        CONFIRM,
        FINISH
    }

    private State currentState = State.WAITING;
    private int timer = 0;
    private int actionDelayTicks;
    private String targetItemId;
    private String targetPrice;

    public AutoAuction() {
        super(Enhanced.CATEGORY, "auto-auction", "Automatically sells items in the auction house.");
    }

    @Override
    public void onActivate() {
        currentState = State.OPENING_AH;
        targetItemId = itemSetting.get();
        if (!targetItemId.contains(":")) {
            targetItemId = "minecraft:" + targetItemId;
        }
        targetPrice = priceSetting.get();
        actionDelayTicks = delaySetting.get() * 20;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        switch (currentState) {
            case WAITING -> {
                 // Idle state, usually not reached unless looping
            }
            case OPENING_AH -> {
                ChatUtils.sendPlayerMsg("/ah");
                currentState = State.WAIT_FOR_AH_GUI;
                timer = 20; // Give it a second to receive packet/open gui
            }
            case WAIT_FOR_AH_GUI -> {
                if (mc.currentScreen != null || mc.player.currentScreenHandler != null) {
                    // Assuming AH opens a GUI (Chest or specific screen)
                    // We can verify Container type if we knew it, but for now assuming non-null is enough
                    currentState = State.SELECT_ITEM;
                    timer = 10;
                } else {
                    // Timeout or failed?
                    // For now just retry opening
                     currentState = State.OPENING_AH;
                }
            }
            case SELECT_ITEM -> {
                Item item = Registries.ITEM.get(new Identifier(targetItemId));
                if (item == Items.AIR) {
                    ChatUtils.error("Invalid item ID: " + targetItemId);
                    toggle();
                    return;
                }

                FindItemResult result = InvUtils.find(item);
                if (!result.found()) {
                    ChatUtils.warning("Item not found: " + targetItemId);
                    toggle();
                    return;
                }

                // Click the item to sell it
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId, 
                    result.slot(), 
                    0, 
                    net.minecraft.screen.slot.SlotActionType.PICKUP, 
                    mc.player
                );
                
                ChatUtils.info("Clicked item in slot " + result.slot());
                currentState = State.WAIT_FOR_SIGN;
                timer = 20;
            }
            case WAIT_FOR_SIGN -> {
                if (mc.currentScreen instanceof SignEditScreen) {
                    currentState = State.SET_PRICE;
                    timer = 5;
                }
            }
            case SET_PRICE -> {
                 if (mc.currentScreen instanceof SignEditScreen screen) {
                    screen.setText(0, targetPrice);
                    // Finish editing
                    screen.finishEditing(); // This closes screen and sends packet
                    ChatUtils.info("Set price to " + targetPrice);
                    currentState = State.CONFIRM;
                    timer = 20; 
                } else {
                    // Sign closed unexpectedly?
                    currentState = State.FINISH;
                }
            }
            case CONFIRM -> {
                // If there is a confirmation GUI, handle it here. 
                // The original code had clickSlot(6).
                // Assuming confirmation GUI is now open.
                 mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId, 
                    6, 
                    0, 
                    net.minecraft.screen.slot.SlotActionType.PICKUP, 
                    mc.player
                );
                ChatUtils.info("Confirmed auction.");
                currentState = State.FINISH;
                timer = 10;
            }
            case FINISH -> {
                // Cycle done.
                // If we want to repeat, we go back to start after long delay.
                // Or just toggle off for single use.
                // For "AutoAuction", maybe loop? Let's obey the delay setting for loop.
                mc.player.closeHandledScreen();
                currentState = State.OPENING_AH;
                timer = actionDelayTicks;
            }
        }
    }
}
