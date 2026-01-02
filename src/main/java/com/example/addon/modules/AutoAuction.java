package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;

public class AutoAuction extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> itemSetting = sgGeneral.add(new StringSetting.Builder()
        .name("item")
        .defaultValue("minecraft:enchanted_book")
        .description("Item ID to auction (e.g., minecraft:diamond)")
        .build()
    );

    private final Setting<String> priceSetting = sgGeneral.add(new StringSetting.Builder()
        .name("price")
        .defaultValue("799")
        .description("Price to sell the item for")
        .build()
    );

    private final Setting<Integer> repeatDelaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("repeat-delay")
        .description("Delay in seconds before starting the next auction")
        .defaultValue(10)
        .min(1)
        .sliderMax(120)
        .build()
    );

    private enum Step {
        OPEN_AH,
        PRESS_CREATE,
        PICK_ITEM,
        PLACE_ITEM,
        ENTER_PRICE,
        CONFIRM_SIGN,
        EXIT_MENU,
        WAIT_REPEAT
    }

    private Step step;
    private int timer;
    private Item targetItem;
    private String targetPrice;
    private int repeatDelayTicks;

    public AutoAuction() {
        super(Enhanced.CATEGORY, "auto-auction", "Automatically sells items in the auction house.");
    }

    @Override
    public void onActivate() {
        String id = itemSetting.get();
        if (!id.contains(":")) id = "minecraft:" + id;

        targetItem = Registries.ITEM.get(Identifier.tryParse(id));
        targetPrice = priceSetting.get();
        repeatDelayTicks = repeatDelaySetting.get() * 20;

        if (targetItem == Items.AIR) {
            ChatUtils.error("Invalid item ID.");
            toggle();
            return;
        }

        step = Step.OPEN_AH;
        timer = 20; // 1 Sekunde Delay vor der ersten Interaktion
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer-- > 0) return;

        final int ONE_SECOND = 20; // 1 Sekunde = 20 Ticks

        switch (step) {

            case OPEN_AH -> {
                ChatUtils.sendPlayerMsg("/ah");
                step = Step.PRESS_CREATE;
                timer = ONE_SECOND;
            }

            case PRESS_CREATE -> {
                if (mc.player.currentScreenHandler != null && mc.player.currentScreenHandler.slots.size() > 53) {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        53,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    step = Step.PICK_ITEM;
                } else {
                    timer = ONE_SECOND;
                    return;
                }
                timer = ONE_SECOND;
            }

            case PICK_ITEM -> {
                if (mc.player.currentScreenHandler == null || mc.player.currentScreenHandler.slots.size() <= 53) {
                    timer = ONE_SECOND; // Warten, bis GUI fertig geladen ist
                    return;
                }

                boolean found = false;
                int lastSlot = Math.min(89, mc.player.currentScreenHandler.slots.size() - 1);

                for (int i = 54; i <= lastSlot; i++) {
                    ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
                    if (!stack.isEmpty() && stack.getItem() == targetItem) {
                        mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            i,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                        );
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    ChatUtils.warning("Item nicht zwischen Slot 54–" + lastSlot + " gefunden.");
                    toggle();
                    return;
                }

                step = Step.PLACE_ITEM;
                timer = ONE_SECOND;
            }

            case PLACE_ITEM -> {
                if (mc.player.currentScreenHandler != null) {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        6,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    step = Step.ENTER_PRICE;
                    timer = ONE_SECOND;
                } else {
                    timer = ONE_SECOND;
                }
            }

            case ENTER_PRICE -> {
                BlockPos pos = mc.player.getBlockPos();

                mc.player.networkHandler.sendPacket(
                    new UpdateSignC2SPacket(
                        pos,
                        true,
                        targetPrice,
                        "",
                        "",
                        ""
                    )
                );

                step = Step.CONFIRM_SIGN;
                timer = ONE_SECOND;
            }

            case CONFIRM_SIGN -> {
                if (mc.player.currentScreenHandler != null) {
                    mc.player.closeHandledScreen();
                }
                step = Step.EXIT_MENU;
                timer = ONE_SECOND;
            }

            case EXIT_MENU -> {
                if (mc.player.currentScreenHandler != null) {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        6,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                }
                ChatUtils.info("Auction created successfully.");
                step = Step.WAIT_REPEAT;
                timer = repeatDelayTicks; // Wartezeit vor nächster Auktion
            }

            case WAIT_REPEAT -> {
                // Starte neue Auktion
                step = Step.OPEN_AH;
                timer = ONE_SECOND;
            }
        }
    }
}
