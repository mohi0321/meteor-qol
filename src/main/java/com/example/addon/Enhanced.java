package com.example.addon;
import com.example.addon.modules.Automation.AutoLibrarian;
import com.example.addon.modules.Automation.AutoAuction;
import com.example.addon.modules.Automation.AutoChat;
import com.example.addon.modules.Visuals.VillagerESP;
import com.example.addon.modules.Visuals.InventoryInteractionDebug;
import com.example.addon.modules.Automation.EnchantedBookBuyer;
import com.example.addon.commands.CommandExample;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.Visuals.VillagerEsp;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Enhanced extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category Automation = new Category("Enhanced Automation");
    public static final Category Visuals = new Category("Enhanced Visuals");
    public static final HudGroup HUD_GROUP = new HudGroup("Enhanced");




    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Addon Template");

        // Modules
        Modules.get().add(new AutoLibrarian());
        Modules.get().add(new AutoChat());
        Modules.get().add(new InventoryInteractionDebug());
        Modules.get().add(new AutoAuction());
        Modules.get().add(new EnchantedBookBuyer());
        Modules.get().add(new VillagerEsp());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(Automation);
        Modules.registerCategory(Visuals);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Mohi0321", "meteor-qol");
    }
}
