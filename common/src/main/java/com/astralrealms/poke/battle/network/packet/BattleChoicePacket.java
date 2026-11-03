package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Shard to worker: a player's answer to a {@link BattleRequestPacket}.
 *
 * @param rqid   the request answered; a choice for an older request is refused
 * @param choice simulator choice ({@code move 2}, {@code switch 3}, {@code item <id> <slot>}...)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleChoicePacket implements Packet {

    private UUID battleId;
    private String side;
    private int rqid;
    private String choice;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeString(this.side);
        buffer.writeVarInt(this.rqid);
        buffer.writeString(this.choice);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.side = buffer.readString();
        this.rqid = buffer.readVarInt();
        this.choice = buffer.readString();
    }
}
