package com.astralrealms.poke.battle.ui.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.astralHologram.AstralHologramAPI;
import com.astralrealms.astralHologram.api.hologram.ComponentDefinition;
import com.astralrealms.astralHologram.api.hud.HudDefinition;
import com.astralrealms.astralHologram.api.hud.HudOpenOptions;
import com.astralrealms.astralHologram.api.hud.HudView;
import com.astralrealms.astralHologram.configuration.BillboardType;
import com.astralrealms.astralHologram.configuration.CursorSettings;
import com.astralrealms.astralHologram.configuration.HudInput;
import com.astralrealms.astralHologram.configuration.HudKey;
import com.astralrealms.astralHologram.configuration.TextAlignment;
import com.astralrealms.astralHologram.hud.HudCloseReason;
import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.configuration.PokeBattleConfiguration;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.service.BattleBag;
import com.astralrealms.poke.battle.ui.BattleBeat;
import com.astralrealms.poke.battle.ui.BattleStage;
import com.astralrealms.poke.battle.ui.BattleUi;
import com.astralrealms.poke.battle.ui.ChoiceDialogs;
import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.battle.ui.FieldView;
import com.astralrealms.poke.core.mechanics.item.ItemEffect;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;

/**
 * One player's battle HUD: the two creatures, the turn and its timer, the last log lines, and the menu (actions,
 * moves, team, balls, bag, targets) on a column of buttons, paged when a list is longer than the column.
 * <p>
 * Texts are set from code on the HUD's elements, so nothing is resolved by AstralHologram itself. Everything runs on
 * the player's thread; the HUD is drawn once AstralHologram has opened it, and what happened before is drawn then.
 * <p>
 * With a {@link BattleCamera}, the menu waits for the camera to come back to rest after the turn's shots.
 */
final class BattleHud {

    /** The back button's art, behind its label. */
    private static final String BACK_FRAME = "back-frame";
    private static final Set<String> DISPLAYS = Set.of("foe", "ally", "prompt", "timer", "log", "info", "hint",
            "ally-frame", "foe-frame", "log-frame", "ally-status", "foe-status", "turn-icon",
            "ally-hp", "foe-hp", "ally-bar", "foe-bar", "ally-icon", "foe-icon", "info-frame",
            "ally-underlay", "foe-underlay", BACK_FRAME);
    /** Art drawn as items (see {@link HudArt}); every other element is text. */
    private static final Set<String> ITEMS = Set.of("ally-frame", "foe-frame", "log-frame", "ally-status", "foe-status",
            "turn-icon", "ally-icon", "foe-icon", "info-frame", "ally-underlay", "foe-underlay", BACK_FRAME);
    /** Text read line by line rather than as a label: its lines line up on the left. */
    private static final Set<String> LEFT_ALIGNED = Set.of("info", "log");
    private static final String BUTTON = "button-";
    /** The strip behind a list button, drawn in the element of the same number. */
    private static final String FRAME = "frame-";
    /** The creature on a team or target button, at the left end of its strip (laid out from the strip). */
    private static final String ICON = "icon-";
    /** An item's width per unit of scale, in blocks (textures padded square). */
    private static final double ITEM_WIDTH = 0.95;
    /** The share of the list strip's texture its strip takes across (90 of 92 pixels). */
    private static final double STRIP_WIDTH = 90.0 / 92;
    private static final double LIST_ICON = 0.22;
    private static final String BACK = "back";
    private static final String NEXT = "next";
    private static final double HOVER_GROWTH = 1.08;
    /** The player's creature card: hovering it shows the details of its team member. */
    private static final String ALLY_CARD = "ally-frame";
    /** The opponent's card: hovering it shows what the battle knows of its creature. */
    private static final String FOE_CARD = "foe-frame";
    /** The details text: the tooltip of a hovered creature, move, item or ability. */
    private static final String INFO = "info";
    /** The share of max HP a revive gives back when the item does not say (ItemEffect's default). */
    private static final double DEFAULT_REVIVE = 0.5;
    private static final double FLAT = 0.05;
    /** What the screen overlay draws instead, when it is on: these elements are then left out of the HUD. */
    private static final Set<String> OVERLAID = Set.of("foe", "ally", "log", "ally-frame", "foe-frame", "log-frame",
            "ally-status", "foe-status", "ally-hp", "foe-hp", "ally-bar", "foe-bar", "ally-icon", "foe-icon",
            "ally-underlay", "foe-underlay", "timer", "turn-icon", "hint");

    enum Menu { NONE, MAIN, MOVES, TEAM, BALLS, BAG, TARGETS }

    /**
     * A button: {@code action} null when it cannot be used, {@code info} shown while it is hovered.
     */
    private record Entry(Component label, @Nullable Runnable action, @Nullable Component info, @Nullable Key frame,
                         @Nullable Key icon) {

        Entry(Component label, @Nullable Runnable action, @Nullable Component info, @Nullable Key frame) {
            this(label, action, info, frame, null);
        }

        Entry(Component label, @Nullable Runnable action, @Nullable Component info) {
            this(label, action, info, null, null);
        }
    }

