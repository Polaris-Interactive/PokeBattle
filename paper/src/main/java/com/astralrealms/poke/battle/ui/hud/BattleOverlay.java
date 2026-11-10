package com.astralrealms.poke.battle.ui.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.battle.ui.FieldView;
import com.astralrealms.poke.battle.ui.hud.OverlayLine.Anchor;
import com.astralrealms.poke.battle.ui.hud.OverlayLine.Run;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.species.Species;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * The two creatures' cards and the battle log drawn on the screen itself, in its corners, rather than in the world
 * in front of the camera: they keep their place and size whatever the player's FOV, and do not move with the camera.
 * <p>
 * It is one boss bar whose title holds every piece ({@link OverlayLine}); the {@code pokesmp} pack's text shader puts
 * each piece in its corner, and the pack empties the white boss bar's own art. Without that pack the title shows as
 * a row of unknown glyphs at the top of the screen: {@code hud.overlay.enabled} is for servers that ship it.
 * <p>
 * Places inside a card and the log are those of the art ({@code assets/battle-hud}), in its pixels. Player's thread.
 */
final class BattleOverlay {

    // The card (131 x 40): portrait well on the outer edge, name strip, health bar slot; the foe's is mirrored
    private static final int CARD_WIDTH = 131;
    private static final int UNDERLAY_X = 5;
    private static final int UNDERLAY_Y = 8;
    private static final int ICON_X = 6;
    private static final int ICON_Y = 9;
    private static final int NAME_LEFT = 39;
    private static final int NAME_RIGHT = 121;
    private static final int NAME_Y = 8;
    private static final int NAME_GAP = 4;
    private static final int BAR_X = 37;
    private static final int BAR_Y = 22;
    private static final int BAR_WIDTH = 84;
    private static final int HP_Y = 29;
    private static final int STATUS_Y = 39;
    // The log (230 x 82, the builder's LOG_SIZE): a dark panel inside a frame, a scroll gutter on its right
    private static final int LOG_HEIGHT = 82;
    private static final int LOG_TEXT_X = 9;
    private static final int LOG_TEXT_Y = 9;
    private static final int LOG_GUTTER = 18;
    /** Rows of text the log panel holds. */
    static final int LOG_ROWS = 7;
    private static final int ROW_HEIGHT = 9;
    // The turn and its timer, top centre: the turn icon (10 x 10), then the text; the skip hint under them
    private static final int TURN_GAP = 3;
    private static final int TURN_TEXT_Y = 1;
    private static final int HINT_Y = 13;

    private final Player player;
    private final OverlayFont font;
    private final PokeBattleConfiguration.Bar bar;
    private final int margin;
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 0, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    private boolean shown;
    private @Nullable Component drawn;

    BattleOverlay(Player player, OverlayFont font, PokeBattleConfiguration.Bar bar, int margin) {
        this.player = player;
        this.font = font;
        this.bar = bar;
        this.margin = Math.max(0, margin);
    }

    /**
     * Draws the field, the turn line, the hint under it (null for none) and the log's last lines, if they changed.
     */
    void draw(FieldView field, Collection<Component> log, @Nullable Component turn, @Nullable Component hint) {
        OverlayLine line = new OverlayLine(this.font);
        this.card(line, field.ally(), true);
        this.card(line, field.foe(), false);
        this.turn(line, turn, hint);
        this.log(line, log);
        Component title = line.build();
        if (!title.equals(this.drawn)) {
            this.drawn = title;
            this.bossBar.name(title);
        }
        if (!this.shown) {
            this.shown = true;
            this.player.showBossBar(this.bossBar);
        }
    }

    void hide() {
        if (this.shown) {
            this.shown = false;
            this.player.hideBossBar(this.bossBar);
        }
    }

