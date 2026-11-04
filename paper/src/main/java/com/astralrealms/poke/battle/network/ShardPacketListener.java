package com.astralrealms.poke.battle.network;

import com.astralrealms.core.packet.listeners.PacketListener;
import com.astralrealms.core.packet.Packet;
import com.astralrealms.poke.battle.PokeBattle;
import com.astralrealms.poke.battle.service.BattleServiceImpl;

/**
 * Worker packets for the battles this server shows ({@code pokebattle.shard.<serverId>}). Never throws: a throwing
 * handler gets the delivery again, and the battle would see a packet twice.
 */
public class ShardPacketListener implements PacketListener {

    private final PokeBattle plugin;
    private final BattleServiceImpl battles;

    public ShardPacketListener(PokeBattle plugin, BattleServiceImpl battles) {
        this.plugin = plugin;
        this.battles = battles;
    }

    @Override
    public void handle(Packet packet) {
        try {
            this.battles.receive(packet);
        } catch (RuntimeException e) {
            this.plugin.getSLF4JLogger().error("Handling {} failed", packet.getClass().getSimpleName(), e);
        }
    }
}
