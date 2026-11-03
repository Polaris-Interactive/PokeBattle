package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Shard to worker: ends a battle from outside the simulation (a capture, a forfeit, an abort).
 *
 * @param result {@code p1}, {@code p2}... for that side's win, {@code tie}, or {@code aborted} (no winner, no rewards)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleEndPacket implements Packet {

    private UUID battleId;
    private String result;
    private String reason;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeString(this.result);
        buffer.writeString(this.reason);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.result = buffer.readString();
        this.reason = buffer.readString();
    }
}
