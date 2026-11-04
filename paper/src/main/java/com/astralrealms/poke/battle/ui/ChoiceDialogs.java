package com.astralrealms.poke.battle.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.placeholder.container.PlaceholderContainer;
import com.astralrealms.poke.battle.configuration.PokeBattleMessages;
import com.astralrealms.poke.battle.service.BattleBag;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

/**
 * The battle choice dialog (Java players), built in code: its buttons change every turn, which AstralCore's YAML
 * dialogs cannot express. Every text is a {@code messages.yml} entry. Bedrock forms come with the presentation step.
 * <p>
 * Moves, then the team, the balls and the bag (each a sub-dialog with a way back), and fleeing.
 * <p>
 * Buttons answer the request they were built for ({@link ChoiceView#rqid()}); the battle refuses a click for an older
 * one.
 */
public class ChoiceDialogs {

    /**
     * What a click does: the battle checks the choice and sends it, or tells the player why not. Called on the
     * player's thread.
     */
    public interface Handler {

        void choose(Player player, ChoiceView view, String choice);

        void throwBall(Player player, ChoiceView view, String ball);

        void useItem(Player player, ChoiceView view, String item, int position);

        BattleBag bag();
    }

    private final Handler handler;
    private final int moveColumns;

    public ChoiceDialogs(Handler handler, int moveColumns) {
        this.handler = handler;
        this.moveColumns = Math.max(1, moveColumns);
    }

    /**
     * Must run on the player's thread.
     */
    public void open(Player player, ChoiceView view) {
        player.showDialog(view.forceSwitch() ? this.team(view, false) : this.main(view));
    }

