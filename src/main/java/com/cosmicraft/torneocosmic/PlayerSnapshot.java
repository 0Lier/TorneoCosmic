package com.cosmicraft.torneocosmic;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

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
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        this.offHand = offHandItem != null ? offHandItem.clone() : null;

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

    public PlayerSnapshot(ConfigurationSection section) {
        this.location = section.getLocation("location");
        List<?> invList = section.getList("inventoryContents");
        this.inventoryContents = invList != null ? invList.toArray(new ItemStack[0]) : new ItemStack[0];
        List<?> armorList = section.getList("armorContents");
        this.armorContents = armorList != null ? armorList.toArray(new ItemStack[0]) : new ItemStack[0];
        this.offHand = section.getItemStack("offHand");
        this.health = section.getDouble("health");
        this.maxHealth = section.getDouble("maxHealth");
        this.foodLevel = section.getInt("foodLevel");
        this.saturation = (float) section.getDouble("saturation");
        this.exp = (float) section.getDouble("exp");
        this.level = section.getInt("level");
        this.totalExperience = section.getInt("totalExperience");
        String gm = section.getString("gameMode");
        this.gameMode = gm != null ? GameMode.valueOf(gm) : GameMode.SURVIVAL;
        List<?> potions = section.getList("potionEffects");
        this.potionEffects = new ArrayList<>();
        if (potions != null) {
            for (Object obj : potions) {
                if (obj instanceof PotionEffect) {
                    this.potionEffects.add((PotionEffect) obj);
                }
            }
        }
        this.allowFlight = section.getBoolean("allowFlight");
        this.flying = section.getBoolean("flying");
        this.fireTicks = section.getInt("fireTicks");
    }

    public void save(ConfigurationSection section) {
        section.set("location", location);
        section.set("inventoryContents", inventoryContents);
        section.set("armorContents", armorContents);
        section.set("offHand", offHand);
        section.set("health", health);
        section.set("maxHealth", maxHealth);
        section.set("foodLevel", foodLevel);
        section.set("saturation", saturation);
        section.set("exp", exp);
        section.set("level", level);
        section.set("totalExperience", totalExperience);
        section.set("gameMode", gameMode.name());
        section.set("potionEffects", potionEffects);
        section.set("allowFlight", allowFlight);
        section.set("flying", flying);
        section.set("fireTicks", fireTicks);
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
        if (offHand != null) {
            player.getInventory().setItemInOffHand(offHand.clone());
        } else {
            player.getInventory().setItemInOffHand(null);
        }
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
