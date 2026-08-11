package com.cosmicraft.torneocosmic;

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
        PlayerSnapshot snapshot = plugin.getPendingRestores().remove(player.getUniqueId());
        if (snapshot != null) {
            // Se restaura un tick después para asegurar que el jugador ya cargó.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> snapshot.restore(player), 5L);
        }
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

        // Durante la cuenta regresiva / animación previa nadie recibe daño.
        if (duelManager.isFreeze()) {
            event.setCancelled(true);
            return;
        }

        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = resolveAttackingPlayer(byEntity.getDamager());
        }

        boolean isEnvironmental = attacker == null && !(event instanceof EntityDamageByEntityEvent);

        // Solo se permite daño proveniente del rival o del entorno (caídas,
        // fuego, ahogo, etc). Cualquier interferencia externa se bloquea.
        if (!isEnvironmental && (attacker == null || !duelManager.isFighter(attacker) || attacker.equals(victim))) {
            event.setCancelled(true);
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (victim.getHealth() - finalDamage <= 0) {
            // Se cancela el golpe letal: nunca muere de verdad, solo se
            // declara perdedor y se restaura su estado original.
            event.setCancelled(true);
            if (victim.getHealth() <= 0) {
                victim.setHealth(1.0D);
            }
            if (attacker != null) {
                duelManager.addDamage(attacker, event.getDamage());
            }
            duelManager.endMatchByDeath(victim);
            return;
        }

        if (attacker != null) {
            duelManager.addDamage(attacker, event.getDamage());
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
