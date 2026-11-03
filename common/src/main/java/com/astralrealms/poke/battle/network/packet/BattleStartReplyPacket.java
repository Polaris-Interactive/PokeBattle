package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Worker to shard, the reply to {@link BattleStartPacket}.
 *
 * @param workerId the worker running the battle; send its later messages to {@code pokebattle.worker.<workerId>}
 * @param reason   why it was refused (unknown format, invalid team...), empty when accepted
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleStartReplyPacket implements Packet {

    private UUID battleId;
    private boolean accepted;
    private String workerId;
    private String reason;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeBoolean(this.accepted);
        buffer.writeString(this.workerId);
        buffer.writeString(this.reason);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.accepted = buffer.readBoolean();
        this.workerId = buffer.readString();
        this.reason = buffer.readString();
    }
}
