package com.astralrealms.poke.battle.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import com.astralrealms.poke.battle.ui.ChoiceView;
import com.astralrealms.poke.core.PokeCoreAPI;
import com.astralrealms.poke.core.api.service.ItemMappingService;
import com.astralrealms.poke.core.mechanics.item.ItemEffect;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * The player's inventory as a battle bag: balls (items PokeCore knows as balls) and items whose blueprint fixes an
 * effect the simulator can apply (heal, revive, cure, PP). Items are taken when the choice leaves this server and
 * given back if the battle refuses it.
 * <p>
 * Every inventory method must run on the player's thread.
 */
public class BattleBag {

    /** The effects the simulator applies in battle; an item with any other effect stays out of the bag. */
    private static final Set<ItemEffect.Type> BATTLE_EFFECTS = Set.of(ItemEffect.Type.HEAL, ItemEffect.Type.REVIVE,
            ItemEffect.Type.CURE_STATUS, ItemEffect.Type.RESTORE_PP);
    private static final double DEFAULT_REVIVE_FRACTION = 0.5;

    /**
     * One kind of item in the bag.
     *
     * @param itemId  battle engine id, as stored and sent
     * @param name    the stack's display name
     * @param amount  how many the player carries
     * @param effects what it does, empty for a ball
     * @param model   the item's own model, the sprite the player sees in the inventory; null without one
     */
    public record Entry(String itemId, Component name, int amount, List<ItemEffect> effects, @Nullable Key model) {

        public Entry {
            effects = List.copyOf(effects);
        }
    }

    /**
     * One item taken for a choice.
     *
     * @param name    the stack's display name, for the log
     * @param effects its battle effects, empty for a ball
     */
    public record Taken(Component name, List<ItemEffect> effects) {

        public Taken {
            effects = List.copyOf(effects);
        }
    }

    public List<Entry> balls(Player player) {
        return this.scan(player, true);
    }

    public List<Entry> medicine(Player player) {
        return this.scan(player, false);
    }

    private List<Entry> scan(Player player, boolean balls) {
        ItemMappingService items = PokeCoreAPI.get().items();
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.isEmpty())
                continue;
            String itemId = items.itemId(stack).orElse(null);
            if (itemId == null)
                continue;
            List<ItemEffect> effects = List.of();
            if (balls) {
                if (PokeCoreAPI.get().capture().ball(itemId).isEmpty())
                    continue;
            } else {
                effects = battleEffects(items.fixedEffects(stack));
                if (effects.isEmpty())
                    continue;
            }
            Entry known = entries.get(itemId);
            entries.put(itemId, new Entry(itemId, stack.effectiveName(),
                    stack.getAmount() + (known == null ? 0 : known.amount()), effects, model(stack)));
        }
        return List.copyOf(entries.values());
    }

    /**
     * Takes one of an item from the player's inventory.
     *
     * @return null if the player no longer carries one
     */
    public @Nullable Taken take(Player player, String itemId) {
        ItemMappingService items = PokeCoreAPI.get().items();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || !items.itemId(stack).map(itemId::equals).orElse(false))
                continue;
            Taken taken = new Taken(stack.effectiveName(), battleEffects(items.fixedEffects(stack)));
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
            return taken;
        }
        return null;
    }

    /**
     * Gives an item back; dropped at the player's feet when the inventory is full.
     *
     * @return false if the item cannot be built any more (blueprint removed)
     */
    public boolean refund(Player player, String itemId) {
        ItemStack stack = PokeCoreAPI.get().items().stack(player, itemId, 1).orElse(null);
        if (stack == null)
            return false;
        player.getInventory()
                .addItem(stack)
                .values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }

    private static @Nullable Key model(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        NamespacedKey model = meta == null ? null : meta.getItemModel();
        return model == null ? null : Key.key(model.getNamespace(), model.getKey());
    }

    static List<ItemEffect> battleEffects(List<ItemEffect> effects) {
        return effects.stream().filter(effect -> BATTLE_EFFECTS.contains(effect.type())).toList();
    }

    /**
     * The effects as the worker reads them: {@code heal[=N]}, {@code cure}, {@code revive=F}, {@code pp[=N]}, joined by
     * {@code +}.
     */
    public static String encode(List<ItemEffect> effects) {
        Set<String> parts = new LinkedHashSet<>();
        for (ItemEffect effect : effects) {
            switch (effect.type()) {
                case HEAL -> {
                    parts.add(effect.amount() == null ? "heal" : "heal=" + effect.amount());
                    if (effect.cureStatus())
                        parts.add("cure");
                }
                case REVIVE -> parts.add("revive=" + number(effect.fraction() == null
                        ? DEFAULT_REVIVE_FRACTION
                        : effect.fraction()));
                case CURE_STATUS -> parts.add("cure");
                case RESTORE_PP -> parts.add(effect.amount() == null ? "pp" : "pp=" + effect.amount());
                default -> {
                }
            }
        }
        return String.join("+", parts);
    }

    /**
     * Whether the item would do something to this team member, as the worker checks it. PP are only known for the
     * active creature, so a PP item is offered for every standing one (the worker refuses it if useless).
     */
    public static boolean applies(List<ItemEffect> effects, ChoiceView.Member member) {
        boolean revives = effects.stream().anyMatch(effect -> effect.type() == ItemEffect.Type.REVIVE);
        for (ItemEffect effect : effects) {
            boolean applies = switch (effect.type()) {
                case REVIVE -> member.fainted();
                case HEAL -> !member.fainted() && (member.hp() < member.maxHp()
                        || (effect.cureStatus() && !member.status().isEmpty()));
                case CURE_STATUS -> !member.fainted() && !member.status().isEmpty();
                case RESTORE_PP -> !member.fainted() || revives;
                default -> false;
            };
            if (applies)
                return true;
        }
        return false;
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * Members the item can be used on, in team order.
     */
    public static List<ChoiceView.Member> targets(Entry entry, List<ChoiceView.Member> team) {
        List<ChoiceView.Member> targets = new ArrayList<>();
        for (ChoiceView.Member member : team)
            if (applies(entry.effects(), member))
                targets.add(member);
        return targets;
    }
}
