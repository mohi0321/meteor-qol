package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import org.lwjgl.glfw.GLFW;

public class AutoAuction extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> itemSetting = sgGeneral.add(new StringSetting.Builder()
        .name("item")
        .defaultValue("minecraft:enchanted_book")
        .description("Item ID to auction (e.g., minecraft:enchanted_book)")
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

    private final Setting<Boolean> autoDisableSetting = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Automatically disable when item runs out")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxAuctionsSetting = sgGeneral.add(new IntSetting.Builder()
        .name("max-auctions")
        .description("Maximum number of auctions to create (0 = unlimited)")
        .defaultValue(0)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> createSlotSetting = sgGeneral.add(new IntSetting.Builder()
        .name("create-button-slot")
        .description("Slot number of the 'Create Auction' button")
        .defaultValue(53)
        .min(0)
        .sliderMax(53)
        .build()
    );

    private final Setting<Integer> auctionSlotSetting = sgGeneral.add(new IntSetting.Builder()
        .name("auction-item-slot")
        .description("Slot number where the item should be placed")
        .defaultValue(6)
        .min(0)
        .sliderMax(53)
        .build()
    );

    private enum Step {
        OPEN_AH,
        WAIT_MAIN_GUI,
        PRESS_CREATE,
        WAIT_CREATE_GUI,
        PICK_ITEM,
        PLACE_ITEM,
        WAIT_SIGN,
        TYPE_PRICE,
        CONFIRM_SIGN,
        WAIT_REPEAT
    }

    private Step step;
    private int timer;
    private Item targetItem;
    private String targetPrice;
    private int repeatDelayTicks;
    private int auctionsCreated;
    private int retryCount;
    private int typingIndex;
    private static final int MAX_RETRIES = 5;

    public AutoAuction() {
        super(Enhanced.CATEGORY, "auto-auction", "Automatically sells items in the auction house.");
    }

    @Override
    public void onActivate() {
        String id = itemSetting.get().toLowerCase();
        if (!id.contains(":")) id = "minecraft:" + id;

        targetItem = Registries.ITEM.get(Identifier.tryParse(id));
        targetPrice = priceSetting.get();
        repeatDelayTicks = repeatDelaySetting.get() * 20;
        auctionsCreated = 0;
        retryCount = 0;
        typingIndex = 0;

        if (targetItem == Items.AIR) {
            ChatUtils.error("Invalid item ID: " + itemSetting.get());
            toggle();
            return;
        }

        ChatUtils.info("Starting AutoAuction for " + targetItem.getName().getString());
        step = Step.OPEN_AH;
        timer = 20;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.player.currentScreenHandler != null) {
            mc.player.closeHandledScreen();
        }
        ChatUtils.info("AutoAuction stopped. Created " + auctionsCreated + " auctions.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer-- > 0) return;

        final int ONE_SECOND = 20;
        final int HALF_SECOND = 10;

        switch (step) {

            case OPEN_AH -> {
                ChatUtils.sendPlayerMsg("/ah");
                step = Step.WAIT_MAIN_GUI;
                timer = ONE_SECOND;
                retryCount = 0;
            }

            case WAIT_MAIN_GUI -> {
                if (mc.player.currentScreenHandler != null && mc.player.currentScreenHandler.slots.size() > createSlotSetting.get()) {
                    step = Step.PRESS_CREATE;
                    timer = HALF_SECOND;
                } else {
                    if (++retryCount > MAX_RETRIES) {
                        ChatUtils.error("Failed to open auction house GUI.");
                        toggle();
                        return;
                    }
                    timer = HALF_SECOND;
                }
            }

            case PRESS_CREATE -> {
                if (mc.player.currentScreenHandler == null) {
                    step = Step.OPEN_AH;
                    timer = ONE_SECOND;
                    return;
                }

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    createSlotSetting.get(),
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                step = Step.WAIT_CREATE_GUI;
                timer = ONE_SECOND * 2;
                retryCount = 0;
            }

            case WAIT_CREATE_GUI -> {
                if (mc.player.currentScreenHandler == null) {
                    if (++retryCount > MAX_RETRIES) {
                        ChatUtils.error("Creation GUI failed to open.");
                        step = Step.OPEN_AH;
                        timer = ONE_SECOND;
                    } else {
                        timer = HALF_SECOND;
                    }
                    return;
                }
                
                if (mc.player.currentScreenHandler.slots.size() > 50) {
                    timer = HALF_SECOND;
                    return;
                }

                step = Step.PICK_ITEM;
                timer = HALF_SECOND;
            }

            case PICK_ITEM -> {
                if (mc.player.currentScreenHandler == null) {
                    ChatUtils.error("GUI closed unexpectedly.");
                    step = Step.OPEN_AH;
                    timer = ONE_SECOND;
                    return;
                }

                FindItemResult result = InvUtils.find(targetItem);
                
                if (!result.found()) {
                    ChatUtils.error("Could not find " + targetItem.getName().getString() + " in inventory.");
                    if (autoDisableSetting.get()) {
                        toggle();
                    } else {
                        step = Step.WAIT_REPEAT;
                        timer = repeatDelayTicks;
                    }
                    return;
                }
                
                int foundSlot = result.slot();

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    foundSlot,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                
                timer = HALF_SECOND;
                step = Step.PLACE_ITEM;
            }

            case PLACE_ITEM -> {
                if (mc.player.currentScreenHandler == null) {
                    ChatUtils.error("GUI closed unexpectedly.");
                    step = Step.OPEN_AH;
                    timer = ONE_SECOND;
                    return;
                }

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    auctionSlotSetting.get(),
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );

                timer = HALF_SECOND;
                step = Step.WAIT_SIGN;
            }

            case WAIT_SIGN -> {
                // Wait for sign screen to open
                if (mc.currentScreen instanceof SignEditScreen) {
                    typingIndex = 0;
                    step = Step.TYPE_PRICE;
                    timer = 5; // Small delay before typing
                } else {
                    if (++retryCount > MAX_RETRIES) {
                        ChatUtils.error("Sign screen failed to open.");
                        step = Step.OPEN_AH;
                        timer = ONE_SECOND;
                    } else {
                        timer = HALF_SECOND;
                    }
                }
            }

            case TYPE_PRICE -> {
                if (!(mc.currentScreen instanceof SignEditScreen)) {
                    ChatUtils.error("Sign screen closed unexpectedly.");
                    step = Step.OPEN_AH;
                    timer = ONE_SECOND;
                    return;
                }

                // Type one character at a time
                if (typingIndex < targetPrice.length()) {
                    char c = targetPrice.charAt(typingIndex);
                    
                    // Send the character as a typed key
                    if (mc.currentScreen != null) {
                        mc.currentScreen.charTyped(c, 0);
                    }
                    
                    typingIndex++;
                    timer = 2; // Small delay between characters
                } else {
                    // Done typing, press enter
                    step = Step.CONFIRM_SIGN;
                    timer = HALF_SECOND;
                    retryCount = 0;
                }
            }

            case CONFIRM_SIGN -> {
                // Press Enter to confirm the sign
                if (mc.currentScreen instanceof SignEditScreen) {
                    mc.currentScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                }
                
                auctionsCreated++;
                ChatUtils.info("Auction #" + auctionsCreated + " created successfully.");
                
                int maxAuctions = maxAuctionsSetting.get();
                if (maxAuctions > 0 && auctionsCreated >= maxAuctions) {
                    ChatUtils.info("Reached maximum auction limit (" + maxAuctions + ").");
                    toggle();
                    return;
                }
                
                step = Step.WAIT_REPEAT;
                timer = repeatDelayTicks;
            }

            case WAIT_REPEAT -> {
                step = Step.OPEN_AH;
                timer = ONE_SECOND;
            }
        }
    }
}
