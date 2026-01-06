package com.example.addon.modules.Automation;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
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
        .defaultValue("minecraft:mending")
        .build()
    );

    private final Setting<Integer> level = sgGeneral.add(new IntSetting.Builder()
        .name("level")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<Integer> maxPrice = sgGeneral.add(new IntSetting.Builder()
        .name("max-price")
        .defaultValue(64)
        .min(1)
        .max(64)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private enum State {
        SEARCHING, TRAVELLING, TRADING, DROPPING, FINISH
    }

    private State currentState = State.SEARCHING;
    private final Set<Integer> visitedVillagers = new HashSet<>();
    private VillagerEntity targetVillager;
    private int timer;
    private final Random random = new Random();

    public EnchantedBookBuyer() {
        super(Enhanced.Automation, "enchanted-book-buyer", "Buys specific enchanted books from librarians.");
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
        if (mc == null || mc.player == null || mc.world == null) return;

        if (timer-- > 0) return;

        switch (currentState) {
            case SEARCHING -> handleSearching();
            case TRAVELLING -> handleTravelling();
            case TRADING -> handleTrading();
            case DROPPING -> handleDropping();
            case FINISH -> toggle();
        }
    }

    /* ---------------- SEARCHING ---------------- */

    private void handleSearching() {
        if (mc.player == null || mc.world == null) return;

        targetVillager = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
            .filter(e -> e instanceof VillagerEntity)
            .map(e -> (VillagerEntity) e)
            .filter(e -> !e.isRemoved() && e.getHealth() > 0)
            .filter(e -> e.getVillagerData() != null)
            .filter(e -> e.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN)
            .filter(e -> !visitedVillagers.contains(e.getId()))
            .filter(e -> mc.player.canSee(e))
            .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
            .orElse(null);

        currentState = (targetVillager != null) ? State.TRAVELLING : State.DROPPING;
    }

    /* ---------------- TRAVELLING ---------------- */

    private void handleTravelling() {
        if (mc.player == null || targetVillager == null || targetVillager.isRemoved()) {
            currentState = State.SEARCHING;
            return;
        }

        if (mc.player.squaredDistanceTo(targetVillager) <= 16) {
            mc.options.forwardKey.setPressed(false);
            currentState = State.TRADING;
            return;
        }

        Rotations.rotate(Rotations.getYaw(targetVillager), Rotations.getPitch(targetVillager));
        mc.options.forwardKey.setPressed(true);
    }

    /* ---------------- TRADING ---------------- */

    private void handleTrading() {
        if (mc.player == null || targetVillager == null) return;

        mc.options.forwardKey.setPressed(false);

        if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            Rotations.rotate(Rotations.getYaw(targetVillager), Rotations.getPitch(targetVillager));
            mc.interactionManager.interactEntity(mc.player, targetVillager, Hand.MAIN_HAND);
            timer = (int) (delay.get() * 20);
            return;
        }

        TradeOfferList offers = handler.getRecipes();
        if (offers == null || offers.isEmpty()) {
            finishTrading();
            return;
        }

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            if (offer != null && isValidTrade(offer) && !offer.isDisabled()
                && mc.player.getInventory().getEmptySlot() != -1) {

                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(i));
                }

                InvUtils.shiftClick().slotId(2);
                timer = (int) (delay.get() * 20);
                return;
            }
        }

        finishTrading();
    }

    private void finishTrading() {
        if (mc.player != null) mc.player.closeHandledScreen();
        if (targetVillager != null) visitedVillagers.add(targetVillager.getId());
        targetVillager = null;
        currentState = State.SEARCHING;
        timer = (int) (delay.get() * 20);
    }

    /* ---------------- DROPPING ---------------- */

    private void handleDropping() {
        if (mc.player == null) return;

        if (mc.player.getPitch() < 90) {
            Rotations.rotate(
                mc.player.getYaw() + (random.nextFloat() - 0.5f) * 10f,
                Math.min(90, mc.player.getPitch() + 5)
            );
            return;
        }

        boolean dropped = false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && isTargetBook(stack)) {
                InvUtils.drop().slotId(i);
                dropped = true;
            }
        }

        if (!dropped) currentState = State.FINISH;
        timer = (int) (delay.get() * 20);
    }

    /* ---------------- VALIDATION ---------------- */

    private boolean isValidTrade(TradeOffer offer) {
        if (offer == null) return false;
        ItemStack sell = offer.getSellItem();
        if (sell == null) return false;

        return isTargetBook(sell)
            && offer.getOriginalFirstBuyItem() != null
            && offer.getOriginalFirstBuyItem().getCount() <= maxPrice.get();
    }

    private boolean isTargetBook(ItemStack stack) {
        if (stack == null || stack.getItem() != Items.ENCHANTED_BOOK) return false;

        var enchants = EnchantmentHelper.getEnchantments(stack);
        if (enchants == null || enchants.isEmpty()) return false;

        String targetId = normalizeId(enchantmentId.get());

        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            String id = entry.getKey().map(k -> k.getValue().toString()).orElse("");
            if (normalizeId(id).equalsIgnoreCase(targetId)
                && enchants.getLevel(entry) >= level.get()) {
                return true;
            }
        }
        return false;
    }

    private String normalizeId(String id) {
        if (id == null) return "";
        return id.contains(":") ? id : "minecraft:" + id;
    }

    @Override
    public void onDeactivate() {
        if (mc != null && mc.options != null)
            mc.options.forwardKey.setPressed(false);
    }
}
