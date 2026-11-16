package com.astralrealms.poke.battle.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

class PacedUiTest {

    /** A player that only has an id: an unpaced UI never schedules anything. */
    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** Plays nothing over time: every call shows at once. */
    private static final class Recording implements BattleUi {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void open(Player player, FieldView field) {
            this.calls.add("open");
        }

        @Override
        public void update(Player player, BattleBeat beat) {
            this.calls.add("update");
        }

        @Override
        public void choose(Player player, ChoiceView view) {
            this.calls.add("choose");
        }

        @Override
        public void waiting(Player player) {
            this.calls.add("waiting");
        }

        @Override
        public void close(Player player, UUID battleId) {
            this.calls.add("close");
        }

        @Override
        public void shutdown() {
        }
    }

    @Test
    void aClosedBattleLeavesNoTimelineBehind() {
        Recording inner = new Recording();
        PacedUi ui = new PacedUi(null, inner);
        UUID id = UUID.randomUUID();
        Player player = player(id);

        ui.waiting(player);
        assertThat(ui.holds(id)).isTrue();
        ui.close(player, UUID.randomUUID());

        assertThat(inner.calls).containsExactly("waiting", "close");
        assertThat(ui.holds(id)).isFalse();
    }

    @Test
    void aPlayerWhoLeavesIsForgotten() {
        PacedUi ui = new PacedUi(null, new Recording());
        UUID id = UUID.randomUUID();
        Player player = player(id);

        ui.waiting(player);
        ui.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null,
                PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(ui.holds(id)).isFalse();
    }
}
