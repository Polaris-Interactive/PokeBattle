package com.astralrealms.poke.battle.network.packet;

import java.util.ArrayList;
import java.util.List;

import com.astralrealms.core.packet.binary.BinaryMessage;

/**
 * How a side's team left the battle, in the order of its start team (the simulator reorders members as they switch).
 *
 * @param slot {@code p1}, {@code p2}...
 */
public record BattleTeamState(String slot, List<BattleMemberState> members) {

    public BattleTeamState {
        members = List.copyOf(members);
    }

    void write(BinaryMessage buffer) {
        buffer.writeString(this.slot);
        buffer.writeList(this.members, (message, member) -> member.write(message));
    }

    static BattleTeamState read(BinaryMessage buffer) {
        String slot = buffer.readString();
        List<BattleMemberState> members = buffer.readList(BattleMemberState::read, ArrayList::new);
        return new BattleTeamState(slot, members);
    }
}
