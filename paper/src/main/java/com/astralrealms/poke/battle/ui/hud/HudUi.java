package com.astralrealms.poke.battle.ui.hud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.astralHologram.api.event.HologramHoverEvent;
import com.astralrealms.astralHologram.hud.HudCloseReason;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.ui.BattleUi;
import com.astralrealms.poke.battle.ui.ChoiceDialogs;
import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.battle.ui.FieldView;

import net.kyori.adventure.text.Component;

/**
 * The battle drawn in front of the player with an AstralHologram HUD, built in code from {@code hud} in
 * {@code config.yml} (its texts from {@code messages.yml}). Only loaded when AstralHologram is installed.
 * <p>
 * Falls back to the dialog for a player who cannot see a HUD (Bedrock), when {@code hud.enabled} is false, and for
 * the rest of a battle whose HUD failed to open. A player who left the HUD gets the log in the chat and the HUD back
 * with their next choice (or {@code /combat}).
 * <p>
 * Main thread only (the battle calls it on the player's thread, and PokeBattle does not run on Folia).
 */
public class HudUi implements BattleUi, Listener {

    static final String HUD_ID = "pokebattle";
    private static final String HOLOGRAM_ID = "hud:" + HUD_ID;

    private final PokeBattle plugin;
    private final ChoiceDialogs.Handler handler;
    private final BattleUi fallback;
    private final Map<UUID, BattleHud> huds = new HashMap<>();
    private final Set<UUID> failed = new HashSet<>();

    public HudUi(PokeBattle plugin, ChoiceDialogs.Handler handler, BattleUi fallback) {
        this.plugin = plugin;
        this.handler = handler;
        this.fallback = fallback;
    }

    PokeBattle plugin() {
        return this.plugin;
    }

    ChoiceDialogs.Handler handler() {
        return this.handler;
    }

    @Override
    public void open(Player player, FieldView field) {
        if (this.drawn(player, field.battleId()))
            this.hud(player, field);
        else
            this.fallback.open(player, field);
    }

    @Override
    public void update(Player player, FieldView field, List<Component> log) {
        BattleHud hud = this.active(player, field.battleId());
        if (hud == null) {
            this.fallback.update(player, field, log);
            return;
        }
        PokeBattleConfiguration.Hud config = this.plugin.configuration().hud();
        if (config != null && config.chatLog())
            log.forEach(player::sendMessage);
        hud.update(field, log);
    }

    @Override
    public void choose(Player player, ChoiceView view) {
        if (this.drawn(player, view.battleId()))
            this.hud(player, FieldView.of(view)).choose(view);
        else
            this.fallback.choose(player, view);
    }

    @Override
    public void waiting(Player player) {
        BattleHud hud = this.huds.get(player.getUniqueId());
        if (hud != null && !hud.closed())
            hud.waiting();
        this.fallback.waiting(player);
    }

    @Override
    public void close(Player player, UUID battleId) {
        this.failed.remove(battleId);
        this.fallback.close(player, battleId);
        BattleHud hud = this.active(player, battleId);
        if (hud == null)
            return;
        hud.ended();
        PokeBattleConfiguration.Hud config = this.plugin.configuration().hud();
        long ticks = config == null || config.closeDelay() == null ? 0 : config.closeDelay().toMillis() / 50;
        if (ticks <= 0) {
            this.remove(player.getUniqueId(), hud);
            return;
        }
        player.getScheduler().runDelayed(this.plugin, _ -> this.remove(player.getUniqueId(), hud), null, ticks);
    }

    @Override
    public void shutdown() {
        List.copyOf(this.huds.values()).forEach(BattleHud::close);
        this.huds.clear();
        this.failed.clear();
        this.fallback.shutdown();
    }

    // ---- Sessions ----

    /**
     * The player's HUD for this battle, opened if it is not.
     */
    private BattleHud hud(Player player, FieldView field) {
        BattleHud hud = this.active(player, field.battleId());
        if (hud != null)
            return hud;
        BattleHud previous = this.huds.remove(player.getUniqueId());
        if (previous != null)
            previous.close();
        hud = new BattleHud(this, player, field, this.plugin.configuration().hud(),
                this.plugin.configuration().battle().turnTimerSeconds());
        this.huds.put(player.getUniqueId(), hud);
        hud.open();
        return hud;
    }

    private @Nullable BattleHud active(Player player, UUID battleId) {
        BattleHud hud = this.huds.get(player.getUniqueId());
        return hud != null && !hud.closed() && hud.battleId().equals(battleId) ? hud : null;
    }

    private void remove(UUID playerId, BattleHud hud) {
        this.huds.remove(playerId, hud);
        hud.close();
    }

    /**
     * Whether this player sees the battle as a HUD.
     */
    private boolean drawn(Player player, UUID battleId) {
        PokeBattleConfiguration.Hud config = this.plugin.configuration().hud();
        return config != null && config.enabled() && !bedrock(player) && !this.failed.contains(battleId);
    }

    /**
     * Floodgate gives Bedrock players a UUID whose upper half is 0. They would see nothing of a HUD: display
     * entities do not exist on Bedrock.
     */
    private static boolean bedrock(Player player) {
        return player.getUniqueId().getMostSignificantBits() == 0;
    }

    /**
     * The HUD could not open (cancelled, no room for the seat...): this battle goes on with the dialog.
     */
    void failed(BattleHud hud, Throwable error) {
        this.plugin.getSLF4JLogger().warn("Battle {}: the HUD could not open, using the dialog", hud.battleId(), error);
        this.failed.add(hud.battleId());
        Player player = hud.player();
        this.huds.remove(player.getUniqueId(), hud);
        ChoiceView pending = hud.pending();
        if (player.isOnline() && pending != null)
            this.fallback.choose(player, pending);
    }

    /**
     * The player left the HUD (sneak, another HUD, a teleport...) while the battle goes on.
     */
    void closed(BattleHud hud, HudCloseReason reason) {
        Player player = hud.player();
        this.huds.remove(player.getUniqueId(), hud);
        if (player.isOnline() && reason != HudCloseReason.QUIT && reason != HudCloseReason.DISABLE)
            PokeBattleMessages.HUD_CLOSED.message(player);
    }

    @EventHandler
    public void onHover(HologramHoverEvent event) {
        if (!event.hologram().equals(HOLOGRAM_ID))
            return;
        BattleHud hud = this.huds.get(event.getPlayer().getUniqueId());
        if (hud != null)
            hud.hover(event.component(), event.entered());
    }
}
