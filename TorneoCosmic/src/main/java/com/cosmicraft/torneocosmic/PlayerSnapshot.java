package com.cosmicraft.torneocosmic;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda una "foto" completa del estado de un jugador (inventario, armadura,
 * vida, hambre, experiencia, ubicación, modo de juego y efectos de poción)
 * para poder devolverlo exactamente a como estaba una vez termine el 1v1.
 */
public class PlayerSnapshot {

    private final Location location;
    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private final float exp;
    private final int level;
    private final int totalExperience;
    private final GameMode gameMode;
    private final List<PotionEffect> potionEffects;
    private final boolean allowFlight;
    private final boolean flying;
    private final int fireTicks;

    public PlayerSnapshot(Player player) {
        this.location = player.getLocation().clone();
        this.inventoryContents = clone(player.getInventory().getContents());
        this.armorContents = clone(player.getInventory().getArmorContents());
        this.offHand = player.getInventory().getItemInOffHand().clone();

        double baseMax = 20.0D;
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            baseMax = player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        }
        this.maxHealth = baseMax;
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.totalExperience = player.getTotalExperience();
        this.gameMode = player.getGameMode();
        this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.fireTicks = player.getFireTicks();
    }

    private static ItemStack[] clone(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    /**
     * Devuelve al jugador exactamente al estado guardado.
     */
    public void restore(Player player) {
        player.getInventory().setContents(clone(inventoryContents));
        player.getInventory().setArmorContents(clone(armorContents));
        player.getInventory().setItemInOffHand(offHand.clone());
        player.updateInventory();

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }

        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        }
        player.setHealth(Math.min(health, maxHealth));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExp(exp);
        player.setLevel(level);
        player.setTotalExperience(totalExperience);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && allowFlight);
        player.setFireTicks(fireTicks);

        player.teleport(location);
    }

    public Location getLocation() {
        return location;
    }
}