    private final HudUi ui;
    private final Player player;
    private final UUID battleId;
    private final PokeBattleConfiguration.Hud config;
    private final int turnTimerSeconds;
    private final List<String> buttons;
    /** Where each button's label sits in the camera's frame ({@code x} right, {@code y} up, {@code z} away), in blocks. */
    private final Map<String, double[]> labels = new HashMap<>();
    /** How far right a label moves to clear the icon of its button. */
    private final Map<String, Double> shifts = new HashMap<>();
    private final Deque<Component> log = new ArrayDeque<>();
    private final Map<String, Object> drawn = new HashMap<>();
    private final @Nullable BattleCamera camera;
    /** The cards and the log on the screen itself; null when they are HUD elements. */
    private final @Nullable BattleOverlay overlay;

    private @Nullable HudView view;
    private @Nullable ScheduledTask ticker;
    private boolean closed;

    private FieldView field;
    private @Nullable ChoiceView choice;
    private Menu menu = Menu.NONE;
    private List<Entry> entries = List.of();
    private int page;
    private BattleBag.@Nullable Entry item;
    private long deadline;
    /** The team as the last choice told it: the details of the player's creature outlive the choice. */
    private List<ChoiceView.Member> team = List.of();
    /** The turn line and the skip hint as last drawn, for the screen overlay. */
    private @Nullable Component turnLine;
    private @Nullable Component hint;

    /**
     * @param stage null for a still camera
     */
    BattleHud(HudUi ui, Player player, FieldView field, PokeBattleConfiguration.Hud config, int turnTimerSeconds,
              @Nullable BattleStage stage) {
        this.ui = ui;
        this.player = player;
        this.battleId = field.battleId();
        this.field = field;
        this.config = config;
        this.turnTimerSeconds = turnTimerSeconds;
        this.buttons = config.elements()
                .keySet()
                .stream()
                .filter(id -> id.startsWith(BUTTON) && number(id) > 0)
                .sorted(Comparator.comparingInt(BattleHud::number))
                .toList();
        this.camera = stage == null ? null : new BattleCamera(ui.plugin(), player, stage, this::renderMenu);
        PokeBattleConfiguration.Overlay overlay = config.overlay();
        OverlayFont font = overlay == null || !overlay.enabled() ? null : ui.overlayFont();
        this.overlay = font == null ? null : new BattleOverlay(player, font, config.bar(), overlay.margin());
    }

    Player player() {
        return this.player;
    }

    UUID battleId() {
        return this.battleId;
    }

    boolean closed() {
        return this.closed;
    }

    // ---- Lifecycle ----

    void open() {
        Location eye = this.player.getEyeLocation();
        eye.setPitch(0);
        AstralHologramAPI.huds()
                .open(this.player, this.definition(), HudOpenOptions.builder().at(eye).build())
                .whenComplete((view, error) -> {
                    if (error != null) {
                        this.closed = true;
                        this.ui.failed(this, error);
                        return;
                    }
                    if (this.closed) {
                        view.close(HudCloseReason.API);
                        return;
                    }
                    this.view = view;
                    this.ticker = this.player.getScheduler()
                            .runAtFixedRate(this.ui.plugin(), _ -> this.renderTimer(), null, 20L, 20L);
                    this.render();
                    if (this.camera != null)
                        this.camera.attach(view);
                });
    }

    private HudDefinition definition() {
        int hover = HealthBar.argb(this.config.hoverBackground());
        int disabled = HealthBar.argb(this.config.disabledBackground());
        PokeBattleConfiguration.Cursor cursor = this.config.cursor();
        HudDefinition.Builder builder = HudDefinition.builder(HudUi.HUD_ID)
                .input(HudInput.MOUSE)
                .cursor(new CursorSettings(cursor.text(), cursor.scale(), this.config.depth() * 0.9,
                        (float) cursor.range().yaw(), (float) cursor.range().pitch(), cursor.sensitivity(),
                        cursor.smoothing()))
                .closeable(this.config.closeable())
                .returnToOrigin(true)
                .depth(this.config.depth())
                .onKey(HudKey.SWAP_HANDS, _ -> this.skip())
                .onKey(HudKey.DROP, _ -> this.goBack())
                .onClose((_, reason) -> this.onClosed(reason));

        for (Map.Entry<String, PokeBattleConfiguration.Element> entry : this.config.elements().entrySet()) {
            String id = entry.getKey();
            PokeBattleConfiguration.Element element = entry.getValue();
            if (this.overlay != null && OVERLAID.contains(id))
                continue;
            boolean clickable = this.buttons.contains(id) || id.equals(BACK) || id.equals(NEXT);
            if (!clickable && !DISPLAYS.contains(id) && !id.startsWith(FRAME)) {
                this.ui.plugin().getSLF4JLogger().warn("Unknown battle HUD element '{}' in config.yml, ignored", id);
                continue;
            }
            double scale = element.scale() > 0 ? element.scale() : 1;
            boolean item = ITEMS.contains(id) || id.startsWith(FRAME);
            // Items are flat art: squashing the model's depth keeps it from reading as a 3D block.
            ComponentDefinition.Builder component = item
                    ? ComponentDefinition.item(id, HudArt.item(HudArt.FRAME_ALLY))
                            .scale(scale, scale, scale * FLAT)
                            // Billboarded, a flat item shows its mirrored back; fixed, the HUD turns its front to the eye
                            .billboard(BillboardType.FIXED)
                    : ComponentDefinition.text(id, "")
                            .scale(scale)
                            .seeThrough(true)
                            .transparentBackground(!element.background())
                            .alignment(LEFT_ALIGNED.contains(id) ? TextAlignment.LEFT : TextAlignment.CENTER);
            if (clickable)
                component.interaction(interaction -> {
                    interaction
                            .hoverBackground(hover)
                            .hoverScale(scale * HOVER_GROWTH)
                            .condition(_ -> this.usable(id))
                            .disabledBackground(disabled)
                            .onClick(_ -> this.click(id));
                    // The back button has no label to size its hitbox from: it covers its art
                    if (id.equals(BACK))
                        interaction.size(scale * ITEM_WIDTH, scale * ITEM_WIDTH);
                });
            else if (id.equals(ALLY_CARD) || id.equals(FOE_CARD))
                component.interaction(interaction -> interaction.hoverScale(scale));
            if (id.equals(INFO))
                component.background(HealthBar.argb(this.config.tooltipBackground()));
            double depth = element.depth() == null ? this.config.depth() : element.depth();
            builder.element(component.build(), element.position().yaw(), element.position().pitch(), depth);
            if (this.buttons.contains(id))
                this.labels.put(id, offset(element.position(), depth));
            if (id.startsWith(FRAME))
                this.icon(builder, id.substring(FRAME.length()), element, scale, depth);
        }
        return builder.build();
    }

