package com.astralrealms.poke.battle.render;

import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.model.Battle;
import com.astralrealms.poke.battle.model.Names;
import com.astralrealms.poke.battle.protocol.Condition;
import com.astralrealms.poke.battle.protocol.PokemonRef;
import com.astralrealms.poke.battle.protocol.ProtocolLine;

import net.kyori.adventure.text.Component;

/**
 * Turns the player's protocol lines into the French battle log ({@code log-*} messages). Lines it does not know are
 * left out: the log shows what matters, the HUD (later) shows the rest.
 */
public class BattleLogRenderer {

    private static final Set<String> STATUSES = Set.of("brn", "par", "slp", "frz", "psn", "tox");

    private final Battle battle;
    private final @Nullable Locale locale;

    public BattleLogRenderer(Battle battle, @Nullable Locale locale) {
        this.battle = battle;
        this.locale = locale;
    }

    public @Nullable Component render(ProtocolLine line) {
        if (line.has("silent"))
            return null;
        return switch (line.type()) {
            case "switch", "drag" -> this.switchIn(line);
            case "turn" -> PokeBattleMessages.LOG_TURN.component(this.placeholders().registerDirect("turn", line.arg(0)));
            case "move" -> PokeBattleMessages.LOG_MOVE.component(this.creature(line.arg(0))
                    .registerDirect("move", Names.text(this.locale, "move", ProtocolLine.toId(line.arg(1)))));
            case "-damage" -> this.damage(line);
            case "-heal" -> line.has("from")
                    ? PokeBattleMessages.LOG_HEAL.component(this.creature(line.arg(0)))
                    : this.hp(line.arg(0), line.arg(1));
            case "faint" -> PokeBattleMessages.LOG_FAINT.component(this.creature(line.arg(0)));
            case "-supereffective" -> PokeBattleMessages.LOG_SUPER_EFFECTIVE.component();
            case "-resisted" -> PokeBattleMessages.LOG_RESISTED.component();
            case "-immune" -> PokeBattleMessages.LOG_IMMUNE.component(this.creature(line.arg(0)));
            case "-crit" -> PokeBattleMessages.LOG_CRIT.component();
            case "-miss" -> PokeBattleMessages.LOG_MISS.component(this.creature(line.arg(1).isEmpty()
                    ? line.arg(0)
                    : line.arg(1)));
            case "-fail" -> PokeBattleMessages.LOG_FAIL.component();
            case "-status" -> PokeBattleMessages.LOG_STATUS.component(this.creature(line.arg(0))
                    .registerDirect("status", Names.text(this.locale, "status", line.arg(1))));
            case "-curestatus" -> PokeBattleMessages.LOG_CURE_STATUS.component(this.creature(line.arg(0))
                    .registerDirect("status", Names.text(this.locale, "status", line.arg(1))));
            case "-boost", "-unboost" -> (line.type().equals("-boost")
                    ? PokeBattleMessages.LOG_BOOST
                    : PokeBattleMessages.LOG_UNBOOST)
                    .component(this.creature(line.arg(0))
                            .registerDirect("stat", Names.text(this.locale, "stat", line.arg(1)))
                            .registerDirect("amount", line.arg(2)));
            case "cant" -> PokeBattleMessages.LOG_CANT.component(this.creature(line.arg(0))
                    .registerDirect("reason", this.effect(line.arg(1))));
            default -> null;
        };
    }

    private @Nullable Component switchIn(ProtocolLine line) {
        PokemonRef ref = PokemonRef.parse(line.arg(0));
        if (ref == null)
            return null;
        PlaceholderContainer placeholders = this.creature(line.arg(0));
        return ref.side().equals(Battle.PLAYER_SIDE)
                ? PokeBattleMessages.LOG_SEND_OUT.component(placeholders)
                : PokeBattleMessages.LOG_WILD_APPEARS.component(placeholders);
    }

    private @Nullable Component damage(ProtocolLine line) {
        String from = line.tag("from");
        if (from == null)
            return this.hp(line.arg(0), line.arg(1));
        return PokeBattleMessages.LOG_DAMAGE_FROM.component(this.creature(line.arg(0))
                .registerDirect("effect", this.effect(from)));
    }

    /**
     * HP after a hit: exact for the player's creatures, a percentage for the opponent's.
     */
    private @Nullable Component hp(String creature, String value) {
        PokemonRef ref = PokemonRef.parse(creature);
        Condition condition = Condition.parse(value);
        if (ref == null || condition == null || condition.fainted())
            return null;
        PlaceholderContainer placeholders = this.creature(creature)
                .registerDirect("hp", condition.hp())
                .registerDirect("max", condition.maxHp())
                .registerDirect("percent", condition.percent());
        return ref.side().equals(Battle.PLAYER_SIDE)
                ? PokeBattleMessages.LOG_HP_ALLY.component(placeholders)
                : PokeBattleMessages.LOG_HP_FOE.component(placeholders);
    }

    /**
     * {@code %pokemon%} (as the log names it) and {@code %name%} (plain).
     */
    private PlaceholderContainer creature(String value) {
        PokemonRef ref = PokemonRef.parse(value);
        String name = ref == null ? "" : this.name(ref);
        Object pokemon = name;
        if (ref != null && !ref.side().equals(Battle.PLAYER_SIDE))
            pokemon = PokeBattleMessages.FOE_NAME_WILD.component(new PlaceholderContainer().registerDirect("name", name));
        return this.placeholders()
                .registerDirect("name", name)
                .registerDirect("pokemon", pokemon);
    }

    private String name(PokemonRef ref) {
        if (ref.side().equals(Battle.PLAYER_SIDE))
            return this.battle.ally(ref.label()).map(ally -> Names.ally(this.locale, ally)).orElse(ref.label());
        return this.battle.foe(ref.label()).map(foe -> Names.foe(this.locale, foe)).orElse(ref.label());
    }

    /**
     * A {@code [from]} effect or a {@code cant} reason: a status, an item, an ability, a move, or a condition.
     */
    private String effect(String value) {
        ProtocolLine.Effect effect = ProtocolLine.effect(value);
        if (effect.kind().isEmpty())
            return Names.text(this.locale, STATUSES.contains(effect.id()) ? "status" : "condition", effect.id());
        return Names.text(this.locale, effect.kind(), effect.id());
    }

    private PlaceholderContainer placeholders() {
        return new PlaceholderContainer();
    }
}
