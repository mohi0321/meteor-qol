package com.example.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import com.example.addon.AddonTemplate;

/**
 * AutoLibrarian (partial port)
 *
 * This file contains the Meteor Client module for AutoLibrarian. The original
 * WurstClient implementation was intentionally not copied here; instead the
 * feature will be ported incrementally and mapped to Meteor Client APIs.
 */
public class AutoLibrarian extends Module {
	private final SettingGroup sg = settings.getDefaultGroup();

	private final BoolSetting lockInTrade = sg.add(new BoolSetting.Builder()
		.name("lock-in-trade")
		.description("Automatically buy an item to lock in a villager's trade when found.")
		.defaultValue(false)
		.build());

	private final IntSetting range = sg.add(new IntSetting.Builder()
		.name("range")
		.description("Search range for villagers (blocks).")
		.defaultValue(5)
		.min(1)
		.max(64)
		.build());

	private final IntSetting repairThreshold = sg.add(new IntSetting.Builder()
		.name("repair-threshold")
		.description("Don't use tools below this durability when performing actions.")
		.defaultValue(1)
		.min(0)
		.max(100)
		.build());

	public AutoLibrarian() {
		super(AddonTemplate.CATEGORY, "auto-librarian", "(Skeleton) Train villagers to become librarians.");
	}

	@Override
	public void onActivate() {
		info("AutoLibrarian enabled (skeleton port)");
	}

	@Override
	public void onDeactivate() {
		info("AutoLibrarian disabled");
	}

	@EventHandler
	private void onTick(TickEvent.Pre event) {
		// Placeholder: implement full port logic here.
		if (mc.player == null || mc.world == null) return;
	}
}
package com.example.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import com.example.addon.AddonTemplate;

/**
 * AutoLibrarian (partial port)
 *
 * This is an initial port/skeleton of the WurstClient AutoLibrarian hack
 * adapted to Meteor Client's module structure. It provides settings and a
 * basic tick handler as a starting point for completing the full feature.
 */
public class AutoLibrarian extends Module {
	private final SettingGroup sg = settings.getDefaultGroup();

	private final BoolSetting lockInTrade = sg.add(new BoolSetting.Builder()
		.name("lock-in-trade")
		.description("Automatically buy an item to lock in a villager's trade when found.")
		.defaultValue(false)
		.build());

	private final IntSetting range = sg.add(new IntSetting.Builder()
		.name("range")
		.description("Search range for villagers (blocks).")
		.defaultValue(5)
		.min(1)
		.max(64)
		.build());

	private final IntSetting repairThreshold = sg.add(new IntSetting.Builder()
		.name("repair-threshold")
		.description("Don't use tools below this durability when performing actions.")
		.defaultValue(1)
		.min(0)
		.max(100)
		.build());

	public AutoLibrarian() {
		super(AddonTemplate.CATEGORY, "auto-librarian", "(Skeleton) Train villagers to become librarians.");
	}

	@Override
	public void onActivate() {
		info("AutoLibrarian enabled (skeleton port)");
	}

	@Override
	public void onDeactivate() {
		info("AutoLibrarian disabled");
	}

	@EventHandler
	private void onTick(TickEvent.Pre event) {
		// This is a lightweight placeholder. A full port would:
		// - Find nearby villagers of profession LIBRARIAN/level 1
		// - Place/break lecterns to retrain villagers
		// - Open trade screen and inspect offers
		// - Optionally buy to lock in trade
		// Implementing the above requires a detailed mapping of Wurst's code
		// to Meteor Client's API and the target Minecraft mappings.

		// For now, we keep a simple heartbeat so the module compiles and can be
		// iteratively extended.
		if (mc.player == null || mc.world == null) return;

		// Example: log every few seconds (quiet by default)
		// Real villager handling to be implemented in follow-up changes.
	}
}
/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.mojang.blaze3d.vertex.PoseStack;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autolibrarian.BookOffer;
import net.wurstclient.hacks.autolibrarian.UpdateBooksSetting;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FaceTargetSetting;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

@SearchTags({"auto librarian", "AutoVillager", "auto villager",
	"VillagerTrainer", "villager trainer", "LibrarianTrainer",
	"librarian trainer", "AutoHmmm", "auto hmmm"})
