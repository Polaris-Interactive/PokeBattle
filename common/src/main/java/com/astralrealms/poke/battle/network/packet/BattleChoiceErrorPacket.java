package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Worker to shard: a choice was refused (stale {@code rqid}, illegal move...). The request it answered still stands.
 *
 * @param message the simulator's reason, for logs; players get a translated message
 * @see BattleUpdatePacket for {@code seq}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleChoiceErrorPacket implements Packet {

    private UUID battleId;
    private long seq;
    private String side;
    private int rqid;
    private String message;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeVarLong(this.seq);
        buffer.writeString(this.side);
        buffer.writeVarInt(this.rqid);
        buffer.writeString(this.message);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.seq = buffer.readVarLong();
        this.side = buffer.readString();
        this.rqid = buffer.readVarInt();
        this.message = buffer.readString();
    }
}