    /**
     * The icon slot of a list button: square, as tall as the strip allows, at the strip's left end and just in front of
     * it. Its label moves right by half the icon when one shows.
     */
    private void icon(HudDefinition.Builder builder, String number, PokeBattleConfiguration.Element frame,
                      double frameScale, double depth) {
        double share = this.config.listIcon() > 0 ? this.config.listIcon() : LIST_ICON;
        double scale = frameScale * share;
        double width = scale * ITEM_WIDTH;
        double strip = frameScale * ITEM_WIDTH * STRIP_WIDTH;
        double[] at = offset(frame.position(), depth);
        builder.elementAt(ComponentDefinition.item(ICON + number, HudArt.item(HudArt.FRAME_LIST))
                        .scale(scale, scale, scale * FLAT)
                        .billboard(BillboardType.FIXED)
                        .build(),
                at[0] - strip / 2 + width / 2, at[1], at[2] - 0.01);
        this.shifts.put(BUTTON + number, width / 2);
    }

    /**
     * An element's place in the camera's frame, as AstralHologram turns {@code yaw}/{@code pitch} and depth into one.
     */
    private static double[] offset(PokeBattleConfiguration.Position position, double depth) {
        double yaw = Math.toRadians(position.yaw());
        double pitch = Math.toRadians(position.pitch());
        return new double[]{depth * Math.cos(pitch) * Math.sin(yaw), -depth * Math.sin(pitch),
                depth * Math.cos(pitch) * Math.cos(yaw)};
    }

    /**
     * Closes the HUD now (the battle is over, or the player moved on to another).
     */
    void close() {
        this.closed = true;
        this.stopTicker();
        if (this.overlay != null)
            this.overlay.hide();
        if (this.camera != null)
            this.camera.stop();
        HudView view = this.view;
        this.view = null;
        if (view != null && view.open())
            view.close(HudCloseReason.API);
    }

    private void onClosed(HudCloseReason reason) {
        boolean ours = this.closed;
        this.closed = true;
        this.stopTicker();
        if (this.overlay != null)
            this.overlay.hide();
        if (this.camera != null)
            this.camera.stop();
        this.view = null;
        if (!ours)
            this.ui.closed(this, reason);
    }

    private void stopTicker() {
        if (this.ticker != null) {
            this.ticker.cancel();
            this.ticker = null;
        }
    }

    // ---- What the battle says ----

    BattleUi.Pace pace(BattleBeat beat) {
        return this.camera == null ? BattleUi.Pace.NONE : this.camera.pace(beat.cue());
    }

    /**
     * A beat starts: the camera heads for its shot (the menu hides while it is away).
     */
    void cue(BattleBeat beat) {
        if (this.camera == null)
            return;
        this.camera.cue(beat.cue());
        this.renderMenu();
    }

    /**
     * The beat shows: its field (cards, HP, bars) and its log lines.
     */
    void update(BattleBeat beat) {
        this.field = beat.field();
        for (Component line : beat.log()) {
            this.log.addLast(line);
            // The screen overlay's panel holds more rows than the HUD's
            int kept = this.overlay == null ? this.config.logLines()
                    : Math.max(this.config.logLines(), BattleOverlay.LOG_ROWS);
            while (this.log.size() > Math.max(0, kept))
                this.log.removeFirst();
        }
        this.renderField();
        this.renderLog();
        this.renderTimer();
        // A creature that just came in or left may not be the size the shot was framed for
        if (this.camera != null)
            this.camera.reframe();
    }

