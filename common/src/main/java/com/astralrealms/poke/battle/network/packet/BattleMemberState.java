package com.astralrealms.poke.battle.network.packet;

import java.util.ArrayList;
import java.util.List;

import com.astralrealms.core.packet.binary.BinaryMessage;

/**
 * The condition of one team member, in the order of the team's moves: what a creature brings into a battle (it is
 * not healed by starting one) and what it leaves with.
 *
 * @param hp     current HP, 0 when fainted
 * @param status non-volatile status id ({@code slp}, {@code psn}...), empty when healthy
 * @param pp     PP left per move
 * @param maxPp  maximum PP per move (PP Ups included): the simulator otherwise assumes every PP Up
 */
public record BattleMemberState(int hp, String status, List<Integer> pp, List<Integer> maxPp) {

    public BattleMemberState {
        pp = List.copyOf(pp);
        maxPp = List.copyOf(maxPp);
    }

    void write(BinaryMessage buffer) {
        buffer.writeVarInt(this.hp);
        buffer.writeString(this.status);
        buffer.writeList(this.pp, BinaryMessage::writeVarInt);
        buffer.writeList(this.maxPp, BinaryMessage::writeVarInt);
    }

    static BattleMemberState read(BinaryMessage buffer) {
        int hp = buffer.readVarInt();
        String status = buffer.readString();
        List<Integer> pp = buffer.readList(BinaryMessage::readVarInt, ArrayList::new);
        List<Integer> maxPp = buffer.readList(BinaryMessage::readVarInt, ArrayList::new);
        return new BattleMemberState(hp, status, pp, maxPp);
    }
}
