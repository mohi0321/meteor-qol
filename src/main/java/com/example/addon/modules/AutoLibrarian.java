package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;
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

    private Villager villager;
    private BlockPos jobSite;
    private boolean placingJobSite;
    private boolean breakingJobSite;
    private final Set<Integer> experiencedVillagerIds = new HashSet<>();
    private int timer = 0;

    public AutoLibrarian() {
        super(AddonTemplate.CATEGORY, "auto-librarian", "Automatically rolls villager trades for desired enchanted books.");
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
        if (timer > 0) {
            timer--;
            // Simple delay handling
        }

        if (villager == null) {
            setTargetVillager();
            return;
        }

        if (villager.isRemoved() || villager.getHealth() <= 0 || mc.player.distanceToSqr(villager) > range.get() * range.get()) {
            villager = null;
            return;
        }

        if (jobSite == null) {
            setTargetJobSite();
            return;
        }
        
        // Ensure job site is still a lectern or air (if we are placing)
        if (!placingJobSite && BlockUtils.getBlock(jobSite) != Blocks.LECTERN && BlockUtils.getBlock(jobSite) != Blocks.AIR) {
             // If it's not a lectern and not air, we might have lost track or it changed?
             // Re-evaluate
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

        // Interaction logic
        if (!(mc.currentScreen instanceof MerchantMenu)) { // Note: MerchantScreen in yarn, but we check generic screen type or specific
            // Check if we are in a MerchantScreen (container)
             if (mc.player.containerMenu instanceof MerchantMenu) {
                 // Already open?
                 handleTrade();
             } else {
                 // Open trade
                 openTradeScreen();
             }
        } else {
            handleTrade();
        }
    }

    // Since mc.currentScreen is the GUI, and mc.player.currentScreenHandler is the container logic.
    // In Meteor/Fabric: mc.currentScreen is Screen. 
    // We should check if mc.currentScreen is instance of MerchantScreen.
    // However, we can also check mc.player.currentScreenHandler which is the menu.

    private void handleTrade() {
        // Wait a bit if we just opened it?
        if (mc.player.containerMenu instanceof MerchantMenu menu) {
             // Check XP
             if (menu.getTraderXp() > 0 && menu.getTraderLevel() > 1) { 
                 // Level > 1 usually means experienced, but getTraderXp > 0 is stricter for Novice with XP.
                 // Actually, a novice has 0 XP. If > 0, they traded.
                 ChatUtils.warning("Villager is already experienced!");
                 experiencedVillagerIds.add(villager.getId());
                 villager = null;
                 jobSite = null;
                 mc.player.closeContainer();
                 return;
             }

             MerchantOffers offers = menu.getOffers();
             if (offers.isEmpty()) return; // Wait for offers

             BookOffer offer = findEnchantedBookOffer(offers);
             if (offer == null) {
                 // No book, reroll
                 reroll();
                 return;
             }
             
             ChatUtils.info("Found book: " + offer.name + " " + offer.level + " for " + offer.price + " emeralds.");

             if (isWanted(offer)) {
                 // Lock in?
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
        mc.player.closeContainer();
        breakingJobSite = true;
    }

    private void lockTradeAndFinish(MerchantMenu menu, BookOffer offer) {
        // Find the index of the trade (assumed 0 or find it)
        // Usually the book trade is one of the first two.
        int tradeIndex = -1;
        for (int i = 0; i < menu.getOffers().size(); i++) {
             MerchantOffer trade = menu.getOffers().get(i);
             if (ItemStack.matches(trade.getResult(), offer.stack)) {
                 tradeIndex = i;
                 break;
             }
        }
        
        if (tradeIndex == -1) {
            // Should not happen if offer came from offers
            tradeIndex = 0; 
        }

        // Select trade
        if (mc.getNetworkHandler() != null)
             mc.getNetworkHandler().sendPacket(new ServerboundSelectTradePacket(tradeIndex));
        
        // Move items? or just click output?
        // Simple way: Click output slot (2)
        // Ensure we have ingredients? logic assumes user has them as per original Wurst code
        
        // Slot 2 is result
        InvUtils.click().slotId(2); 
        
        ChatUtils.info("Locked in trade!");
        updateWantedList(offer);
        toggle();
    }
    
    private void updateWantedList(BookOffer offer) {
        if (updateMode.get() == UpdateMode.Remove) {
            List<String> current = new ArrayList<>(wantedBooks.get());
             // Remove the one that matched
             // We need to parse and match again or pass the string
             // Simplest: remove exact match or loosely match
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
        mc.interactionManager.interactEntity(mc.player, villager, InteractionHand.MAIN_HAND);
        timer = 10;
    }

    private void breakJobSite() {
        if (jobSite == null) {
            breakingJobSite = false;
            return;
        }
        
        if (BlockUtils.getBlock(jobSite) == Blocks.AIR) {
            breakingJobSite = false;
            placingJobSite = true;
            timer = 5; // Delay before place
            return;
        }

        // Need an axe?
        FindItemResult axe = InvUtils.find(item -> item.getItem() instanceof net.minecraft.world.item.AxeItem);
        if (!axe.found()) {
            // fallback
        } else {
            // InvUtils.swap(axe.slot(), true); // Swap to axe (silent? Wurst does equipBestTool)
            // We can just use interactionManager default breaking which swaps automatically if configured or we swap explicitly
        }
        
        Rotations.rotate(Rotations.getYaw(jobSite), Rotations.getPitch(jobSite)); // Look at block
        BlockUtils.breakBlock(jobSite, true); // true = swing
    }

    private void placeJobSite() {
        if (jobSite == null) {
            placingJobSite = false;
            return;
        }

        if (BlockUtils.getBlock(jobSite) == Blocks.LECTERN) {
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
        timer = 5; // Delay after place
    }

    private void setTargetVillager() {
        double rangeSq = range.get() * range.get();
        
        Stream<Entity> stream = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
            .filter(e -> e instanceof Villager)
            .filter(e -> !e.isRemoved())
            .filter(e -> ((Villager)e).getHealth() > 0)
            .filter(e -> mc.player.distanceToSqr(e) <= rangeSq);

        villager = (Villager) stream
            .map(e -> (Villager)e)
            .filter(v -> v.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN)
            .filter(v -> v.getVillagerData().getLevel() == 1) // Novice
            .filter(v -> !experiencedVillagerIds.contains(v.getId()))
            .min(Comparator.comparingDouble(e -> mc.player.distanceToSqr(e)))
            .orElse(null);
            
        if (villager != null) {
            ChatUtils.info("Found villager at " + villager.blockPosition());
        }
    }

    private void setTargetJobSite() {
        if (villager == null) return;
        
        // Identify the lectern the villager is bound to, or the nearest lectern
        // Logic: Scan nearby lecterns.
        // Wurst logic: all lecterns in range, min dist to villager.
        
        List<BlockPos> potSpots = BlockUtils.getAllInBox(
                BlockPos.containing(mc.player.getEyePosition()).offset((int)-range.get(), (int)-range.get(), (int)-range.get()),
                BlockPos.containing(mc.player.getEyePosition()).offset((int)range.get(), (int)range.get(), (int)range.get())
        );
        
        jobSite = potSpots.stream()
            .filter(pos -> BlockUtils.getBlock(pos) == Blocks.LECTERN)
            .min(Comparator.comparingDouble(pos -> villager.distanceToSqr(Vec3.atCenterOf(pos))))
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
    
    // --- Helpers ---
    private boolean isWanted(BookOffer offer) {
        for (String s : wantedBooks.get()) {
            BookOffer wanted = BookOffer.parse(s);
            if (wanted != null && wanted.matches(offer)) {
                return true;
            }
        }
        return false;
    }

    private BookOffer findEnchantedBookOffer(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            if (result.getItem() == Items.ENCHANTED_BOOK) {
                // Get Enchantments
                Map<Holder<Enchantment>, Integer> enchants = EnchantmentHelper.getEnchantmentsForCrafting(result);
                // Usually only one
                if (!enchants.isEmpty()) {
                    Map.Entry<Holder<Enchantment>, Integer> entry = enchants.entrySet().iterator().next();
                    // Component logic in newer versions might differ slightly, but this is standard 1.20+
                    // We need the string ID? or just compare Holder?
                    // Wrapper for comparison.
                    String id = entry.getKey().unwrapKey().map(k -> k.location().toString()).orElse("");
                    return new BookOffer(id, entry.getValue(), offer.getCostA().getCount(), result);
                }
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
             // Try to get name
             this.name = id; // Fallback
             // If we can get a readable name?
             // Component n = stack.getHoverName();
        }

        public static BookOffer parse(String s) {
            try {
                String[] parts = s.split(";");
                if (parts.length >= 2) {
                    String id = parts[0];
                    int level = Integer.parseInt(parts[1]);
                    int maxPrice = parts.length > 2 ? Integer.parseInt(parts[2]) : 64;
                    BookOffer b = new BookOffer(id, level, maxPrice, null);
                    return b;
                }
            } catch (Exception e) {}
            return null;
        }
        
        public boolean matches(BookOffer other) {
            return this.id.equals(other.id) && other.level >= this.level && other.price <= this.price;
        }
    }
}