    /**
     * Every beat has played: the camera goes back to {@code idle}, where the menu shows.
     */
    void idle() {
        if (this.camera != null)
            this.camera.rest();
    }

    /**
     * F: what is still to play shows at once.
     */
    private void skip() {
        if (!this.closed)
            this.ui.plugin().ui().skip(this.player);
    }

    void choose(ChoiceView view) {
        this.choice = view;
        this.team = view.team();
        this.field = FieldView.of(view);
        this.deadline = this.turnTimerSeconds > 0 ? view.askedAt() + this.turnTimerSeconds * 1000L : 0;
        this.renderField();
        this.renderTimer();
        this.show(view.forceSwitch() ? Menu.TEAM : Menu.MAIN);
    }

    void waiting() {
        this.choice = null;
        this.deadline = 0;
        this.renderTimer();
        this.show(Menu.NONE);
    }

    /**
     * The battle is over: no menu until the HUD closes.
     */
    void ended() {
        this.waiting();
    }

    /**
     * The choice still to make, if any: a HUD that could not open hands it to the dialog.
     */
    @Nullable ChoiceView pending() {
        return this.choice;
    }

    // ---- Menu ----

    private void show(Menu menu) {
        ChoiceView view = this.choice;
        List<Entry> entries = view == null ? List.of() : this.entries(menu, view);
        if (entries == null)
            return;
        this.menu = view == null ? Menu.NONE : menu;
        this.entries = entries;
        this.page = 0;
        this.renderMenu();
    }

    /**
     * The buttons of a menu, or null to stay where the player is (nothing to show, they were told why).
     */
    private @Nullable List<Entry> entries(Menu menu, ChoiceView view) {
        List<Entry> entries = new ArrayList<>();
        switch (menu) {
            case NONE -> {
            }
            case MAIN -> {
                entries.add(new Entry(PokeBattleMessages.HUD_FIGHT_LABEL.component(), () -> this.show(Menu.MOVES),
                        null, HudArt.FRAME_FIGHT));
                if (!view.switchable().isEmpty() && !view.trapped())
                    entries.add(new Entry(PokeBattleMessages.HUD_TEAM_LABEL.component(), () -> this.show(Menu.TEAM),
                            null, HudArt.FRAME_SWITCH));
                if (view.actions().capture())
                    entries.add(new Entry(PokeBattleMessages.HUD_BALL_BUTTON.component(), () -> this.show(Menu.BALLS),
                            null));
                if (view.actions().bag())
                    entries.add(new Entry(PokeBattleMessages.HUD_BAG_LABEL.component(), () -> this.show(Menu.BAG),
                            null, HudArt.FRAME_BAG));
                if (view.actions().flee())
                    entries.add(new Entry(PokeBattleMessages.HUD_RUN_LABEL.component(),
                            () -> this.handler().choose(this.player, view, "run"), null, HudArt.FRAME_RUN));
            }
            case MOVES -> {
                for (ChoiceView.Move move : view.moves()) {
                    PlaceholderContainer placeholders = new PlaceholderContainer()
                            .registerDirect("move", move.name())
                            .registerDirect("pp", move.pp())
                            .registerDirect("maxpp", move.maxPp())
                            .registerDirect("type", move.type())
                            .registerDirect("category", move.category())
                            .registerDirect("power", move.power() > 0 ? Integer.toString(move.power()) : "—")
                            .registerDirect("accuracy", move.accuracy() == null ? "—" : move.accuracy() + " %")
                            .registerDirect("icon", HudArt.typeIcon(move.typeId()));
                    // A move that cannot be used keeps its PP plain: its button is greyed as a whole
                    Component label = move.disabled()
                            ? PokeBattleMessages.HUD_MOVE_BUTTON_DISABLED.component(placeholders)
                            : PokeBattleMessages.HUD_MOVE_BUTTON.component(placeholders
                                    .registerDirect("pp", pp(move.pp(), move.maxPp())));
                    entries.add(new Entry(label,
                            move.disabled() ? null : () -> this.handler().choose(this.player, view, "move " + move.index()),
                            PokeBattleMessages.HUD_TOOLTIP_MOVE.component(placeholders
                                    .registerDirect("pp", pp(move.pp(), move.maxPp())))));
                }
            }
            case TEAM -> {
                // The whole team, so the player sees who is left; only those who can be sent in can be clicked
                for (ChoiceView.Member member : view.team())
                    entries.add(new Entry(PokeBattleMessages.HUD_MEMBER_BUTTON.component(this.member(member, true)),
                            view.switchable().contains(member)
                                    ? () -> this.handler().choose(this.player, view, "switch " + member.position())
                                    : null,
                            this.memberTooltip(member), null, HudArt.speciesItem(member.species())));
            }
            case BALLS -> {
                List<BattleBag.Entry> balls = this.handler().bag().balls(this.player);
                if (balls.isEmpty()) {
                    PokeBattleMessages.NO_BALLS.message(this.player);
                    return null;
                }
                for (BattleBag.Entry ball : balls)
                    entries.add(new Entry(this.bagEntry(ball),
                            () -> this.handler().throwBall(this.player, view, ball.itemId()),
                            this.itemTooltip(ball), null, ball.model()));
            }
            case BAG -> {
                List<BattleBag.Entry> items = this.handler().bag().medicine(this.player);
                if (items.isEmpty()) {
                    PokeBattleMessages.NO_BATTLE_ITEMS.message(this.player);
                    return null;
                }
                for (BattleBag.Entry item : items)
                    entries.add(new Entry(this.bagEntry(item), () -> {
                        this.item = item;
                        this.show(Menu.TARGETS);
                    }, this.itemTooltip(item), null, item.model()));
            }
            case TARGETS -> {
                BattleBag.Entry item = this.item;
                List<ChoiceView.Member> targets = item == null ? List.of() : BattleBag.targets(item, view.team());
                if (item == null || targets.isEmpty()) {
                    PokeBattleMessages.ITEM_NO_TARGET.message(this.player);
                    return null;
                }
                for (ChoiceView.Member member : targets)
                    entries.add(new Entry(PokeBattleMessages.HUD_TARGET_BUTTON.component(this.member(member, false)),
                            () -> this.handler().useItem(this.player, view, item.itemId(), member.position()),
                            this.memberTooltip(member), null, HudArt.speciesItem(member.species())));
            }
        }
        return entries;
    }

