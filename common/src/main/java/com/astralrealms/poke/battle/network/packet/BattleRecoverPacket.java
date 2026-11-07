package com.astralrealms.poke.battle.network.packet;

import java.util.UUID;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.binary.BinaryMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Shard to the start queue: the worker running the battle died. The first worker with room takes it over, replays its
 * input log from Redis and answers with a {@link BattleStartReplyPacket} (refused with reason {@code ended} when the
 * battle had already ended: its {@link BattleEndedPacket} waits in Redis). The new worker then resends the battle to
 * {@code shardId} from sequence 0: a resync update, then the pending requests.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BattleRecoverPacket implements Packet {

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