    private Dialog main(ChoiceView view) {
        List<ActionButton> buttons = new ArrayList<>();
        for (ChoiceView.Move move : view.moves()) {
            PlaceholderContainer placeholders = new PlaceholderContainer()
                    .registerDirect("move", move.name())
                    .registerDirect("pp", move.pp())
                    .registerDirect("max_pp", move.maxPp())
                    .registerDirect("type", move.type())
                    .registerDirect("category", move.category())
                    .registerDirect("power", move.power() > 0 ? Integer.toString(move.power()) : "—")
                    .registerDirect("accuracy", move.accuracy() == null ? "—" : move.accuracy() + " %");
            Component label = (move.disabled() ? PokeBattleMessages.MOVE_BUTTON_DISABLED : PokeBattleMessages.MOVE_BUTTON)
                    .component(placeholders);
            buttons.add(this.button(label, PokeBattleMessages.MOVE_TOOLTIP.component(placeholders),
                    player -> this.handler.choose(player, view, "move " + move.index())));
        }
        if (!view.switchable().isEmpty() && !view.trapped())
            buttons.add(this.button(PokeBattleMessages.TEAM_BUTTON.component(), null,
                    player -> player.showDialog(this.team(view, true))));
        if (view.actions().capture())
            buttons.add(this.button(PokeBattleMessages.BALL_BUTTON.component(), null,
                    player -> this.openBalls(player, view)));
        if (view.actions().bag())
            buttons.add(this.button(PokeBattleMessages.BAG_BUTTON.component(), null,
                    player -> this.openBag(player, view)));
        if (view.actions().flee())
            buttons.add(this.button(PokeBattleMessages.RUN_BUTTON.component(), null,
                    player -> this.handler.choose(player, view, "run")));

        List<DialogBody> body = List.of(DialogBody.plainMessage(this.field(view), 300));
        DialogBase base = DialogBase.builder(PokeBattleMessages.DIALOG_TITLE.component(new PlaceholderContainer()
                        .registerDirect("turn", Math.max(1, view.turn()))))
                .canCloseWithEscape(true)
                .pause(false)
                .body(body)
                .build();
        return Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(this.moveColumns).build()));
    }

    /**
     * @param back whether the player can go back to the moves (not when a creature fainted)
     */
    private Dialog team(ChoiceView view, boolean back) {
        List<ActionButton> buttons = new ArrayList<>();
        for (ChoiceView.Member member : view.switchable())
            buttons.add(this.button(PokeBattleMessages.MEMBER_BUTTON.component(new PlaceholderContainer()
                            .registerDirect("name", member.name())
                            .registerDirect("level", member.level())
                            .registerDirect("hp", member.hp())
                            .registerDirect("max", member.maxHp())
                            .registerDirect("status", this.status(member.status()))), null,
                    player -> this.handler.choose(player, view, "switch " + member.position())));

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(this.field(view), 300));
        if (view.forceSwitch())
            body.add(DialogBody.plainMessage(PokeBattleMessages.DIALOG_FORCED_SWITCH.component(new PlaceholderContainer()
                    .registerDirect("ally", view.ally().name()))));

        DialogBase base = DialogBase.builder(PokeBattleMessages.TEAM_TITLE.component())
                .canCloseWithEscape(!view.forceSwitch())
                .pause(false)
                .body(body)
                .build();
        MultiActionType.Builder type = DialogType.multiAction(buttons).columns(1);
        if (back)
            type.exitAction(this.button(PokeBattleMessages.BACK_BUTTON.component(), null,
                    player -> player.showDialog(this.main(view))));
        return Dialog.create(factory -> factory.empty().base(base).type(type.build()));
    }

    private void openBalls(Player player, ChoiceView view) {
        List<BattleBag.Entry> balls = this.handler.bag().balls(player);
        if (balls.isEmpty()) {
            PokeBattleMessages.NO_BALLS.message(player);
            player.showDialog(this.main(view));
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (BattleBag.Entry ball : balls)
            buttons.add(this.button(this.entry(ball), null,
                    clicker -> this.handler.throwBall(clicker, view, ball.itemId())));
        player.showDialog(this.list(PokeBattleMessages.BALLS_TITLE.component(), view, buttons,
                clicker -> clicker.showDialog(this.main(view))));
    }

    private void openBag(Player player, ChoiceView view) {
        List<BattleBag.Entry> items = this.handler.bag().medicine(player);
        if (items.isEmpty()) {
            PokeBattleMessages.NO_BATTLE_ITEMS.message(player);
            player.showDialog(this.main(view));
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (BattleBag.Entry item : items)
            buttons.add(this.button(this.entry(item), null, clicker -> this.openTargets(clicker, view, item)));
        player.showDialog(this.list(PokeBattleMessages.BAG_TITLE.component(), view, buttons,
                clicker -> clicker.showDialog(this.main(view))));
    }

    private void openTargets(Player player, ChoiceView view, BattleBag.Entry item) {
        List<ChoiceView.Member> targets = BattleBag.targets(item, view.team());
        if (targets.isEmpty()) {
            PokeBattleMessages.ITEM_NO_TARGET.message(player);
            this.openBag(player, view);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (ChoiceView.Member member : targets)
            buttons.add(this.button(PokeBattleMessages.TARGET_BUTTON.component(new PlaceholderContainer()
                            .registerDirect("name", member.name())
                            .registerDirect("level", member.level())
                            .registerDirect("hp", member.hp())
                            .registerDirect("max", member.maxHp())
                            .registerDirect("status", member.fainted()
                                    ? PokeBattleMessages.TARGET_FAINTED.component()
                                    : this.status(member.status()))), null,
                    clicker -> this.handler.useItem(clicker, view, item.itemId(), member.position())));
        player.showDialog(this.list(PokeBattleMessages.TARGET_TITLE.component(new PlaceholderContainer()
                .registerDirect("item", item.name())), view, buttons, clicker -> this.openBag(clicker, view)));
    }

    private Component entry(BattleBag.Entry entry) {
        return PokeBattleMessages.BAG_ENTRY_BUTTON.component(new PlaceholderContainer()
                .registerDirect("item", entry.name())
                .registerDirect("amount", entry.amount()));
    }

    /**
     * A one-column list with the field on top and a way back.
     */
    private Dialog list(Component title, ChoiceView view, List<ActionButton> buttons, Consumer<Player> back) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .body(List.of(DialogBody.plainMessage(this.field(view), 300)))
                .build();
        MultiActionType type = DialogType.multiAction(buttons)
                .columns(1)
                .exitAction(this.button(PokeBattleMessages.BACK_BUTTON.component(), null, back))
                .build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private Component field(ChoiceView view) {
        return PokeBattleMessages.DIALOG_FIELD.component(new PlaceholderContainer()
                .registerDirect("ally", view.ally().name())
                .registerDirect("ally_level", view.ally().level())
                .registerDirect("ally_hp", view.ally().hp())
                .registerDirect("ally_max", view.ally().maxHp())
                .registerDirect("ally_status", this.status(view.ally().status()))
                .registerDirect("foe", view.foe().name())
                .registerDirect("foe_level", view.foe().level())
                .registerDirect("foe_percent", view.foe().hp())
                .registerDirect("foe_status", this.status(view.foe().status())));
    }

    private Object status(String status) {
        if (status.isEmpty())
            return "";
        return PokeBattleMessages.DIALOG_STATUS.component(new PlaceholderContainer().registerDirect("status", status));
    }

    private ActionButton button(Component label, @Nullable Component tooltip, Consumer<Player> click) {
        ActionButton.Builder builder = ActionButton.builder(label)
                .action(DialogAction.customClick((_, audience) -> {
                    if (audience instanceof Player player)
                        click.accept(player);
                }, ClickCallback.Options.builder()
                        .uses(1)
                        .lifetime(ClickCallback.DEFAULT_LIFETIME)
                        .build()));
        if (tooltip != null)
            builder.tooltip(tooltip);
        return builder.build();
    }
}