    /**
     * Where {@code back} goes, or null when there is no way back (the main menu, a forced switch).
     */
    private @Nullable Menu back() {
        ChoiceView view = this.choice;
        return switch (this.menu) {
            case NONE, MAIN -> null;
            case TEAM -> view != null && view.forceSwitch() ? null : Menu.MAIN;
            case MOVES, BALLS, BAG -> Menu.MAIN;
            case TARGETS -> Menu.BAG;
        };
    }

    private int pages() {
        return this.buttons.isEmpty() ? 1 : Math.max(1, (this.entries.size() + this.buttons.size() - 1) / this.buttons.size());
    }

    private @Nullable Entry entryAt(String button) {
        int slot = this.buttons.indexOf(button);
        int index = this.page * this.buttons.size() + slot;
        return slot < 0 || index >= this.entries.size() ? null : this.entries.get(index);
    }

    private boolean usable(String id) {
        if (id.equals(BACK) || id.equals(NEXT))
            return true;
        Entry entry = this.entryAt(id);
        return entry != null && entry.action() != null;
    }

    private void click(String id) {
        if (this.closed || this.choice == null || this.camera != null && this.camera.busy())
            return;
        if (id.equals(BACK)) {
            this.goBack();
            return;
        }
        this.sound(this.config.clickSound());
        switch (id) {
            case NEXT -> {
                this.page = (this.page + 1) % this.pages();
                this.renderMenu();
            }
            default -> {
                Entry entry = this.entryAt(id);
                if (entry != null && entry.action() != null)
                    entry.action().run();
            }
        }
    }

    /**
     * The back button, or the drop key: one menu up, when there is one.
     */
    private void goBack() {
        Menu back = this.back();
        if (this.closed || this.choice == null || back == null || this.camera != null && this.camera.busy())
            return;
        this.sound(this.config.clickSound());
        this.show(back);
    }

    void hover(String id, boolean entered) {
        if (this.closed)
            return;
        if (entered)
            this.sound(this.config.hoverSound());
        this.focus(id, entered);
    }

    /**
     * Draws what hovering {@code id} shows (its details, its art's second state), or takes it away.
     */
    private void focus(String id, boolean entered) {
        boolean moving = this.camera != null && this.camera.busy();
        // The menu is hidden while the camera is away: nothing of it to tell
        Entry entry = entered && !moving ? this.entryAt(id) : null;
        Component info = entry == null ? null : entry.info();
        if (entered && id.equals(ALLY_CARD))
            info = this.allyTooltip();
        if (entered && id.equals(FOE_CARD))
            info = this.foeTooltip();
        this.tooltip(info);

        // A usable button lights up: its art's second state
        Entry under = this.entryAt(id);
        if (under != null && under.action() != null && !moving && this.buttons.contains(id)) {
            Key art = this.art(under);
            this.setItem(FRAME + id.substring(BUTTON.length()), entered ? HudArt.hover(art) : art);
        }

        // The back button's art lights up the same way
        if (id.equals(BACK) && !moving && this.back() != null)
            this.setItem(BACK_FRAME, entered ? HudArt.hover(HudArt.BACK) : HudArt.BACK);
    }

    private Key art(Entry entry) {
        return entry.frame() != null ? entry.frame() : HudArt.FRAME_LIST;
    }

    // ---- Drawing ----

    private void render() {
        this.setItem("ally-frame", HudArt.FRAME_ALLY);
        this.setItem("foe-frame", HudArt.FRAME_FOE);
        this.setItem("log-frame", HudArt.FRAME_LOG);
        this.setItem("turn-icon", HudArt.TURN_ICON);
        this.setItem("info-frame", null);
        this.renderField();
        this.renderLog();
        this.renderTimer();
        this.renderMenu();
    }

