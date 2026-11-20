package com.astralrealms.poke.battle.model;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.core.PokeCoreAPI;

import lombok.experimental.UtilityClass;

/**
 * Display text from the species bundle, always in the server's language (French), never the player's client
 * language: {@code <kind>.<id>.name}. Battle lines name moves, items and abilities by their English display name;
 * their simulator id is the bundle's id, so every name goes through here.
 * <p>
 * Kinds used by battles: {@code species}, {@code move}, {@code type}, {@code status}, {@code stat}, {@code ability},
 * {@code item}, {@code condition} (weather, hazards, volatile effects), {@code category}.
 */
@UtilityClass
public class Names {

    public static String text(String kind, @Nullable String id) {
        if (id == null || id.isEmpty())
            return "";
        return PokeCoreAPI.get().species().text(kind + "." + id + ".name");
    }

    /**
     * The short description of an entity ({@code <kind>.<id>.desc}), empty when the bundle has none.
     */
    public static String description(String kind, @Nullable String id) {
        if (id == null || id.isEmpty())
            return "";
        String key = kind + "." + id + ".desc";
        String text = PokeCoreAPI.get().species().text(key);
        return text.equals(key) ? "" : text;
    }

    public static String ally(Battle.Ally ally) {
        String nickname = ally.pokemon().nickname();
        if (nickname != null && !nickname.isBlank())
            return nickname;
        return text("species", ally.pokemon().species());
    }

    public static String foe(Battle.Foe foe) {
        if (foe.nickname() != null && !foe.nickname().isBlank())
            return foe.nickname();
        return text("species", foe.species());
    }
}
