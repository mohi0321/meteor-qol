package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;

import java.util.*;
import java.util.stream.StreamSupport;

public class EnchantedBookBuyer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> enchantmentId = sgGeneral.add(new StringSetting.Builder()
        .name("enchantment-id")
        .description("The ID of the enchantment to look for (e.g., 'minecraft:mending').")
        .defaultValue("minecraft:mending")
        .build()
    );

    private final Setting<Integer> level = sgGeneral.add(new IntSetting.Builder()
        .name("level")
        .description("The minimum level of the enchantment.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<Integer> maxPrice = sgGeneral.add(new IntSetting.Builder()
        .name("max-price")
        .description("The maximum price in emeralds.")
        .defaultValue(64)
        .min(1)
        .max(64)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("The delay between actions in seconds.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(5)
        .build()
    );



    private enum State {
        SEARCHING,
        TRAVELLING,
        TRADING,
        DROPPING,
        FINISH
    }

    private State currentState = State.SEARCHING;
    private final Set<Integer> visitedVillagers = new HashSet<>();
    private VillagerEntity targetVillager;
    private int timer = 0;
    private final Random random = new Random();

    public EnchantedBookBuyer() {
        super(Enhanced.CATEGORY, "enchanted-book-buyer", "Buys specific enchanted books from all nearby librarians.");
    }

    @Override
    public void onActivate() {
        currentState = State.SEARCHING;
        visitedVillagers.clear();
        targetVillager = null;
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
            case SEARCHING -> handleSearching();
            case TRAVELLING -> handleTravelling();
            case TRADING -> handleTrading();
            case DROPPING -> handleDropping();
            case FINISH -> {
                toggle();
            }
        }
    }

    private void handleSearching() {
        targetVillager = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
            .filter(e -> e instanceof VillagerEntity)
            .map(e -> (VillagerEntity) e)
            .filter(e -> !e.isRemoved() && e.getHealth() > 0)
            .filter(e -> !visitedVillagers.contains(e.getId()))
            .filter(e -> e.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN)
            .filter(e -> mc.player.canSee(e))
            .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
            .orElse(null);

        if (targetVillager != null) {
            ChatUtils.info("Found librarian at " + targetVillager.getBlockPos() + ". Travelling...");
            currentState = State.TRAVELLING;
        } else {
            ChatUtils.info("No more librarians found. Dropping items.");
            currentState = State.DROPPING;
        }
    }

    private void handleTravelling() {
        if (targetVillager == null || targetVillager.isRemoved()) {
            currentState = State.SEARCHING;
            return;
        }

        double distSq = mc.player.squaredDistanceTo(targetVillager);
        if (distSq <= 16) { // 4 blocks distance (4*4=16)
            currentState = State.TRADING;
            // potential simple stop movement logic if needed, but manual control override or just stopping input
            mc.options.forwardKey.setPressed(false);
        } else {
            // Simple walk towards
            Rotations.rotate(Rotations.getYaw(targetVillager), Rotations.getPitch(targetVillager));
            mc.options.forwardKey.setPressed(true); 
            // Note: This is very basic. ideally use baritone or more complex pathing.
            // But strict requirement was "reachable using walking". This assumes straight line line-of-sight walking.
        }
    }

    private void handleTrading() {
        // Reset movement
        mc.options.forwardKey.setPressed(false);

        if (targetVillager == null || targetVillager.isRemoved()) {
            currentState = State.SEARCHING;
            return;
        }

        if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler)) {
            // Open trade
            if (timer <= 0) {
                Rotations.rotate(Rotations.getYaw(targetVillager), Rotations.getPitch(targetVillager));
                mc.interactionManager.interactEntity(mc.player, targetVillager, Hand.MAIN_HAND);
                timer = (int) (delay.get() * 20); // wait for screen
            }
            return;
        }

        MerchantScreenHandler menu = (MerchantScreenHandler) mc.player.currentScreenHandler;
        TradeOfferList offers = menu.getRecipes();
        
        // Find trade
        int tradeIndex = -1;
        TradeOffer validOffer = null;

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            if (isValidTrade(offer)) {
                tradeIndex = i;
                validOffer = offer;
                break;
            }
        }

        if (tradeIndex != -1 && validOffer != null) {
            // Buy
             if (!validOffer.isDisabled() && mc.player.getInventory().getEmptySlot() != -1) { // Check space/stock
                 // Select trade
                 mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(tradeIndex));
                 // Click output (slot 2)
                 // We want to buy ALL. InvUtils quickMove or standard click loop?
                 // Quick move (shift click) is best to buy max stack.
                 InvUtils.shiftClick().slotId(2);
                 timer = (int) (delay.get() * 20); // small delay between buys
             } else {
                 // Done with this villager (either out of stock or full inv or no money)
                 finishTrading();
             }
        } else {
            // No valid trade found
            finishTrading();
        }
    }
    
    private void finishTrading() {
        mc.player.closeHandledScreen();
        visitedVillagers.add(targetVillager.getId());
        targetVillager = null;
        currentState = State.SEARCHING;
        timer = (int) (delay.get() * 20);
    }

    private void handleDropping() {
        // Tween rotation
        float pitch = mc.player.getPitch();
        if (pitch < 90) {
            float newPitch = Math.min(90, pitch + 5);
            float yaw = mc.player.getYaw() + (random.nextFloat() - 0.5f) * 10f;
            Rotations.rotate(yaw, newPitch);
            return;
        }
        
        // Iterate inventory and drop valid books
        // We need to check exact NBT match or just any enchanted book?
        // "buys out the stock of THAT item ... and drops it"
        // So we should drop only the specific book we wanted.
        
        // We iterate slots and drop
        boolean droppedAny = false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && isTargetBook(stack)) {
                 InvUtils.drop().slotId(i);
                 droppedAny = true;
            }
        }
        
        if (!droppedAny) {
             currentState = State.FINISH;
        }
        timer = (int) (delay.get() * 20); // delay for drops
    }

    private boolean isValidTrade(TradeOffer offer) {
        ItemStack sell = offer.getSellItem();
        return isTargetBook(sell) && offer.getOriginalFirstBuyItem().getCount() <= maxPrice.get();
    }

    private boolean isTargetBook(ItemStack stack) {
        if (stack.getItem() != Items.ENCHANTED_BOOK) return false;

         var enchantsComponent = EnchantmentHelper.getEnchantments(stack);
         Set<RegistryEntry<Enchantment>> enchants = enchantsComponent.getEnchantments();
         if (enchants.isEmpty()) return false;

         // Check if contains our target
         for (RegistryEntry<Enchantment> entry : enchants) {
             String id = entry.getKey().map(k -> k.getValue().toString()).orElse(entry.toString());
              if (!id.contains(":") && !id.contains(".")) {
                    id = "minecraft:" + id;
              }
              int lvl = enchantsComponent.getLevel(entry);
              
              // flexible matching
              String targetId = enchantmentId.get();
               if (!targetId.contains(":") && !targetId.contains(".")) {
                    targetId = "minecraft:" + targetId;
              }
              
              if (id.equalsIgnoreCase(targetId) && lvl >= level.get()) {
                  return true;
              }
         }
         return false;
    }
    
    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
    }
}