    private void renderField() {
        ChoiceView.Creature foe = this.field.foe();
        this.card("foe", foe, PokeBattleMessages.HUD_FOE_NAME, PokeBattleMessages.HUD_FOE_HP);
        ChoiceView.Creature ally = this.field.ally();
        this.card("ally", ally, PokeBattleMessages.HUD_ALLY_NAME, PokeBattleMessages.HUD_ALLY_HP);
        this.setItem("ally-status", this.strip(ally));
        this.setItem("foe-status", this.strip(foe));
        this.renderOverlay();
    }

    /**
     * The cards and the log on the screen, when they are drawn there. Like the HUD's elements, only once it is open.
     */
    private void renderOverlay() {
        if (this.overlay != null && this.view != null && !this.closed)
            this.overlay.draw(this.field, this.log, this.turnLine, this.hint);
    }

    /**
     * One card: its name and level, its HP (the percentage for the opponent) and its bar, each in its own element.
     */
    private void card(String id, ChoiceView.Creature creature, PokeBattleMessages name, PokeBattleMessages hp) {
        if (!FieldView.present(creature)) {
            this.set(id, List.of());
            this.set(id + "-hp", List.of());
            this.set(id + "-bar", List.of());
            this.setItem(id + "-icon", null);
            this.setItem(id + "-underlay", null);
            return;
        }
        this.setItem(id + "-icon", HudArt.speciesItem(creature.species()));
        this.setItem(id + "-underlay", HudArt.ICON_UNDERLAY);
        this.set(id, List.of(name.component(new PlaceholderContainer()
                .registerDirect("name", creature.name())
                .registerDirect("level", creature.level())
                .registerDirect("status", this.cardStatus(creature)))));
        this.set(id + "-hp", List.of(hp.component(new PlaceholderContainer()
                .registerDirect("hp", creature.hp())
                .registerDirect("max", creature.maxHp())
                .registerDirect("percent", creature.hp()))));
        this.set(id + "-bar", List.of(HealthBar.render(creature.hp(), creature.maxHp(), this.config.bar())));
    }

    /**
     * The status as text, unless the card draws its strip instead.
     */
    private Object cardStatus(ChoiceView.Creature creature) {
        return this.strip(creature) == null ? this.status(creature.status()) : "";
    }

    /**
     * The status strip of a card, or null when it has none (or nobody is on the card).
     */
    private @Nullable Key strip(ChoiceView.Creature creature) {
        return FieldView.present(creature) ? HudArt.statusItem(creature.statusId()) : null;
    }

    private void renderLog() {
        this.set("log", List.copyOf(this.log));
        this.renderOverlay();
    }

    private void renderTimer() {
        if (this.closed)
            return;
        PlaceholderContainer placeholders = new PlaceholderContainer()
                .registerDirect("turn", Math.max(1, this.field.turn()));
        Component line;
        if (this.deadline > 0) {
            long seconds = Math.max(0, (this.deadline - System.currentTimeMillis() + 999) / 1000);
            line = (seconds <= this.config.timerWarning()
                    ? PokeBattleMessages.HUD_TIMER_URGENT
                    : PokeBattleMessages.HUD_TIMER).component(placeholders.registerDirect("seconds", seconds));
        } else {
            line = PokeBattleMessages.HUD_TURN.component(placeholders);
        }
        this.set("timer", List.of(line));
        this.turnLine = line;
        this.renderOverlay();
    }

    private void renderMenu() {
        if (this.closed)
            return;
        boolean moving = this.camera != null && this.camera.busy();
        this.set("hint", moving ? List.of(PokeBattleMessages.HUD_SKIP_HINT.component()) : List.of());
        this.visible("hint", moving);
        this.hint = moving ? PokeBattleMessages.HUD_SKIP_HINT.component() : null;
        this.renderOverlay();
        Menu menu = moving ? Menu.NONE : this.menu;
        ChoiceView view = this.choice;
        // The prompt titles a menu: nothing is said above the buttons while there is none (the turn plays, the
        // opponent chooses, the battle is over)
        this.set("prompt", menu == Menu.NONE ? List.of() : List.of(switch (menu) {
            case NONE -> Component.empty();
            case MAIN -> PokeBattleMessages.HUD_PROMPT.component(new PlaceholderContainer()
                    .registerDirect("ally", this.field.ally().name()));
            case MOVES -> PokeBattleMessages.HUD_MOVES_TITLE.component();
            case TEAM -> view != null && view.forceSwitch()
                    ? PokeBattleMessages.DIALOG_FORCED_SWITCH.component(new PlaceholderContainer()
                    .registerDirect("ally", view.ally().name()))
                    : PokeBattleMessages.TEAM_TITLE.component();
            case BALLS -> PokeBattleMessages.BALLS_TITLE.component();
            case BAG -> PokeBattleMessages.BAG_TITLE.component();
            case TARGETS -> PokeBattleMessages.TARGET_TITLE.component(new PlaceholderContainer()
                    .registerDirect("item", this.item == null ? "" : this.item.name()));
        }));

        for (String button : this.buttons) {
            Entry entry = moving ? null : this.entryAt(button);
            if (entry != null)
                this.set(button, List.of(entry.label()));
            this.visible(button, entry != null);
            // Action buttons are their own art; list buttons (moves, team, bag) get a strip behind the label.
            String number = button.substring(BUTTON.length());
            this.setItem(FRAME + number, entry == null ? null : this.art(entry));
            Key icon = entry == null ? null : entry.icon();
            this.setItem(ICON + number, icon);
            this.shift(button, icon != null);
        }
        // Icon only: the button's art says what it does (ChoiceDialogs keeps the text for the dialog fallback)
        this.set(BACK, List.of());
        this.visible(BACK, !moving && this.back() != null);
        this.setItem(BACK_FRAME, HudArt.BACK);
        this.visible(BACK_FRAME, !moving && this.back() != null);
        this.set(NEXT, List.of(PokeBattleMessages.HUD_NEXT_BUTTON.component(new PlaceholderContainer()
                .registerDirect("page", this.page + 1)
                .registerDirect("pages", this.pages()))));
        this.visible(NEXT, !moving && this.pages() > 1);
        this.tooltip(null);

        // Disabled looks are decided on a redraw
        HudView hud = this.view;
        if (hud == null)
            return;
        hud.refresh();
        // The cursor may rest on what was just drawn: no hover event comes for an element it never left
        hud.hovered().ifPresent(id -> this.focus(id, true));
    }

