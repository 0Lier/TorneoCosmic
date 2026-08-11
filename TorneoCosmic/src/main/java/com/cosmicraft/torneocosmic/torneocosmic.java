package com.cosmicraft.torneocosmic;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
}
