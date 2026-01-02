package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
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
                    .description("The delay between auctions.")
                    .defaultValue(10)
                    .min(0)
                    .max(60)
                    .build()
    );

    private final Setting<Boolean> repeat = sgGeneral.add(
            new BoolSetting.Builder()
                    .name("repeat")
                    .description("Repeat auctions.")
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
        super(Enhanced.CATEGORY, "auto-auction", "Automatically auction items.");
    }

    private enum Step {
        OPEN_AH,
        PRESS_CREATE,
        FIND_ITEM,
        PLACE_ITEM,
        ENTER_PRICE,
        CONFIRM_AUCTION
    }

    private Step step = Step.OPEN_AH;
    private int timer = 0;

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
                // String -> Identifier -> Item
                Identifier id = Identifier.tryParse(item.get());
                Item targetItem = Registries.ITEM.get(id);

                FindItemResult result = InvUtils.find(targetItem);

                if (!result.found()) {
                    ChatUtils.error("Item not found: " + item.get());
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
                    String text = String.valueOf(priceSetting.get());

                    for (char c : text.toCharArray()) {
                        screen.charTyped(c, 0);
                    }

                    // Schild bestätigen
                    screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }

                step = Step.CONFIRM_AUCTION;
                timer = 20;
            }

            case CONFIRM_AUCTION -> {
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        6,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                );

                step = Step.OPEN_AH;
                timer = delay.get() * 20;
            }
        }
    }
}
