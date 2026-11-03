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
 * Worker to shard: simulator protocol lines, as one side sees them.
 * <p>
 * {@code seq} numbers every worker-to-shard packet of a battle (updates, requests, errors, end) from 0, without gaps:
 * AstralCore handles deliveries concurrently, so the shard puts them back in order before rendering.
 *
 * @param side {@code p1}, {@code p2}... for that player's view, empty for the spectator view
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleUpdatePacket implements Packet {

    private UUID battleId;
    private long seq;
    private String side;
    private List<String> lines;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeVarLong(this.seq);
        buffer.writeString(this.side);
        buffer.writeList(this.lines, BinaryMessage::writeString);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.seq = buffer.readVarLong();
        this.side = buffer.readString();
        this.lines = buffer.readList(BinaryMessage::readString, ArrayList::new);
    }
}
