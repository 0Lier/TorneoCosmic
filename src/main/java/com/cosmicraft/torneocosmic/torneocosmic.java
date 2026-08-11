package com.cosmicraft.torneocosmic;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

public final class torneocosmic extends JavaPlugin implements Listener {

    private static torneocosmic instance;

    private KitManager kitManager;
    private DuelManager duelManager;

    // Guarda el estado de un peleador que se desconectó a mitad de combate,
    // para restaurarlo apenas vuelva a entrar (ver DuelListener).
    private final Map<UUID, PlayerSnapshot> pendingRestores = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        validateConfiguration();
        loadRestores();

        this.kitManager = new KitManager(this);
        this.duelManager = new DuelManager(this);

        TournamentCommand commandExecutor = new TournamentCommand(this);
        getCommand("tournament").setExecutor(commandExecutor);
        getCommand("tournament").setTabCompleter(commandExecutor);

        getServer().getPluginManager().registerEvents(new DuelListener(this), this);

        getLogger().info("torneocosmic: Sistema de Torneo habilitado.");
    }

    @Override
    public void onDisable() {
        if (duelManager != null && duelManager.isActive()) {
            duelManager.stopDuel(getServer().getConsoleSender(), "El servidor se está apagando.");
        }
        getLogger().info("torneocosmic deshabilitado.");
    }

    public static torneocosmic getInstance() {
        return instance;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public Map<UUID, PlayerSnapshot> getPendingRestores() {
        return pendingRestores;
    }

    private void validateConfiguration() {
        ConfigurationSection config = getConfig();
        if (!config.contains("duelo.duracion-segundos")) {
            getLogger().warning("Falta 'duelo.duracion-segundos' en config.yml");
        }
        if (!config.contains("kit")) {
            getLogger().warning("Falta sección 'kit' en config.yml");
        }
        if (!config.contains("posiciones.pos1") || !config.contains("posiciones.pos2")) {
            getLogger().info("Posiciones no configuradas. Usa /tournament set pos1 y pos2");
        }
    }

    private File restoresFile;
    private FileConfiguration restoresConfig;

    private void loadRestores() {
        restoresFile = new File(getDataFolder(), "restores.yml");
        if (!restoresFile.exists()) {
            try { restoresFile.createNewFile(); } catch (Exception e) {}
        }
        restoresConfig = YamlConfiguration.loadConfiguration(restoresFile);
        
        ConfigurationSection restoresSec = restoresConfig.getConfigurationSection("pendingRestores");
        if (restoresSec != null) {
            for (String key : restoresSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    PlayerSnapshot snap = new PlayerSnapshot(restoresSec.getConfigurationSection(key));
                    pendingRestores.put(uuid, snap);
                } catch (Exception e) {}
            }
        }
    }

    public void saveRestore(UUID uuid, PlayerSnapshot snapshot) {
        pendingRestores.put(uuid, snapshot);
        ConfigurationSection restoresSec = restoresConfig.getConfigurationSection("pendingRestores");
        if (restoresSec == null) restoresSec = restoresConfig.createSection("pendingRestores");
        snapshot.save(restoresSec.createSection(uuid.toString()));
        try { restoresConfig.save(restoresFile); } catch (Exception e) {}
    }

    public void removeRestore(UUID uuid) {
        pendingRestores.remove(uuid);
        ConfigurationSection restoresSec = restoresConfig.getConfigurationSection("pendingRestores");
        if (restoresSec != null) {
            restoresSec.set(uuid.toString(), null);
            try { restoresConfig.save(restoresFile); } catch (Exception e) {}
        }
    }

    // ================= MÉTODOS DE GANADOR ABSOLUTO =================

    public void addAbsoluteWinner(UUID uuid) {
        java.util.List<String> winners = getConfig().getStringList("absolute-winners");
        if (!winners.contains(uuid.toString())) {
            winners.add(uuid.toString());
            getConfig().set("absolute-winners", winners);
            saveConfig();
        }
    }

    public void removeAbsoluteWinner(UUID uuid) {
        java.util.List<String> winners = getConfig().getStringList("absolute-winners");
        if (winners.contains(uuid.toString())) {
            winners.remove(uuid.toString());
            getConfig().set("absolute-winners", winners);
            saveConfig();
        }
    }

    public boolean isAbsoluteWinner(UUID uuid) {
        return getConfig().getStringList("absolute-winners").contains(uuid.toString());
    }

    public void updateAbsoluteWinnerHealth(org.bukkit.entity.Player player) {
        if (player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            if (isAbsoluteWinner(player.getUniqueId())) {
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(22.0); // 11 Corazones
            } else {
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0); // 10 Corazones (Default)
            }
        }
    }
}
