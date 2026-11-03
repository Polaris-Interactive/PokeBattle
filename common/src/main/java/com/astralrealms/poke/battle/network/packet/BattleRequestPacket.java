package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Worker to shard: a player has to choose. Answered with {@link BattleChoicePacket} and the same {@code rqid}.
 *
 * @param request the simulator request, as JSON (active moves with PP and targets, the team, forced switches...)
 * @see BattleUpdatePacket for {@code seq}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleRequestPacket implements Packet {

    private UUID battleId;
    private long seq;
    private String side;
    private int rqid;
    private String request;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeVarLong(this.seq);
        buffer.writeString(this.side);
        buffer.writeVarInt(this.rqid);
        buffer.writeString(this.request);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.seq = buffer.readVarLong();
        this.side = buffer.readString();
        this.rqid = buffer.readVarInt();
        this.request = buffer.readString();
    }
}
