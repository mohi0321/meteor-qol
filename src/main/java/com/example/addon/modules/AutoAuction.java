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

    private final Setting<Integer> delaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .defaultValue(10)
        .min(1)
        .sliderMax(60)
        .build()
    );

    private enum Step {
        OPEN_AH,
        PICK_ITEM,
        PLACE_ITEM,
        ENTER_PRICE,
        CONFIRM,
        FINISH
    }

    private Step step;
    private int timer;
    private int repeatDelay;
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
        repeatDelay = delaySetting.get() * 20;

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
                step = Step.PICK_ITEM;
                timer = 20; // 1 second
            }

            case PICK_ITEM -> {
                FindItemResult result = InvUtils.find(targetItem);
                if (!result.found()) {
                    ChatUtils.warning("Item not found.");
                    toggle();
                    return;
                }

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
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    6, // server-specific slot
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
                        new String[] { targetPrice, "", "", "" }
                    )
                );

                step = Step.CONFIRM;
                timer = 20;
            }

            case CONFIRM -> {
                mc.player.closeHandledScreen();
                ChatUtils.info("Auctioned item for " + targetPrice);

                step = Step.FINISH;
                timer = 20;
            }

            case FINISH -> {
                step = Step.OPEN_AH;
                timer = repeatDelay;
            }
        }
    }
}
