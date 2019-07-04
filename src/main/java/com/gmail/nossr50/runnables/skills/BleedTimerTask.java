package com.gmail.nossr50.runnables.skills;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.skills.BleedContainer;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.skills.ParticleEffectUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class BleedTimerTask extends BukkitRunnable {
    private final mcMMO pluginRef;
    private Map<LivingEntity, BleedContainer> bleedList;

    public BleedTimerTask(mcMMO pluginRef) {
        this.pluginRef = pluginRef;
        bleedList = new HashMap<>();
    }

    private BleedContainer copyContainer(BleedContainer container) {
        LivingEntity target = container.target;
        LivingEntity source = container.damageSource;
        int bleedTicks = container.bleedTicks;
        int bleedRank = container.bleedRank;
        int toolTier = container.toolTier;

        return new BleedContainer(target, bleedTicks, bleedRank, toolTier, source);
    }

    /**
     * Instantly Bleed out a LivingEntity
     *
     * @param entity LivingEntity to bleed out
     */
    public void bleedOut(LivingEntity entity) {
        /*
         * Don't remove anything from the list outside of run()
         */

        if (bleedList.containsKey(entity)) {
            pluginRef.getCombatTools().dealNoInvulnerabilityTickDamage(entity, bleedList.get(entity).bleedTicks * 2, bleedList.get(entity).damageSource);
        }
    }

    /**
     * Add a LivingEntity to the bleedList if it is not in it.
     *
     * @param entity LivingEntity to add
     * @param ticks  Number of bleeding ticks
     */
    public void add(LivingEntity entity, LivingEntity attacker, int ticks, int bleedRank, int toolTier) {
        if (toolTier < 4)
            ticks = Math.max(1, (ticks / 3));

        BleedContainer newBleedContainer = new BleedContainer(entity, ticks, bleedRank, toolTier, attacker);
        bleedList.put(entity, newBleedContainer);
    }

    public boolean isBleeding(LivingEntity entity) {
        return bleedList.containsKey(entity);
    }

    @Override
    public void run() {
        Iterator<Entry<LivingEntity, BleedContainer>> bleedIterator = bleedList.entrySet().iterator();

        while (bleedIterator.hasNext()) {
            Entry<LivingEntity, BleedContainer> containerEntry = bleedIterator.next();
            LivingEntity target = containerEntry.getKey();
            int toolTier = containerEntry.getValue().toolTier;

//            String debugMessage = "";
//            debugMessage += ChatColor.GOLD + "Target ["+target.getName()+"]: " + ChatColor.RESET;

//            debugMessage+="RemainingTicks=["+containerEntry.getValue().bleedTicks+"], ";

            if (containerEntry.getValue().bleedTicks <= 0 || !target.isValid()) {
                if (target instanceof Player) {
                    pluginRef.getNotificationManager().sendPlayerInformation((Player) target, NotificationType.SUBSKILL_MESSAGE, "Swords.Combat.Bleeding.Stopped");
                }

                bleedIterator.remove();
                continue;
            }

            int armorCount = 0;

            double damage;

            if (target instanceof Player) {
                damage = pluginRef.getConfigManager().getConfigSwords().getRuptureDamagePlayer();

                //Above Bleed Rank 3 deals 50% more damage
                if (containerEntry.getValue().toolTier >= 4 && containerEntry.getValue().bleedRank >= 3)
                    damage = damage * 1.5;

                Player player = (Player) target;

                if (!player.isOnline()) {
                    continue;
                }

                //Count Armor
                for (ItemStack armorPiece : ((Player) target).getInventory().getArmorContents()) {
                    if (armorPiece != null)
                        armorCount++;
                }

            } else {
                damage = pluginRef.getConfigManager().getConfigSwords().getRuptureDamageMobs();

//                debugMessage+="BaseDMG=["+damage+"], ";

                //Above Bleed Rank 3 deals 50% more damage
                if (containerEntry.getValue().bleedRank >= 3) {
                    damage = damage * 1.5;
                }

//                debugMessage+="Rank4Bonus=["+String.valueOf(containerEntry.getValue().bleedRank >= 3)+"], ";


                pluginRef.getMobHealthBarManager().handleMobHealthbars(target, damage, pluginRef); //Update health bars
            }

//            debugMessage+="FullArmor=["+String.valueOf(armorCount > 3)+"], ";

            if (armorCount > 3) {
                damage = damage * .75;
            }

//            debugMessage+="AfterRankAndArmorChecks["+damage+"], ";

            //Weapons below Diamond get damage cut in half
            if (toolTier < 4)
                damage = damage / 2;

//            debugMessage+="AfterDiamondCheck=["+String.valueOf(damage)+"], ";

            //Wood weapons get damage cut in half again
            if (toolTier < 2)
                damage = damage / 2;

//            debugMessage+="AfterWoodenCheck=["+String.valueOf(damage)+"], ";

            double victimHealth = target.getHealth();

//            debugMessage+="TargetHealthBeforeDMG=["+String.valueOf(target.getHealth())+"], ";

            pluginRef.getCombatTools().dealNoInvulnerabilityTickDamageRupture(target, damage, containerEntry.getValue().damageSource, toolTier);

            double victimHealthAftermath = target.getHealth();

//            debugMessage+="TargetHealthAfterDMG=["+String.valueOf(target.getHealth())+"], ";

            if (victimHealthAftermath <= 0 || victimHealth != victimHealthAftermath) {
                //Play Bleed Sound
                SoundManager.worldSendSound(target.getWorld(), target.getLocation(), SoundType.BLEED);

                ParticleEffectUtils.playBleedEffect(target);
            }

            //Lower Bleed Ticks
            BleedContainer loweredBleedContainer = copyContainer(containerEntry.getValue());
            loweredBleedContainer.bleedTicks -= 1;

//            debugMessage+="RemainingTicks=["+loweredBleedContainer.bleedTicks+"]";
            containerEntry.setValue(loweredBleedContainer);

//            Bukkit.broadcastMessage(debugMessage);
        }
    }
}
