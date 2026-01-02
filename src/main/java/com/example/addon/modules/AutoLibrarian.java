package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AutoLibrarian extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General Settings
    private final Setting<List<String>> wantedBooks = sgGeneral.add(new StringListSetting.Builder()
        .name("wanted-books")
        .description("List of books to look for. Format: 'enchantment_id;level;max_price'")
        .defaultValue(Arrays.asList(
            "minecraft:mending;1;64",
            "minecraft:unbreaking;3;64",
            "minecraft:protection;4;64",
            "minecraft:sharpness;5;64"
        ))
        .build()
    );

    private final Setting<Boolean> lockInTrade = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-in-trade")
        .description("Automatically buys the book to lock the trade.")
        .defaultValue(true)
        .build()
    );

    private final Setting<UpdateMode> updateMode = sgGeneral.add(new EnumSetting.Builder<UpdateMode>()
        .name("update-mode")
        .description("What to do after finding a wanted book.")
        .defaultValue(UpdateMode.Remove)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Interaction range.")
        .defaultValue(5.0)
        .min(1)
        .sliderMax(6)
        .build()
    );
    
    // Render Settings
    private final Setting<SettingColor> renderColor = sgRender.add(new ColorSetting.Builder()
        .name("render-color")
        .description("Color for rendering the target villager and block.")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .build()
    );

    public enum UpdateMode {
        None,
        Remove,
        ToggleOff
    }

    private VillagerEntity villager;
    private BlockPos jobSite;
    private boolean placingJobSite;
    private boolean breakingJobSite;
    private final Set<Integer> experiencedVillagerIds = new HashSet<>();
    private int timer = 0;

    public AutoLibrarian() {
        super(Enhanced.CATEGORY, "auto-librarian", "Automatically rolls villager trades for desired enchanted books.");
    }

    @Override
    public void onDeactivate() {
        villager = null;
        jobSite = null;
        placingJobSite = false;
        breakingJobSite = false;
        experiencedVillagerIds.clear();
        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        if (villager == null) {
            setTargetVillager();
            return;
        }

        if (villager.isRemoved() || villager.getHealth() <= 0 || mc.player.squaredDistanceTo(villager) > range.get() * range.get()) {
            villager = null;
            return;
        }

        if (jobSite == null) {
            setTargetJobSite();
            return;
        }
        
        // Ensure job site is still a lectern or air (if we are placing)
        if (!placingJobSite && mc.world.getBlockState(jobSite).getBlock() != Blocks.LECTERN && mc.world.getBlockState(jobSite).getBlock() != Blocks.AIR) {
             setTargetJobSite();
             return;
        }

        if (placingJobSite) {
            placeJobSite();
            return;
        }

        if (breakingJobSite) {
            breakJobSite();
            return;
        }

        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler) {
             handleTrade();
        } else {
             openTradeScreen();
        }
    }

    private void handleTrade() {
        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler menu) {
             if (menu.getExperience() > 0 && menu.getLevelProgress() > 1) { 
                 ChatUtils.warning("Villager is already experienced!");
                 experiencedVillagerIds.add(villager.getId());
                 villager = null;
                 jobSite = null;
                 mc.player.closeHandledScreen();
                 return;
             }

             TradeOfferList offers = menu.getRecipes();
             if (offers.isEmpty()) return;

             BookOffer offer = findEnchantedBookOffer(offers);
             if (offer == null) {
                 reroll();
                 return;
             }
             
             ChatUtils.info("Found book: " + offer.name + " " + offer.level + " for " + offer.price + " emeralds.");

             if (isWanted(offer)) {
                 if (lockInTrade.get()) {
                     lockTradeAndFinish(menu, offer);
                 } else {
                     ChatUtils.info("Trade found! Stopping.");
                     updateWantedList(offer);
                     toggle();
                 }
             } else {
                 reroll();
             }
        }
    }
    
    private void reroll() {
        mc.player.closeHandledScreen();
        breakingJobSite = true;
    }

    private void lockTradeAndFinish(MerchantScreenHandler menu, BookOffer offer) {
        int tradeIndex = 0;
        for (int i = 0; i < menu.getRecipes().size(); i++) {
             TradeOffer trade = menu.getRecipes().get(i);
             if (ItemStack.areItemsAndComponentsEqual(trade.getSellItem(), offer.stack)) {
                 tradeIndex = i;
                 break;
             }
        }
        
        if (mc.getNetworkHandler() != null)
             mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(tradeIndex));
        
        InvUtils.click().slotId(2); 
        
        ChatUtils.info("Locked in trade!");
        updateWantedList(offer);
        toggle();
    }
    
    private void updateWantedList(BookOffer offer) {
        if (updateMode.get() == UpdateMode.Remove) {
            List<String> current = new ArrayList<>(wantedBooks.get());
             current.removeIf(s -> {
                 BookOffer wanted = BookOffer.parse(s);
                 return wanted != null && wanted.matches(offer);
             });
             wantedBooks.set(current);
        } else if (updateMode.get() == UpdateMode.ToggleOff) {
            toggle();
        }
    }

    private void openTradeScreen() {
        if (timer > 0) return;
        
        Rotations.rotate(Rotations.getYaw(villager), Rotations.getPitch(villager));
        mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
        timer = 20;
    }

    private void breakJobSite() {
        if (jobSite == null) {
            breakingJobSite = false;
            return;
        }
        
        if (mc.world.getBlockState(jobSite).getBlock() == Blocks.AIR) {
            breakingJobSite = false;
            placingJobSite = true;
            timer = 10;
            return;
        }

        Rotations.rotate(Rotations.getYaw(jobSite), Rotations.getPitch(jobSite));
        BlockUtils.breakBlock(jobSite, true);
    }

    private void placeJobSite() {
        if (jobSite == null) {
            placingJobSite = false;
            return;
        }

        if (mc.world.getBlockState(jobSite).getBlock() == Blocks.LECTERN) {
            placingJobSite = false;
            return;
        }
        
        if (timer > 0) return;

        FindItemResult lectern = InvUtils.find(Items.LECTERN);
        if (!lectern.found()) {
            ChatUtils.error("No Lectern found in hotbar!");
            toggle();
            return;
        }

        BlockUtils.place(jobSite, lectern, true, 50, true, true);
        placingJobSite = false;
        timer = 10;
    }

    private void setTargetVillager() {
        double rangeSq = range.get() * range.get();
        if (mc.world == null) return;
        
        Stream<Entity> stream = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
            .filter(e -> e instanceof VillagerEntity)
            .filter(e -> !e.isRemoved())
            .filter(e -> ((VillagerEntity)e).getHealth() > 0)
            .filter(e -> mc.player.squaredDistanceTo(e) <= rangeSq);

        villager = (VillagerEntity) stream
            .map(e -> (VillagerEntity)e)
            .filter(v -> v.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN)
            .filter(v -> v.getVillagerData().getLevel() == 1)
            .filter(v -> !experiencedVillagerIds.contains(v.getId()))
            .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
            .orElse(null);
            
        if (villager != null) {
            ChatUtils.info("Found villager at " + villager.getBlockPos());
        }
    }

    private void setTargetJobSite() {
        if (villager == null || mc.world == null) return;
        
        List<BlockPos> potSpots = new ArrayList<>();
        int r = range.get().intValue();
        BlockPos center = BlockPos.ofFloored(mc.player.getEyePos());
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    potSpots.add(center.add(x, y, z));
                }
            }
        }
        
        jobSite = potSpots.stream()
            .filter(pos -> mc.world.getBlockState(pos).getBlock() == Blocks.LECTERN)
            .min(Comparator.comparingDouble(pos -> villager.squaredDistanceTo(Vec3d.ofCenter(pos))))
            .orElse(null);
            
        if (jobSite != null) {
            ChatUtils.info("Found lectern at " + jobSite);
        }
    }
    
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (villager != null) {
            event.renderer.box(villager.getBoundingBox(), renderColor.get(), renderColor.get(), ShapeMode.Lines, 0);
        }
        if (jobSite != null) {
             event.renderer.box(jobSite, renderColor.get(), renderColor.get(), ShapeMode.Lines, 0);
        }
    }
    
    private boolean isWanted(BookOffer offer) {
        for (String s : wantedBooks.get()) {
            BookOffer wanted = BookOffer.parse(s);
            if (wanted != null && wanted.matches(offer)) {
                return true;
            }
        }
        return false;
    }

    private BookOffer findEnchantedBookOffer(TradeOfferList offers) {
        for (TradeOffer offer : offers) {
            ItemStack result = offer.getSellItem();
            if (result.getItem() == Items.ENCHANTED_BOOK) {
                var enchantsComponent = EnchantmentHelper.getEnchantments(result);
                Set<RegistryEntry<Enchantment>> enchants = enchantsComponent.getEnchantments();
                if (enchants.isEmpty()) continue;
                
                RegistryEntry<Enchantment> key = enchants.iterator().next();
                int level = enchantsComponent.getLevel(key);
                
                String id = key.getKey().map(k -> k.getValue().toString()).orElse(key.toString());

                if (!id.contains(":") && !id.contains(".")) {
                    id = "minecraft:" + id;
                }
                
                return new BookOffer(id, level, offer.getOriginalFirstBuyItem().getCount(), result);
            }
        }
        return null;
    }

    private static class BookOffer {
        String id;
        int level;
        int price;
        ItemStack stack;
        String name;

        public BookOffer(String id, int level, int price, ItemStack stack) {
            this.id = id;
            this.level = level;
            this.price = price;
            this.stack = stack;
            this.name = id; 
        }

        public static BookOffer parse(String s) {
            try {
                String[] parts = s.split(";");
                if (parts.length >= 2) {
                    String id = parts[0];
                    int level = Integer.parseInt(parts[1]);
                    int maxPrice = parts.length > 2 ? Integer.parseInt(parts[2]) : 64;
                    return new BookOffer(id, level, maxPrice, null);
                }
            } catch (Exception e) {}
            return null;
        }
        
        public boolean matches(BookOffer other) {
            boolean idMatch = this.id.equals(other.id) || other.id.endsWith(":" + this.id) || this.id.endsWith(":" + other.id);
             if (!idMatch) {
                 String cleanThis = this.id.replace("minecraft:", "");
                 String cleanOther = other.id.replace("minecraft:", "");
                 idMatch = cleanThis.equals(cleanOther);
             }
            
            return idMatch && other.level >= this.level && other.price <= this.price;
        }
    }
}