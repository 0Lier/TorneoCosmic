package com.cosmicraft.torneocosmic;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DuelListener implements Listener {

    private final torneocosmic plugin;

    public DuelListener(torneocosmic plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Revisar y aplicar la vida de ganador absoluto apenas entra
        plugin.updateAbsoluteWinnerHealth(player);

        PlayerSnapshot snapshot = plugin.getPendingRestores().get(player.getUniqueId());
        if (snapshot != null) {
            plugin.removeRestore(player.getUniqueId());
            // Se restaura un tick después para asegurar que el jugador ya cargó.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    snapshot.restore(player);
                    // Volvemos a aplicar la salud por si el snapshot lo regresó a 20 de vida
                    plugin.updateAbsoluteWinnerHealth(player);
                }
            }, 5L);
        }
        
        // Añadir espectador o reconectado a la BossBar
        plugin.getDuelManager().addPlayerToBossBar(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        DuelManager duelManager = plugin.getDuelManager();
        if (duelManager.isFighter(event.getPlayer())) {
            duelManager.handleFighterQuit(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        DuelManager duelManager = plugin.getDuelManager();
        if (!duelManager.isFreeze()) {
            return;
        }
        Player player = event.getPlayer();
        if (!duelManager.isFighter(player)) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        DuelManager duelManager = plugin.getDuelManager();
        if (!duelManager.isFighter(victim)) {
            return;
        }

        if (duelManager.isFreeze()) {
            event.setCancelled(true);
            return;
        }

        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = resolveAttackingPlayer(byEntity.getDamager());
        }

        boolean isEnvironmental = attacker == null && !(event instanceof EntityDamageByEntityEvent);

        if (!isEnvironmental && (attacker == null || !duelManager.isFighter(attacker) || attacker.equals(victim))) {
            event.setCancelled(true);
            return;
        }

        // --- EFECTOS VISUALES Y DE SONIDO DURANTE EL PELEA ---
        if (attacker != null) {
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.2, 0.3, 0.2, 0.1);
        }

        double finalDamage = event.getFinalDamage();
        if (victim.getHealth() - finalDamage <= 0) {
            event.setCancelled(true);
            if (victim.getHealth() <= 0) {
                victim.setHealth(1.0D);
            }
            if (attacker != null) {
                duelManager.addDamage(attacker, event.getDamage());
            }
            
            // Sonido de golpe final impactante
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            
            duelManager.endMatchByDeath(victim);
            return;
        }

        if (attacker != null) {
            duelManager.addDamage(attacker, event.getDamage());
        }
    }

    @EventHandler
    public void onDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (plugin.getDuelManager().isFighter(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "No puedes tirar ítems durante el combate.");
        }
    }

    @EventHandler
    public void onPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getDuelManager().isFighter(player)) {
                event.setCancelled(true);
            }
        }
    }

    private static final java.util.List<String> BLOCKED_COMMANDS = java.util.Arrays.asList(
        "/tpa", "/tpaccept", "/tpdeny", "/home", "/spawn", "/lobby", "/suicide", "/tp", "/warp", "/back", "/hub"
    );

    @EventHandler
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (plugin.getDuelManager().isFighter(event.getPlayer())) {
            String msg = event.getMessage().toLowerCase();
            for (String cmd : BLOCKED_COMMANDS) {
                if (msg.startsWith(cmd + " ") || msg.equals(cmd)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "No puedes usar este comando mientras estás en un duelo.");
                    return;
                }
            }
        }
    }

    private Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}