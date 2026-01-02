/*package com.example.addon.modules;

import com.example.addon.Enhanced;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;


public class AuctionSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");   

    //settings
    private final Setting<String> item = sgGeneral.add(new StringSetting.Builder()
        .name("item")
        .description("Item to sell. Format: 'item_id'")
        .defaultValue("minecraft:enchanted_book")
        .build()
    );

    private final Setting<String> price = sgGeneral.add(new StringSetting.Builder()
        .name("price")
        .description("Price to sell items for.")
        .defaultValue("799")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in Seconds between auctions.")
        .defaultValue(10)
        .build()
    );
}
    

@Override
public void onActivate() {
    price = Integer.parseInt(price.get());
    item = item.get();
    delay = delay.get()*20;
}

@EventHandler
private void onTick(TickEvent.Pre event) {
    if (mc.world == null || mc.player == null) return;

    if (mc.player.getInventory().getMainHandStack().getItem() == Items.AIR) {
        FindItemResult itemResult = InvUtils.find(item);
        if (itemResult.found()) {
            InvUtils.clickSlot(itemResult.slot());
        }
    }

    if (mc.player.getInventory().getMainHandStack().getItem() == Items.ENCHANTED_BOOK) {
        ChatUtils.sendPlayerMsg("/ah")

    }
}*/