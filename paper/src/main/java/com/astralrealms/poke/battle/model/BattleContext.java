package com.astralrealms.poke.battle.model;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import com.astralrealms.poke.battle.api.model.StageSpots;
import com.astralrealms.poke.core.model.Pokemon;
import com.google.gson.Gson;

/**
 * What a shard needs to show and settle a battle besides the worker's packets, kept in Redis by the shard that started
 * it so another one can take it over (the player changed server, or the shard crashed). The player's creatures are
 * named by id, in label order: they are read back from the player's storage, which a battle never changes (stand-ins,
 * in no storage, are kept whole). The foes are in label order too (a context written before trainers held whole wild
 * creatures: their species, form and level read the same).
 *
 * @param staging  where the battle was staged (an arena, a gym's marks); null when it was laid in front of the player,
 *                 and in a context written before staged battles were kept
 * @param standIns the player's side whole when it is stand-ins (they are in no storage to read back), in label order;
 *                 null for stored creatures
 */
public record BattleContext(UUID battleId, String format, UUID playerId, List<UUID> allies,
                            List<Battle.Foe> foes, Battle.Options options, @Nullable Staging staging,
                            @Nullable List<Pokemon> standIns) {

    private static final Gson GSON = new Gson();

    public BattleContext {
        allies = List.copyOf(allies);
        foes = List.copyOf(foes);
        standIns = standIns == null ? null : List.copyOf(standIns);
    }

    /**
     * A battle fought by stored creatures.
     */
    public BattleContext(UUID battleId, String format, UUID playerId, List<UUID> allies, List<Battle.Foe> foes,
                         Battle.Options options, @Nullable Staging staging) {
        this(battleId, format, playerId, allies, foes, options, staging, null);
    }

    /**
     * A battle laid in front of the player.
     */
    public BattleContext(UUID battleId, String format, UUID playerId, List<UUID> allies, List<Battle.Foe> foes,
                         Battle.Options options) {
        this(battleId, format, playerId, allies, foes, options, null, null);
    }

    public static BattleContext of(Battle battle, @Nullable Staging staging) {
        return new BattleContext(battle.id(), battle.format(), battle.playerId(),
                battle.allies().stream().map(ally -> ally.pokemon().id()).toList(),
                battle.foes(),
                battle.options(),
                staging,
                battle.options().foughtByStandIns()
                        ? battle.allies().stream().map(Battle.Ally::pokemon).toList()
                        : null);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static BattleContext fromJson(String json) {
        return GSON.fromJson(json, BattleContext.class);
    }

    /**
     * A staged battle's place, its world by name: the shard that takes the battle over lays it there again when it
     * has that world (the same arena server, its instance still open), else in front of the player.
     *
     * @param world the stage's world
     * @param stage feet of the player's side, facing the opponent
     * @param ally  the player's creature; null, with {@code foe}, for the configured offsets from the stage
     * @param foe   the opponent's creature
     */
    public record Staging(String world, Spot stage, @Nullable Spot ally, @Nullable Spot foe) {

        /**
         * The staging of a battle laid at {@code stage}, with its creature spots only when they are in its world (as
         * {@code BattleStages} uses them). Null for a stage with no world.
         */
        public static @Nullable Staging of(Location stage, @Nullable StageSpots spots) {
            World world = stage.getWorld();
            if (world == null)
                return null;
            return of(world.getName(), stage, spots == null || spots.ally().getWorld() != world ? null : spots);
        }

        static Staging of(String world, Location stage, @Nullable StageSpots spots) {
            return new Staging(world, Spot.of(stage),
                    spots == null ? null : Spot.of(spots.ally()),
                    spots == null ? null : Spot.of(spots.foe()));
        }

        public Location stage(@Nullable World world) {
            return this.stage.in(world);
        }

        /**
         * The creature spots in {@code world}, null when the battle had none.
         */
        public @Nullable StageSpots spots(@Nullable World world) {
            if (this.ally == null || this.foe == null)
                return null;
            return new StageSpots(this.ally.in(world), this.foe.in(world));
        }
    }

    /**
     * A location without its world.
     */
    public record Spot(double x, double y, double z, float yaw, float pitch) {

        public static Spot of(Location location) {
            return new Spot(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        public Location in(@Nullable World world) {
            return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
        }
    }
}
