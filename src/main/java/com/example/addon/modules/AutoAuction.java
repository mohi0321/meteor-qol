package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
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
        SELECT_ITEM,
        SEND_COMMAND,
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
        currentState = State.SELECT_ITEM;
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
                 // Idle state
            }
            case SELECT_ITEM -> {
                // Parse Item ID
                String[] split = targetItemId.split(":");
                Item item = Registries.ITEM.get(Identifier.of(split[0], split[1]));
                
                if (item == Items.AIR) {
                    ChatUtils.error("Invalid item ID: " + targetItemId);
                    toggle();
                    return;
                }

                // Find Item
                FindItemResult result = InvUtils.find(item);
                if (!result.found()) {
                    ChatUtils.warning("Item not found: " + targetItemId);
                    toggle();
                    return;
                }

                // Select in hotbar
                if (result.isHotbar()) {
                    InvUtils.swap(result.slot(), true);
                } else {
                    InvUtils.move().from(result.slot()).toHotbar(mc.player.getInventory().selectedSlot);
                    InvUtils.swap(mc.player.getInventory().selectedSlot, true);
                }
                
                ChatUtils.info("Selected item " + targetItemId);
                currentState = State.SEND_COMMAND;
                timer = 10;
            }
            case SEND_COMMAND -> {
                ChatUtils.sendPlayerMsg("/ah sell " + targetPrice);
                ChatUtils.info("Sold item for " + targetPrice);
                currentState = State.FINISH;
                timer = 10;
            }
            case FINISH -> {
                // Done. Repeat after delay?
                currentState = State.SELECT_ITEM;
                timer = actionDelayTicks;
            }
        }
    }
}
