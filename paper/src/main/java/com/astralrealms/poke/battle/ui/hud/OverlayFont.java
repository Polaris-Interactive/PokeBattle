package com.astralrealms.poke.battle.ui.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * How wide text is in the game's default font, in GUI pixels: the overlay places text itself, so it has to know.
 * Read from {@code overlay/default-font.widths} (written from the client's own font by
 * {@code pokeapi-importer/tools/build_battle_overlay.py}). A glyph the file lacks (drawn from Unifont) counts as
 * {@link #UNKNOWN}.
 */
final class OverlayFont {

    private static final String RESOURCE = "/overlay/default-font.widths";
    private static final int UNKNOWN = 6;

    private final Map<Integer, Integer> advances;

    OverlayFont(Map<Integer, Integer> advances) {
        this.advances = Map.copyOf(advances);
    }

    /**
     * @throws IllegalStateException if the resource is missing or unreadable
     */
    static OverlayFont load() {
        Map<Integer, Integer> advances = new HashMap<>();
        try (InputStream stream = OverlayFont.class.getResourceAsStream(RESOURCE)) {
            if (stream == null)
                throw new IllegalStateException("Missing " + RESOURCE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank() || line.startsWith("#"))
                    continue;
                String[] parts = line.trim().split(" ");
                advances.put(Integer.parseInt(parts[0], 16), Integer.parseInt(parts[1]));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unreadable " + RESOURCE, e);
        }
        return new OverlayFont(advances);
    }

    /**
     * @param bold a bold glyph is one pixel wider
     */
    int width(String text, boolean bold) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            width += this.advances.getOrDefault(codePoint, UNKNOWN) + (bold ? 1 : 0);
            i += Character.charCount(codePoint);
        }
        return width;
    }
}
