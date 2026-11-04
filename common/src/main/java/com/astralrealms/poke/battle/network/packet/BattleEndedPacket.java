package com.astralrealms.poke.battle.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Worker to shard: the battle is over and the worker forgot it. Always the last packet of a battle.
 *
 * @param result {@code p1}, {@code p2}... for the winner, {@code tie}, or {@code aborted}
 * @param turns  turns played
 * @param teams  how every side's team left the battle; empty when the battle never started
 * @see BattleUpdatePacket for {@code seq}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleEndedPacket implements Packet {

    private UUID battleId;
    private long seq;
    private String result;
    private String reason;
    private int turns;
    private List<BattleTeamState> teams;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeVarLong(this.seq);
        buffer.writeString(this.result);
        buffer.writeString(this.reason);
        buffer.writeVarInt(this.turns);
        buffer.writeList(this.teams, (message, team) -> team.write(message));
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.seq = buffer.readVarLong();
        this.result = buffer.readString();
        this.reason = buffer.readString();
        this.turns = buffer.readVarInt();
        this.teams = buffer.readList(BattleTeamState::read, ArrayList::new);
    }
}