    /**
     * Sets an element's text, if it changed. Nothing before the HUD is open: {@link #render()} draws it all then.
     */
    private void set(String id, List<Component> lines) {
        HudView view = this.view;
        if (view == null || Objects.equals(this.drawn.get(id), lines))
            return;
        this.drawn.put(id, lines);
        view.element(id).ifPresent(element -> element.text(lines));
    }

    /**
     * Sets an item element's model, or hides it for null. Like {@link #set}, nothing is sent when nothing changed.
     */
    private void setItem(String id, @Nullable Key model) {
        HudView view = this.view;
        // Elements start visible, so the first call must send the hide too: an empty cache is not "nothing to do".
        if (view == null || this.drawn.containsKey(id) && Objects.equals(this.drawn.get(id), model))
            return;
        this.drawn.put(id, model);
        view.element(id).ifPresent(element -> {
            if (model == null) {
                element.visible(false);
            } else {
                element.item(HudArt.item(model));
                element.visible(true);
            }
        });
    }

    private void visible(String id, boolean visible) {
        HudView view = this.view;
        String key = id + "#visible";
        if (view == null || Objects.equals(this.drawn.get(key), visible))
            return;
        this.drawn.put(key, visible);
        view.element(id).ifPresent(element -> element.visible(visible));
    }

    /**
     * Moves a button's label right of its icon, or back where it was laid out.
     */
    private void shift(String button, boolean shifted) {
        HudView view = this.view;
        double[] at = this.labels.get(button);
        String key = button + "#shift";
        if (view == null || at == null || Objects.equals(this.drawn.get(key), shifted))
            return;
        this.drawn.put(key, shifted);
        double dx = shifted ? this.shifts.getOrDefault(button, 0.0) : 0;
        view.element(button).ifPresent(element -> element.offset(at[0] + dx, at[1], at[2]));
    }

    /**
     * The tooltip on its panel, or neither.
     */
    private void tooltip(@Nullable Component info) {
        // The text carries its own background, sized to what it says; a fixed art frame would clip longer details
        this.set("info", info == null ? List.of() : List.of(info));
        this.setItem("info-frame", null);
    }

    /**
     * The details of the player's creature on its card: its team member's tooltip, or null when it is not known.
     */
    private @Nullable Component allyTooltip() {
        ChoiceView.Creature ally = this.field.ally();
        if (!FieldView.present(ally))
            return null;
        // The team is as old as the last choice: after a switch, its active member is no longer the one on the card
        return this.team.stream()
                .filter(member -> member.active() && member.name().equals(ally.name()))
                .findFirst()
                .map(this::memberTooltip)
                .orElse(null);
    }

    /**
     * The opponent's creature on its card: its name, level, HP, status; null when there is no opponent.
     */
    private @Nullable Component foeTooltip() {
        ChoiceView.Creature foe = this.field.foe();
        if (!FieldView.present(foe))
            return null;
        return PokeBattleMessages.HUD_TOOLTIP_FOE.component(new PlaceholderContainer()
                .registerDirect("name", foe.name())
                .registerDirect("level", foe.level())
                .registerDirect("percent", foe.hp())
                .registerDirect("status", foe.status().isEmpty() ? "—" : foe.status()));
    }

    /**
     * A team member's moves with their type and PP, then its ability and Speed; null when nothing is known of it.
     */
    private @Nullable Component memberTooltip(ChoiceView.Member member) {
        if (member.moves().isEmpty())
            return null;
        List<Component> lines = new ArrayList<>();
        for (ChoiceView.MemberMove move : member.moves())
            lines.add(PokeBattleMessages.HUD_TOOLTIP_MEMBER_MOVE.component(new PlaceholderContainer()
                    .registerDirect("icon", HudArt.typeIcon(move.typeId()))
                    .registerDirect("move", move.name())
                    .registerDirect("pp", pp(move.pp(), move.maxPp()))));
        return PokeBattleMessages.HUD_TOOLTIP_MEMBER.component(new PlaceholderContainer()
                .registerDirect("moves", Component.join(JoinConfiguration.newlines(), lines))
                .registerDirect("ability", member.ability().isEmpty() ? "—" : member.ability())
                .registerDirect("details", this.abilityDetails(member))
                .registerDirect("speed", member.speed() > 0 ? Integer.toString(member.speed()) : "—"));
    }

