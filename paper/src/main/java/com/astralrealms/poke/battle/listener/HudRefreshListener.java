package com.astralrealms.poke.battle.listener;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.astralrealms.core.paper.AstralPaperAPI;
import com.astralrealms.core.paper.model.action.PaperAction;
import com.astralrealms.core.paper.model.action.impl.DefaultPaperActionContext;
import com.astralrealms.core.paper.model.action.impl.DefaultPaperActionFactory;
import com.astralrealms.core.platform.executable.exception.ExecutableParseException;
import com.astralrealms.core.platform.executable.exception.ExecutableRunException;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.api.event.BattleEndEvent;
import com.astralrealms.poke.battle.api.event.BattleResumeEvent;
import com.astralrealms.poke.battle.api.event.BattleStartEvent;

/**
 * Re-picks the player's AstralHud boss bar containers when a battle starts, resumes or ends, so the ones gated on
 * {@code %pokecore_in_battle%} go away and come back at once instead of at the next rotation. Runs AstralHud's global
 * {@code [hud-update]} action: only registered when AstralHud is enabled, with no compile dependency on it.
 */
public class HudRefreshListener implements Listener {

    private final PokeBattle plugin;
    private final PaperAction update;

    public HudRefreshListener(PokeBattle plugin) throws ExecutableParseException {
        this.plugin = plugin;
        this.update = DefaultPaperActionFactory.INSTANCE.create(plugin, "[hud-update]");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(BattleStartEvent event) {
        this.refresh(event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResume(BattleResumeEvent event) {
        this.refresh(event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(BattleEndEvent event) {
        Player player = Bukkit.getPlayer(event.playerId());
        if (player != null)
            this.refresh(player);
    }

    // [hud-update] moves to the player's own thread itself
    private void refresh(Player player) {
        try {
            this.update.run(new DefaultPaperActionContext(player, List.of(), AstralPaperAPI.createPlaceholderContainer(player)));
        } catch (ExecutableRunException e) {
            this.plugin.getSLF4JLogger().warn("Could not refresh the HUD of {}", player.getName(), e);
        }
    }
}