    /**
     * One card: the player's in the top left corner, the opponent's (mirrored art) in the top right one.
     */
    private void card(OverlayLine line, ChoiceView.Creature creature, boolean ally) {
        if (!FieldView.present(creature))
            return;
        Anchor anchor = ally ? Anchor.TOP_LEFT : Anchor.TOP_RIGHT;
        // The card's left edge from its anchor, and a place of the ally's art turned into this card's
        int left = ally ? this.margin : -this.margin - CARD_WIDTH;
        int top = this.margin;
        line.glyph(anchor, left, top, ally ? OverlayGlyph.FRAME_ALLY : OverlayGlyph.FRAME_FOE);
        line.glyph(anchor, left + this.across(ally, UNDERLAY_X, OverlayGlyph.UNDERLAY.width()), top + UNDERLAY_Y,
                OverlayGlyph.UNDERLAY);
        line.species(anchor, left + this.across(ally, ICON_X, OverlayGlyph.SPECIES_WIDTH), top + ICON_Y,
                dexNo(creature.species()));

        // The name on the left of its strip, the level on its right; a long name is cut to leave the level room
        int stripLeft = left + this.across(ally, NAME_LEFT, NAME_RIGHT - NAME_LEFT);
        int stripWidth = NAME_RIGHT - NAME_LEFT;
        List<Run> level = OverlayLine.runs(PokeBattleMessages.HUD_OVERLAY_LEVEL.component(new PlaceholderContainer()
                .registerDirect("level", creature.level())));
        int levelWidth = line.width(level);
        List<Run> name = OverlayLine.clip(this.font, OverlayLine.runs(PokeBattleMessages.HUD_OVERLAY_NAME.component(
                new PlaceholderContainer().registerDirect("name", creature.name()))), stripWidth - levelWidth - NAME_GAP);
        line.text(anchor, stripLeft, top + NAME_Y, name, true);
        line.text(anchor, stripLeft + stripWidth - levelWidth, top + NAME_Y, level, true);

        // The bar: its whole length empty, what is left over it
        int barLeft = left + this.across(ally, BAR_X, BAR_WIDTH);
        double ratio = creature.maxHp() <= 0 ? 0 : Math.clamp((double) creature.hp() / creature.maxHp(), 0, 1);
        int filled = (int) Math.round(ratio * BAR_WIDTH);
        if (creature.hp() > 0 && filled == 0)
            filled = 1;
        line.bar(anchor, barLeft, top + BAR_Y, BAR_WIDTH, OverlayPalette.BAR_EMPTY);
        line.bar(anchor, barLeft, top + BAR_Y, filled, OverlayPalette.index(TextColor.fromHexString(
                ratio > 0.5 ? this.bar.high() : ratio > 0.2 ? this.bar.medium() : this.bar.low())));

        // Under the bar: the HP (exact for the player's creature, a percentage for the opponent's), then the status
        List<Run> hp = OverlayLine.runs((ally ? PokeBattleMessages.HUD_ALLY_HP : PokeBattleMessages.HUD_FOE_HP)
                .component(new PlaceholderContainer()
                        .registerDirect("hp", creature.hp())
                        .registerDirect("max", creature.maxHp())
                        .registerDirect("percent", creature.hp())));
        line.text(anchor, ally ? barLeft + 1 : barLeft + BAR_WIDTH - 1 - line.width(hp), top + HP_Y, hp, true);
        OverlayGlyph status = OverlayGlyph.status(creature.statusId());
        if (status != null)
            line.glyph(anchor, ally ? barLeft + 1 : barLeft + BAR_WIDTH - 1 - status.width(), top + STATUS_Y, status);
    }

    /**
     * Where something {@code width} wide at {@code x} of the ally's card is on this card: the same place, or its
     * mirror on the opponent's.
     */
    private int across(boolean ally, int x, int width) {
        return ally ? x : CARD_WIDTH - x - width;
    }

    /**
     * The turn and its timer in the middle of the top of the screen, behind the turn icon, and the hint under them.
     */
    private void turn(OverlayLine line, @Nullable Component turn, @Nullable Component hint) {
        if (turn != null) {
            List<Run> runs = OverlayLine.runs(turn);
            int left = -(OverlayGlyph.TURN.width() + TURN_GAP + line.width(runs)) / 2;
            line.glyph(Anchor.TOP_CENTRE, left, this.margin, OverlayGlyph.TURN);
            line.text(Anchor.TOP_CENTRE, left + OverlayGlyph.TURN.width() + TURN_GAP, this.margin + TURN_TEXT_Y, runs,
                    true);
        }
        if (hint != null) {
            List<Run> runs = OverlayLine.runs(hint);
            line.text(Anchor.TOP_CENTRE, -line.width(runs) / 2, this.margin + HINT_Y, runs, true);
        }
    }

    /**
     * The log in the bottom right corner: its lines wrapped to the panel, the last rows that fit.
     */
    private void log(OverlayLine line, Collection<Component> log) {
        int left = -this.margin - OverlayGlyph.LOG.width();
        // From the bottom of the screen to the top of the frame
        int top = this.margin + LOG_HEIGHT;
        line.glyph(Anchor.BOTTOM_RIGHT, left, top, OverlayGlyph.LOG);
        List<List<Run>> rows = new ArrayList<>();
        for (Component entry : log)
            rows.addAll(OverlayLine.wrap(this.font, OverlayLine.runs(entry),
                    OverlayGlyph.LOG.width() - LOG_TEXT_X - LOG_GUTTER));
        List<List<Run>> last = rows.subList(Math.max(0, rows.size() - LOG_ROWS), rows.size());
        for (int i = 0; i < last.size(); i++)
            line.text(Anchor.BOTTOM_RIGHT, left + LOG_TEXT_X, top - LOG_TEXT_Y - i * ROW_HEIGHT, last.get(i), false);
    }

    /**
     * The species' dex number (its icon's glyph), 0 when the bundle does not know it.
     */
    private static int dexNo(String species) {
        return PokeCoreAPI.get().species().findSpecies(species).map(Species::dexNo).orElse(0);
    }
}
