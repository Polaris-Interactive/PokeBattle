package com.astralrealms.poke.battle.model;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.core.PokeCoreAPI;

import lombok.experimental.UtilityClass;

/**
 * Display text from the species bundle, in the viewer's language: {@code <kind>.<id>.name}. Battle lines name moves,
 * items and abilities by display name; their simulator id is the bundle's id.
 * <p>
 * Kinds used by battles: {@code species}, {@code move}, {@code type}, {@code status}, {@code stat}, {@code ability},
 * {@code item}, {@code condition} (weather, hazards, volatile effects), {@code category}.
 */
@UtilityClass
public class Names {

    public static String text(@Nullable Locale locale, String kind, @Nullable String id) {
        if (id == null || id.isEmpty())
            return "";
        return PokeCoreAPI.get().species().text(locale, kind + "." + id + ".name");
    }

    /**
     * The short description of an entity ({@code <kind>.<id>.desc}), empty when the bundle has none.
     */
    public static String description(@Nullable Locale locale, String kind, @Nullable String id) {
        if (id == null || id.isEmpty())
            return "";
        String key = kind + "." + id + ".desc";
        String text = PokeCoreAPI.get().species().text(locale, key);
        return text.equals(key) ? "" : text;
    }

    public static String ally(@Nullable Locale locale, Battle.Ally ally) {
        String nickname = ally.pokemon().nickname();
        if (nickname != null && !nickname.isBlank())
            return nickname;
        return text(locale, "species", ally.pokemon().species());
    }

    public static String foe(@Nullable Locale locale, Battle.Foe foe) {
        if (foe.nickname() != null && !foe.nickname().isBlank())
            return foe.nickname();
        return text(locale, "species", foe.species());
    }
}
