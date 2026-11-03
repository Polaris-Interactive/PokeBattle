package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.astralrealms.core.packet.binary.BinaryMessage;

/**
 * One side of a battle in a {@link BattleStartPacket}.
 *
 * @param slot     simulator side id: {@code p1}, {@code p2} (and {@code p3}, {@code p4} for multi battles)
 * @param playerId the player on this side, null for a creature or an NPC
 * @param team     the team in the simulator's packed format (PokeCore's {@code PackedTeam})
 * @param ai       the AI that plays this side ({@code wild}, {@code trainer}...), empty when a player chooses
 */
public record BattleSide(String slot, String name, @Nullable UUID playerId, String team, String ai) {

    public boolean human() {
        return this.ai.isEmpty();
    }

    void write(BinaryMessage buffer) {
        buffer.writeString(this.slot);
        buffer.writeString(this.name);
        buffer.writeOptional(this.playerId, BinaryMessage::writeUUID);
        buffer.writeString(this.team);
        buffer.writeString(this.ai);
    }

    static BattleSide read(BinaryMessage buffer) {
        String slot = buffer.readString();
        String name = buffer.readString();
        UUID playerId = buffer.readOptional(BinaryMessage::readUUID).orElse(null);
        String team = buffer.readString();
        String ai = buffer.readString();
        return new BattleSide(slot, name, playerId, team, ai);
    }
}