    /**
     * The ability's description under its name, or nothing when the bundle has none.
     */
    private Component abilityDetails(ChoiceView.Member member) {
        if (member.abilityDescription().isEmpty())
            return Component.empty();
        return PokeBattleMessages.HUD_TOOLTIP_ABILITY.component(new PlaceholderContainer()
                .registerDirect("description", member.abilityDescription()));
    }

    /**
     * A bag item or ball: its name and amount, then what it does (a ball only catches).
     */
    private Component itemTooltip(BattleBag.Entry entry) {
        List<Component> lines = new ArrayList<>();
        for (ItemEffect effect : entry.effects())
            lines.addAll(effectLines(effect));
        if (entry.effects().isEmpty())
            lines.add(PokeBattleMessages.HUD_EFFECT_BALL.component());
        return PokeBattleMessages.HUD_TOOLTIP_ITEM.component(new PlaceholderContainer()
                .registerDirect("name", entry.name())
                .registerDirect("amount", entry.amount())
                .registerDirect("effects", Component.join(JoinConfiguration.newlines(), lines)));
    }

    /**
     * The lines saying what one battle effect does; a heal that also cures gets a line for the cure.
     */
    private static List<Component> effectLines(ItemEffect effect) {
        return switch (effect.type()) {
            case HEAL -> {
                List<Component> lines = new ArrayList<>();
                lines.add(effect.amount() == null
                        ? PokeBattleMessages.HUD_EFFECT_HEAL_ALL.component()
                        : PokeBattleMessages.HUD_EFFECT_HEAL.component(new PlaceholderContainer()
                        .registerDirect("amount", effect.amount())));
                if (effect.cureStatus())
                    lines.add(PokeBattleMessages.HUD_EFFECT_CURE.component());
                yield lines;
            }
            case REVIVE -> List.of(PokeBattleMessages.HUD_EFFECT_REVIVE.component(new PlaceholderContainer()
                    .registerDirect("percent", Math.round((effect.fraction() == null ? DEFAULT_REVIVE : effect.fraction()) * 100) + " %")));
            case CURE_STATUS -> List.of(PokeBattleMessages.HUD_EFFECT_CURE.component());
            case RESTORE_PP -> List.of(effect.amount() == null
                    ? PokeBattleMessages.HUD_EFFECT_PP_ALL.component()
                    : PokeBattleMessages.HUD_EFFECT_PP.component(new PlaceholderContainer()
                    .registerDirect("amount", effect.amount())));
            default -> List.of();
        };
    }

    /**
     * PP left over the most, green while more than half is left, then yellow, then red under a quarter.
     */
    private static Component pp(int pp, int max) {
        double ratio = max <= 0 ? 1 : (double) pp / max;
        String colour = ratio > 0.5 ? "#5BE37D" : ratio > 0.25 ? "#F2C94C" : "#EB5757";
        return Component.text(pp + "/" + max, TextColor.fromHexString(colour));
    }

    // ---- Helpers ----

    private ChoiceDialogs.Handler handler() {
        return this.ui.handler();
    }

    /**
     * @param team on the team menu, the creature on the field says so in place of its status
     */
    private PlaceholderContainer member(ChoiceView.Member member, boolean team) {
        return new PlaceholderContainer()
                .registerDirect("name", member.name())
                .registerDirect("level", member.level())
                .registerDirect("hp", HealthBar.points(member.hp(), member.maxHp(), this.config.bar()))
                .registerDirect("max", member.maxHp())
                .registerDirect("status", member.fainted()
                        ? PokeBattleMessages.TARGET_FAINTED.component()
                        : team && member.active()
                        ? PokeBattleMessages.HUD_MEMBER_ACTIVE.component()
                        : this.shortStatus(member.status()));
    }

    /**
     * A status cut to its first three letters, so it fits a button.
     */
    private Object shortStatus(String status) {
        if (status.isEmpty())
            return "";
        String cut = status.substring(0, Math.min(3, status.length())).toUpperCase(Locale.ROOT);
        return PokeBattleMessages.HUD_MEMBER_STATUS.component(new PlaceholderContainer().registerDirect("status", cut));
    }

    private Component bagEntry(BattleBag.Entry entry) {
        return PokeBattleMessages.BAG_ENTRY_BUTTON.component(new PlaceholderContainer()
                .registerDirect("item", entry.name())
                .registerDirect("amount", entry.amount()));
    }

    private Object status(String status) {
        if (status.isEmpty())
            return "";
        return PokeBattleMessages.DIALOG_STATUS.component(new PlaceholderContainer().registerDirect("status", status));
    }

    private void sound(String key) {
        if (key == null || key.isBlank())
            return;
        try {
            this.player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, 1f));
        } catch (IllegalArgumentException e) {
            this.ui.plugin().getSLF4JLogger().warn("Invalid battle HUD sound '{}'", key);
        }
    }

    private static int number(String id) {
        try {
            return Integer.parseInt(id.substring(BUTTON.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
