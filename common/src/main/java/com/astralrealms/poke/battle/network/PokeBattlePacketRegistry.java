package com.astralrealms.poke.battle.network;

import com.astralrealms.core.packet.PacketRegistry;
import com.astralrealms.poke.battle.network.packet.BattleAttachPacket;
import com.astralrealms.poke.battle.network.packet.BattleChoiceErrorPacket;
import com.astralrealms.poke.battle.network.packet.BattleChoicePacket;
import com.astralrealms.poke.battle.network.packet.BattleEndPacket;
import com.astralrealms.poke.battle.network.packet.BattleEndedPacket;
import com.astralrealms.poke.battle.network.packet.BattleRequestPacket;
import com.astralrealms.poke.battle.network.packet.BattleStartPacket;
import com.astralrealms.poke.battle.network.packet.BattleStartReplyPacket;
import com.astralrealms.poke.battle.network.packet.BattleUpdatePacket;

/**
 * Packet ids of the battle protocol. The Node worker ({@code poke-battle-service}) mirrors this table: never reuse or
 * renumber an id, and change a packet's fields only together with the worker and the contract vectors.
 */
public class PokeBattlePacketRegistry extends PacketRegistry {

    public PokeBattlePacketRegistry() {
        super();

        // Shard -> worker
        registerPacket(0x00, BattleStartPacket::new);
        registerPacket(0x01, BattleStartReplyPacket::new);
        registerPacket(0x02, BattleChoicePacket::new);
        registerPacket(0x03, BattleEndPacket::new);
        registerPacket(0x04, BattleAttachPacket::new);
        // Worker -> shard
        registerPacket(0x05, BattleUpdatePacket::new);
        registerPacket(0x06, BattleRequestPacket::new);
        registerPacket(0x07, BattleChoiceErrorPacket::new);
        registerPacket(0x08, BattleEndedPacket::new);
    }
}
