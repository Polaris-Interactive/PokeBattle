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
 * Shard to the shared start queue, with a reply: the worker that takes the battle answers
 * {@link BattleStartReplyPacket}. Sending it again with the same {@code battleId} is safe: a worker that finds the
 * battle already claimed drops it.
 *
 * @param format            PokeLegends format id ({@code polariswild}...), mapped to a simulator format by the worker
 * @param seed              simulator seed, so the battle can be replayed
 * @param turnTimerSeconds  0 for no timer
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleStartPacket implements Packet {

    private UUID battleId;
    private String shardId;
    private String format;
    private String seed;
    private List<BattleSide> sides;
    private int turnTimerSeconds;
    private boolean allowBag;
    private boolean allowCapture;

    @Override
    public void write(BinaryMessage buffer) {
        buffer.writeUUID(this.battleId);
        buffer.writeString(this.shardId);
        buffer.writeString(this.format);
        buffer.writeString(this.seed);
        buffer.writeList(this.sides, (message, side) -> side.write(message));
        buffer.writeVarInt(this.turnTimerSeconds);
        buffer.writeBoolean(this.allowBag);
        buffer.writeBoolean(this.allowCapture);
    }

    @Override
    public void read(BinaryMessage buffer) {
        this.battleId = buffer.readUUID();
        this.shardId = buffer.readString();
        this.format = buffer.readString();
        this.seed = buffer.readString();
        this.sides = buffer.readList(BattleSide::read, ArrayList::new);
        this.turnTimerSeconds = buffer.readVarInt();
        this.allowBag = buffer.readBoolean();
        this.allowCapture = buffer.readBoolean();
    }
}
