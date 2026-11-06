package com.astralrealms.poke.battle.ui.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
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
import com.astralrealms.astralHologram.configuration.CursorSettings;
import com.astralrealms.astralHologram.configuration.HudInput;
import com.astralrealms.astralHologram.configuration.HudKey;
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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;

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

    private static final Set<String> DISPLAYS = Set.of("foe", "ally", "prompt", "timer", "log", "info", "hint",
            "ally-frame", "foe-frame", "log-frame", "ally-status", "foe-status", "turn-icon",
            "ally-hp", "foe-hp", "ally-bar", "foe-bar", "ally-icon", "foe-icon");
    /** Art drawn as items (see {@link HudArt}); every other element is text. */
    private static final Set<String> ITEMS = Set.of("ally-frame", "foe-frame", "log-frame", "ally-status", "foe-status",
            "turn-icon", "ally-icon", "foe-icon");
    private static final String BUTTON = "button-";
    /** The strip behind a list button, drawn in the element of the same number. */
    private static final String FRAME = "frame-";
    private static final String BACK = "back";
    private static final String NEXT = "next";
    private static final double HOVER_GROWTH = 1.08;
    private static final double FLAT = 0.05;

    enum Menu { NONE, MAIN, MOVES, TEAM, BALLS, BAG, TARGETS }

    /**
     * A button: {@code action} null when it cannot be used, {@code info} shown while it is hovered.
     */
    private record Entry(Component label, @Nullable Runnable action, @Nullable Component info, @Nullable Key frame) {

        Entry(Component label, @Nullable Runnable action, @Nullable Component info) {
            this(label, action, info, null);
        }
    }

    private final HudUi ui;
    private final Player player;
    private final UUID battleId;
    private final PokeBattleConfiguration.Hud config;
    private final int turnTimerSeconds;
    private final List<String> buttons;
    private final Deque<Component> log = new ArrayDeque<>();
    private final Map<String, Object> drawn = new HashMap<>();
    private final @Nullable BattleCamera camera;

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
                .onClose((_, reason) -> this.onClosed(reason));

        for (Map.Entry<String, PokeBattleConfiguration.Element> entry : this.config.elements().entrySet()) {
            String id = entry.getKey();
            PokeBattleConfiguration.Element element = entry.getValue();
            boolean clickable = this.buttons.contains(id) || id.equals(BACK) || id.equals(NEXT);
            if (!clickable && !DISPLAYS.contains(id) && !id.startsWith(FRAME)) {
                this.ui.plugin().getSLF4JLogger().warn("Unknown battle HUD element '{}' in config.yml, ignored", id);
                continue;
            }
            double scale = element.scale() > 0 ? element.scale() : 1;
            boolean item = ITEMS.contains(id) || id.startsWith(FRAME);
            // Items are flat art: squashing the model's depth keeps it from reading as a 3D block.
            ComponentDefinition.Builder component = item
                    ? ComponentDefinition.item(id, HudArt.item(HudArt.FRAME_ALLY)).scale(scale, scale, scale * FLAT)
                    : ComponentDefinition.text(id, "")
                            .scale(scale)
                            .seeThrough(true)
                            .transparentBackground(!element.background());
            if (clickable)
                component.interaction(interaction -> interaction
                        .hoverBackground(hover)
                        .hoverScale(scale * HOVER_GROWTH)
                        .condition(_ -> this.usable(id))
                        .disabledBackground(disabled)
                        .onClick(_ -> this.click(id)));
            builder.element(component.build(), element.position().yaw(), element.position().pitch(),
                    element.depth() == null ? this.config.depth() : element.depth());
        }
        return builder.build();
    }

    /**
     * Closes the HUD now (the battle is over, or the player moved on to another).
     */
    void close() {
        this.closed = true;
        this.stopTicker();
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
            while (this.log.size() > Math.max(0, this.config.logLines()))
                this.log.removeFirst();
        }
        this.renderField();
        this.renderLog();
        this.renderTimer();
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
     * The battle is over: no menu, no prompt, until the HUD closes.
     */
    void ended() {
        this.waiting();
        this.set("prompt", List.of());
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
                            () -> this.handler().choose(this.player, view, "run"), null));
            }
            case MOVES -> {
                for (ChoiceView.Move move : view.moves()) {
                    PlaceholderContainer placeholders = new PlaceholderContainer()
                            .registerDirect("move", move.name())
                            .registerDirect("pp", move.pp())
                            .registerDirect("max_pp", move.maxPp())
                            .registerDirect("type", move.type())
                            .registerDirect("category", move.category())
                            .registerDirect("power", move.power() > 0 ? Integer.toString(move.power()) : "—")
                            .registerDirect("accuracy", move.accuracy() == null ? "—" : move.accuracy() + " %");
                    entries.add(new Entry((move.disabled()
                            ? PokeBattleMessages.MOVE_BUTTON_DISABLED
                            : PokeBattleMessages.MOVE_BUTTON).component(placeholders),
                            move.disabled() ? null : () -> this.handler().choose(this.player, view, "move " + move.index()),
                            PokeBattleMessages.MOVE_TOOLTIP.component(placeholders)));
                }
            }
            case TEAM -> {
                for (ChoiceView.Member member : view.switchable())
                    entries.add(new Entry(PokeBattleMessages.MEMBER_BUTTON.component(this.member(member, false)),
                            () -> this.handler().choose(this.player, view, "switch " + member.position()), null));
            }
            case BALLS -> {
                List<BattleBag.Entry> balls = this.handler().bag().balls(this.player);
                if (balls.isEmpty()) {
                    PokeBattleMessages.NO_BALLS.message(this.player);
                    return null;
                }
                for (BattleBag.Entry ball : balls)
                    entries.add(new Entry(this.bagEntry(ball),
                            () -> this.handler().throwBall(this.player, view, ball.itemId()), null));
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
                    }, null));
            }
            case TARGETS -> {
                BattleBag.Entry item = this.item;
                List<ChoiceView.Member> targets = item == null ? List.of() : BattleBag.targets(item, view.team());
                if (item == null || targets.isEmpty()) {
                    PokeBattleMessages.ITEM_NO_TARGET.message(this.player);
                    return null;
                }
                for (ChoiceView.Member member : targets)
                    entries.add(new Entry(PokeBattleMessages.TARGET_BUTTON.component(this.member(member, true)),
                            () -> this.handler().useItem(this.player, view, item.itemId(), member.position()), null));
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
        this.sound(this.config.clickSound());
        switch (id) {
            case BACK -> {
                Menu back = this.back();
                if (back != null)
                    this.show(back);
            }
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

    void hover(String id, boolean entered) {
        if (this.closed)
            return;
        if (entered)
            this.sound(this.config.hoverSound());
        Entry entry = entered ? this.entryAt(id) : null;
        this.set("info", entry == null || entry.info() == null ? List.of() : List.of(entry.info()));
    }

    // ---- Drawing ----

    private void render() {
        this.setItem("ally-frame", HudArt.FRAME_ALLY);
        this.setItem("foe-frame", HudArt.FRAME_FOE);
        this.setItem("log-frame", HudArt.FRAME_LOG);
        this.setItem("turn-icon", HudArt.TURN_ICON);
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
            return;
        }
        this.setItem(id + "-icon", HudArt.speciesItem(creature.species()));
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
    }

    private void renderTimer() {
        if (this.closed)
            return;
        PlaceholderContainer placeholders = new PlaceholderContainer()
                .registerDirect("turn", Math.max(1, this.field.turn()));
        Component line;
        if (this.deadline > 0) {
            long seconds = Math.max(0, (this.deadline - System.currentTimeMillis() + 999) / 1000);
            line = PokeBattleMessages.HUD_TIMER.component(placeholders.registerDirect("seconds", seconds));
        } else {
            line = PokeBattleMessages.HUD_TURN.component(placeholders);
        }
        this.set("timer", List.of(line));
    }

    private void renderMenu() {
        if (this.closed)
            return;
        boolean moving = this.camera != null && this.camera.busy();
        this.set("hint", moving ? List.of(PokeBattleMessages.HUD_SKIP_HINT.component()) : List.of());
        this.visible("hint", moving);
        Menu menu = moving ? Menu.NONE : this.menu;
        ChoiceView view = this.choice;
        this.set("prompt", List.of(switch (menu) {
            case NONE -> PokeBattleMessages.HUD_WAITING.component();
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
            String frame = FRAME + button.substring(BUTTON.length());
            this.setItem(frame, entry == null ? null : entry.frame() != null ? entry.frame() : HudArt.FRAME_LIST);
        }
        this.set(BACK, List.of(PokeBattleMessages.BACK_BUTTON.component()));
        this.visible(BACK, !moving && this.back() != null);
        this.set(NEXT, List.of(PokeBattleMessages.HUD_NEXT_BUTTON.component()));
        this.visible(NEXT, !moving && this.pages() > 1);
        this.set("info", List.of());

        // Disabled looks are decided on a redraw
        HudView hud = this.view;
        if (hud != null)
            hud.refresh();
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

    // ---- Helpers ----

    private ChoiceDialogs.Handler handler() {
        return this.ui.handler();
    }

    private PlaceholderContainer member(ChoiceView.Member member, boolean target) {
        return new PlaceholderContainer()
                .registerDirect("name", member.name())
                .registerDirect("level", member.level())
                .registerDirect("hp", member.hp())
                .registerDirect("max", member.maxHp())
                .registerDirect("status", target && member.fainted()
                        ? PokeBattleMessages.TARGET_FAINTED.component()
                        : this.status(member.status()));
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
