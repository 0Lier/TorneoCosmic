package com.cosmicraft.torneocosmic;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Construye los items del kit de duelo a partir de la sección "kit" del
 * config.yml y se los entrega a los jugadores al iniciar un 1v1.
 */
public class KitManager {

    private final torneocosmic plugin;

    public KitManager(torneocosmic plugin) {
        this.plugin = plugin;
    }

    /**
     * Limpia por completo el inventario del jugador y le entrega el kit
     * configurado en config.yml (armadura + slots de inventario).
     */
    public void applyKit(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);

        ConfigurationSection kitSection = plugin.getConfig().getConfigurationSection("kit");
        if (kitSection == null) {
            plugin.getLogger().warning("No se encontró la sección 'kit' en config.yml. Se entregará un kit vacío.");
            return;
        }

        ConfigurationSection armor = kitSection.getConfigurationSection("armadura");
        if (armor != null) {
            inv.setHelmet(buildItem(armor.getConfigurationSection("helmet")));
            inv.setChestplate(buildItem(armor.getConfigurationSection("chestplate")));
            inv.setLeggings(buildItem(armor.getConfigurationSection("leggings")));
            inv.setBoots(buildItem(armor.getConfigurationSection("boots")));
        }

        ConfigurationSection slots = kitSection.getConfigurationSection("slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot < 0 || slot > 35) {
                        plugin.getLogger().warning("Slot inválido en config.yml: " + key + " (debe ser 0-35)");
                        continue;
                    }
                    ItemStack item = buildItem(slots.getConfigurationSection(key));
                    if (item != null) {
                        inv.setItem(slot, item);
                    }
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Slot inválido en config.yml: " + key + " (debe ser un número 0-35)");
                }
            }
        }

        player.updateInventory();
    }

    private ItemStack buildItem(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String materialName = section.getString("material");
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }

        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Material inválido en config.yml: " + materialName);
            return null;
        }

        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = section.getString("name");
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }

            List<String> lore = section.getStringList("lore");
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(colored);
            }

            item.setItemMeta(meta);
        }

        ConfigurationSection enchants = section.getConfigurationSection("enchantments");
        if (enchants != null) {
            for (Map.Entry<String, Object> entry : enchants.getValues(false).entrySet()) {
                try {
                    NamespacedKey key = NamespacedKey.minecraft(entry.getKey().toLowerCase());
                    Enchantment enchantment = Registry.ENCHANTMENT.get(key);
                    if (enchantment == null) {
                        plugin.getLogger().warning("Encantamiento inválido en config.yml: " + entry.getKey());
                        continue;
                    }
                    int level = Integer.parseInt(String.valueOf(entry.getValue()));
                    item.addUnsafeEnchantment(enchantment, level);
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Nivel de encantamiento inválido para: " + entry.getKey());
                }
            }
        }

        return item;
    }
}