public final class AutoLibrarianHack extends Hack
	implements UpdateListener, RenderListener
{
	private final BookOffersSetting wantedBooks = new BookOffersSetting(
		"Wanted books",
		"A list of enchanted books that you want your villagers to sell.\n\n"
			+ "AutoLibrarian will stop training the current villager"
			+ " once it has learned to sell one of these books.\n\n"
			+ "You can also set a maximum price for each book, in case you"
			+ " already have a villager selling it but you want it for a"
			+ " cheaper price.",
		"minecraft:depth_strider;3", "minecraft:efficiency;5",
		"minecraft:feather_falling;4", "minecraft:fortune;3",
		"minecraft:looting;3", "minecraft:mending;1", "minecraft:protection;4",
		"minecraft:respiration;3", "minecraft:sharpness;5",
		"minecraft:silk_touch;1", "minecraft:unbreaking;3");
	
	private final CheckboxSetting lockInTrade = new CheckboxSetting(
		"Lock in trade",
		"Automatically buys something from the villager once it has learned to"
			+ " sell the book you want. This prevents the villager from"
			+ " changing its trade offers later.\n\n"
			+ "Make sure you have at least 24 paper and 9 emeralds in your"
			+ " inventory when using this feature. Alternatively, 1 book and"
			+ " 64 emeralds will also work.",
		false);
	
	private final UpdateBooksSetting updateBooks = new UpdateBooksSetting();
	
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final SliderSetting repairMode = new SliderSetting("Repair mode",
		"Prevents AutoLibrarian from using your axe when its durability reaches"
			+ " the given threshold, so you can repair it before it breaks.\n"
			+ "Can be adjusted from 0 (off) to 100 remaining uses.",
		1, 0, 100, 1, ValueDisplay.INTEGER.withLabel(0, "off"));
	
	private final OverlayRenderer overlay = new OverlayRenderer();
	private final HashSet<Villager> experiencedVillagers = new HashSet<>();
	
	private Villager villager;
	private BlockPos jobSite;
	
	private boolean placingJobSite;
	private boolean breakingJobSite;
	
	public AutoLibrarianHack()
	{
		super("AutoLibrarian");
		setCategory(Category.OTHER);
		addSetting(wantedBooks);
		addSetting(lockInTrade);
		addSetting(updateBooks);
		addSetting(range);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(repairMode);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(breakingJobSite)
		{
			MC.gameMode.isDestroying = true;
			MC.gameMode.stopDestroyBlock();
			breakingJobSite = false;
		}
		
		overlay.resetProgress();
		villager = null;
		jobSite = null;
		placingJobSite = false;
		breakingJobSite = false;
		experiencedVillagers.clear();
	}
	
	@Override
	public void onUpdate()
	{
		if(villager == null)
		{
			setTargetVillager();
			return;
		}
		
		if(jobSite == null)
		{
			setTargetJobSite();
			return;
		}
		
		if(placingJobSite && breakingJobSite)
			throw new IllegalStateException(
				"Trying to place and break job site at the same time. Something is wrong.");
		
		if(placingJobSite)
		{
			placeJobSite();
			return;
		}
		
		if(breakingJobSite)
		{
			breakJobSite();
			return;
		}
		
		if(!(MC.screen instanceof MerchantScreen tradeScreen))
		{
			openTradeScreen();
			return;
		}
		
		// Can't see experience until the trade screen is open, so we have to
		// check it here and start over if the villager is already experienced.
		int experience = tradeScreen.getMenu().getTraderXp();
		if(experience > 0)
		{
			ChatUtils.warning("Villager at "
				+ villager.blockPosition().toShortString()
				+ " is already experienced, meaning it can't be trained anymore.");
			ChatUtils.message("Looking for another villager...");
			experiencedVillagers.add(villager);
			villager = null;
			jobSite = null;
			closeTradeScreen();
			return;
		}
		
		// check which book the villager is selling
		BookOffer bookOffer =
			findEnchantedBookOffer(tradeScreen.getMenu().getOffers());
		
		if(bookOffer == null)
		{
			ChatUtils.message("Villager is not selling an enchanted book.");
			closeTradeScreen();
			breakingJobSite = true;
			System.out.println("Breaking job site...");
			return;
		}
		
		ChatUtils.message(
			"Villager is selling " + bookOffer.getEnchantmentNameWithLevel()
				+ " for " + bookOffer.getFormattedPrice() + ".");
		
		// if wrong enchantment, break job site and start over
		if(!wantedBooks.isWanted(bookOffer))
		{
			breakingJobSite = true;
			System.out.println("Breaking job site...");
			closeTradeScreen();
			return;
		}
		
		// lock in the trade, if enabled
		if(lockInTrade.isChecked())
		{
			// select the first valid trade
			tradeScreen.getMenu().setSelectionHint(0);
			tradeScreen.getMenu().tryMoveItems(0);
			MC.getConnection().send(new ServerboundSelectTradePacket(0));
			
			// buy whatever the villager is selling
			MC.gameMode.handleInventoryMouseClick(
				tradeScreen.getMenu().containerId, 2, 0, ClickType.PICKUP,
				MC.player);
			
			// close the trade screen
			closeTradeScreen();
		}
		
		// update wanted books based on the user's settings
		updateBooks.getSelected().update(wantedBooks, bookOffer);
		
		ChatUtils.message("Done!");
		setEnabled(false);
	}
	
	private void breakJobSite()
	{
		if(jobSite == null)
			throw new IllegalStateException("Job site is null.");
		
		BlockBreakingParams params =
			BlockBreaker.getBlockBreakingParams(jobSite);
		
		if(params == null || BlockUtils.getState(jobSite).canBeReplaced())
		{
			System.out.println("Job site has been broken. Replacing...");
			breakingJobSite = false;
			placingJobSite = true;
			return;
		}
		
		// equip tool
		WURST.getHax().autoToolHack.equipBestTool(jobSite, false, true,
			repairMode.getValueI());
		
		// face block
		faceTarget.face(params.hitVec());
		
		// damage block and swing hand
		if(MC.gameMode.continueDestroyBlock(jobSite, params.side()))
			swingHand.swing(InteractionHand.MAIN_HAND);
		
		// update progress
		overlay.updateProgress();
	}
	
	private void placeJobSite()
	{
		if(jobSite == null)
			throw new IllegalStateException("Job site is null.");
		
		if(!BlockUtils.getState(jobSite).canBeReplaced())
		{
			if(BlockUtils.getBlock(jobSite) == Blocks.LECTERN)
			{
				System.out.println("Job site has been placed.");
				placingJobSite = false;
				
			}else
			{
				System.out
					.println("Found wrong block at job site. Breaking...");
				breakingJobSite = true;
				placingJobSite = false;
			}
			
			return;
		}
		
		// check if holding a lectern
		if(!MC.player.isHolding(Items.LECTERN))
		{
			InventoryUtils.selectItem(Items.LECTERN, 36);
			return;
		}
		
		// get the hand that is holding the lectern
		InteractionHand hand = MC.player.getMainHandItem().is(Items.LECTERN)
			? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		
		// sneak-place to avoid activating trapdoors/chests/etc.
		IKeyBinding sneakKey = IKeyBinding.get(MC.options.keyShift);
		sneakKey.setDown(true);
		if(!MC.player.isShiftKeyDown())
			return;
		
		// get block placing params
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(jobSite);
		if(params == null)
		{
			sneakKey.resetPressedState();
			return;
		}
		
		// face block
		faceTarget.face(params.hitVec());
		
		// place block
		InteractionResult result =
			MC.gameMode.useItemOn(MC.player, hand, params.toHitResult());
		
		// swing hand
		if(result instanceof InteractionResult.Success success
			&& success.swingSource() == InteractionResult.SwingSource.CLIENT)
			swingHand.swing(hand);
		
		// reset sneak
		sneakKey.resetPressedState();
	}
	
	private void openTradeScreen()
	{
		if(MC.rightClickDelay > 0)
			return;
		
		MultiPlayerGameMode im = MC.gameMode;
		LocalPlayer player = MC.player;
		
		if(player.distanceToSqr(villager) > range.getValueSq())
		{
			ChatUtils.error("Villager is out of range. Consider trapping"
				+ " the villager so it doesn't wander away.");
			setEnabled(false);
			return;
		}
		
		// create realistic hit result
		AABB box = villager.getBoundingBox();
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 end = box.getCenter();
		Vec3 hitVec = box.clip(start, end).orElse(start);
		EntityHitResult hitResult = new EntityHitResult(villager, hitVec);
		
		// face end vector
		faceTarget.face(end);
		
		// click on villager
		InteractionHand hand = InteractionHand.MAIN_HAND;
		InteractionResult actionResult =
			im.interactAt(player, villager, hitResult, hand);
		
		if(!actionResult.consumesAction())
			im.interact(player, villager, hand);
		
		// swing hand
		if(actionResult instanceof InteractionResult.Success success
			&& success.swingSource() == InteractionResult.SwingSource.CLIENT)
			swingHand.swing(hand);
		
		// set cooldown
		MC.rightClickDelay = 4;
	}
	
	private void closeTradeScreen()
	{
		MC.player.closeContainer();
		MC.rightClickDelay = 4;
	}
	
	private BookOffer findEnchantedBookOffer(MerchantOffers tradeOffers)
	{
		for(MerchantOffer tradeOffer : tradeOffers)
		{
			ItemStack stack = tradeOffer.getResult();
			if(stack.getItem() != Items.ENCHANTED_BOOK)
				continue;
			
			Set<Entry<Holder<Enchantment>>> enchantmentLevelMap =
				EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet();
			if(enchantmentLevelMap.isEmpty())
				continue;
			
			Object2IntMap.Entry<Holder<Enchantment>> firstEntry =
				enchantmentLevelMap.stream().findFirst().orElseThrow();
			
			String enchantment = firstEntry.getKey().getRegisteredName();
			int level = firstEntry.getIntValue();
			int price = tradeOffer.getCostA().getCount();
			BookOffer bookOffer = new BookOffer(enchantment, level, price);
			
			if(!bookOffer.isFullyValid())
			{
				System.out.println("Found invalid enchanted book offer.\n"
					+ "Component data: " + enchantmentLevelMap);
				continue;
			}
			
			return bookOffer;
		}
		
		return null;
	}
	
	private void setTargetVillager()
	{
		LocalPlayer player = MC.player;
		double rangeSq = range.getValueSq();
		
		Stream<Villager> stream = StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), true)
			.filter(e -> !e.isRemoved()).filter(Villager.class::isInstance)
			.map(e -> (Villager)e).filter(e -> e.getHealth() > 0)
			.filter(e -> player.distanceToSqr(e) <= rangeSq)
			.filter(e -> e.getVillagerData().profession().unwrapKey()
				.orElse(null) == VillagerProfession.LIBRARIAN)
			.filter(e -> e.getVillagerData().level() == 1)
			.filter(e -> !experiencedVillagers.contains(e));
		
		villager =
			stream.min(Comparator.comparingDouble(e -> player.distanceToSqr(e)))
				.orElse(null);
		
		if(villager == null)
		{
			String errorMsg = "Couldn't find a nearby librarian.";
			int numExperienced = experiencedVillagers.size();
			if(numExperienced > 0)
				errorMsg += " (Except for " + numExperienced + " that "
					+ (numExperienced == 1 ? "is" : "are")
					+ " already experienced.)";
			
			ChatUtils.error(errorMsg);
			ChatUtils.message("Make sure both the librarian and the lectern"
				+ " are reachable from where you are standing.");
			setEnabled(false);
			return;
		}
		
		System.out.println("Found villager at " + villager.blockPosition());
	}
	
	private void setTargetJobSite()
	{
		Vec3 eyesVec = RotationUtils.getEyesPos();
		double rangeSq = range.getValueSq();
		
		Stream<BlockPos> stream = BlockUtils
			.getAllInBoxStream(BlockPos.containing(eyesVec),
				range.getValueCeil())
			.filter(
				pos -> eyesVec.distanceToSqr(Vec3.atCenterOf(pos)) <= rangeSq)
			.filter(pos -> BlockUtils.getBlock(pos) == Blocks.LECTERN);
		
		jobSite = stream
			.min(Comparator.comparingDouble(
				pos -> villager.distanceToSqr(Vec3.atCenterOf(pos))))
			.orElse(null);
		
		if(jobSite == null)
		{
			ChatUtils.error("Couldn't find the librarian's lectern.");
			ChatUtils.message("Make sure both the librarian and the lectern"
				+ " are reachable from where you are standing.");
			setEnabled(false);
			return;
		}
		
		System.out.println("Found lectern at " + jobSite);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		int green = 0xC000FF00;
		int red = 0xC0FF0000;
		
		if(villager != null)
			RenderUtils.drawOutlinedBox(matrixStack, villager.getBoundingBox(),
				green, false);
		
		if(jobSite != null)
			RenderUtils.drawOutlinedBox(matrixStack, new AABB(jobSite), green,
				false);
		
		List<AABB> expVilBoxes = experiencedVillagers.stream()
			.map(Villager::getBoundingBox).toList();
		RenderUtils.drawOutlinedBoxes(matrixStack, expVilBoxes, red, false);
		RenderUtils.drawCrossBoxes(matrixStack, expVilBoxes, red, false);
		
		if(breakingJobSite)
			overlay.render(matrixStack, partialTicks, jobSite);
	}
}