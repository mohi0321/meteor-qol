package com.example.addon.modules;

import java.util.Objects;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import pwn.noobs.trouserstreak.Trouser;

public class AttributeSwap extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Integer> targetSlot;
   private final Setting<Boolean> swapBack;
   private final Setting<Integer> delay;
   private int prevSlot;
   private int dDelay;

   public AttributeSwap() {
      super(Trouser.Main, "attribute-swap", "Swaps attributes of the main hand item with the target slot on attack");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.targetSlot = this.sgGeneral.add(((Builder)((Builder)((Builder)(new Builder()).name("target-slot")).description("The hotbar slot to swap to when attacking.")).sliderRange(1, 9).defaultValue(1)).min(1).build());
      this.swapBack = this.sgGeneral.add(((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()).name("swap-back")).description("Swap back to the original slot after a short delay.")).defaultValue(true)).build());
      SettingGroup var10001 = this.sgGeneral;
      Builder var10002 = ((Builder)((Builder)((Builder)(new Builder()).name("swap-back-delay")).description("Delay in ticks before swapping back to the previous slot.")).sliderRange(1, 20).defaultValue(1)).min(1);
      Setting var10003 = this.swapBack;
      Objects.requireNonNull(var10003);
      this.delay = var10001.add(((Builder)var10002.visible(var10003::get)).build());
      this.prevSlot = -1;
      this.dDelay = 0;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if ((Boolean)this.swapBack.get()) {
            this.prevSlot = this.mc.field_1724.method_31548().field_7545;
         }

         InvUtils.swap((Integer)this.targetSlot.get() - 1, false);
         if ((Boolean)this.swapBack.get() && this.prevSlot != -1) {
            this.dDelay = (Integer)this.delay.get();
         }

      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.dDelay > 0) {
         --this.dDelay;
         if (this.dDelay == 0 && this.prevSlot != -1) {
            InvUtils.swap(this.prevSlot, false);
            this.prevSlot = -1;
         }
      }

   }
}
    