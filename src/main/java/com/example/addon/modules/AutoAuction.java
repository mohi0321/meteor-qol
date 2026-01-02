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
        .build()
    );

    private final Setting<String> priceSetting = sgGeneral.add(new StringSetting.Builder()
        .name("price")
        .defaultValue("799")
        .build()
    );

    private enum Step {
        OPEN_AH,
        PRESS_CREATE,
        PICK_ITEM,
        PLACE_ITEM,
        ENTER_PRICE,
        CONFIRM_SIGN,
        EXIT_MENU
    }

    private Step step;
    private int timer;
    private Item targetItem;
    private String targetPrice;

    public AutoAuction() {
        super(Enhanced.CATEGORY, "auto-auction", "Automatically sells items in the auction house.");
    }

    @Override
    public void onActivate() {
        String id = itemSetting.get();
        if (!id.contains(":")) id = "minecraft:" + id;

        targetItem = Registries.ITEM.get(Identifier.tryParse(id));
        targetPrice = priceSetting.get();

        if (targetItem == Items.AIR) {
            ChatUtils.error("Invalid item ID.");
            toggle();
            return;
        }

        step = Step.OPEN_AH;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer-- > 0) return;

        switch (step) {

            case OPEN_AH -> {
                ChatUtils.sendPlayerMsg("/ah");
                step = Step.PRESS_CREATE;
                timer = 20;
            }

            case PRESS_CREATE -> {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    53,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                step = Step.PICK_ITEM;
                timer = 20;
            }

            case PICK_ITEM -> {
                boolean found = false;

                for (int i = 54; i <= 89; i++) {
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
                    ChatUtils.warning("Item NOT found between slot 54 and 89.");
                    toggle();
                    return;
                }

                step = Step.PLACE_ITEM;
                timer = 20;
            }

            case PLACE_ITEM -> {
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
                timer = 20;
            }

            case CONFIRM_SIGN -> {
                mc.player.closeHandledScreen();
                step = Step.EXIT_MENU;
                timer = 20;
            }

            case EXIT_MENU -> {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    6,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );

                ChatUtils.info("Auction created successfully.");
                toggle();
            }
        }
    }
}
