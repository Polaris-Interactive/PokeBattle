package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Shard to worker: this shard now shows the battle (the player changed server, or the previous shard crashed). The
 * worker sends its later messages there, and the pending requests again.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleAttachPacket implements Packet {

    private UUID battleId;
    private String shardId;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeString(this.shardId);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.shardId = buffer.readString();
    }
}
