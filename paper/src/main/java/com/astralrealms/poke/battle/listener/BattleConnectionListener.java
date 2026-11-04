package com.astralrealms.poke.battle.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.astralrealms.poke.battle.service.BattleServiceImpl;

/**
 * A player who leaves mid-battle has a grace period to come back to this server; past it the battle is lost with no
 * penalty. Coming back reopens the pending choice.
 */
public class BattleConnectionListener implements Listener {

    private final BattleServiceImpl battles;

    public BattleConnectionListener(BattleServiceImpl battles) {
        this.battles = battles;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.battles.onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.battles.onJoin(event.getPlayer());
    }
}
