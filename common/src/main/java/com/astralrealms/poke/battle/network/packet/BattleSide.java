package com.astralrealms.poke.battle.network.packet;

import java.util.ArrayList;
import java.util.List;
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
 * @param state    the condition of each team member, in team order; empty when the whole team is fresh (full HP and
 *                 PP, no status). Members must not be fainted: leave them out of the team
 */
public record BattleSide(String slot, String name, @Nullable UUID playerId, String team, String ai,
                         List<BattleMemberState> state) {

    public BattleSide {
        state = List.copyOf(state);
    }

    public boolean human() {
        return this.ai.isEmpty();
    }

    void write(BinaryMessage buffer) {
        buffer.writeString(this.slot);
        buffer.writeString(this.name);
        buffer.writeOptional(this.playerId, BinaryMessage::writeUUID);
        buffer.writeString(this.team);
        buffer.writeString(this.ai);
        buffer.writeList(this.state, (message, member) -> member.write(message));
    }

    static BattleSide read(BinaryMessage buffer) {
        String slot = buffer.readString();
        String name = buffer.readString();
        UUID playerId = buffer.readOptional(BinaryMessage::readUUID).orElse(null);
        String team = buffer.readString();
        String ai = buffer.readString();
        List<BattleMemberState> state = buffer.readList(BattleMemberState::read, ArrayList::new);
        return new BattleSide(slot, name, playerId, team, ai, state);
    }
}
