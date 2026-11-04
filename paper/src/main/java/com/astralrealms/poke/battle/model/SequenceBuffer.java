package com.astralrealms.poke.battle.model;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Puts a battle's worker packets back in order. AstralCore handles each delivery on its own virtual thread, so they
 * arrive in any order; the worker numbers them from 0 without gaps. Duplicates (redeliveries) are dropped.
 */
public class SequenceBuffer<T> {

    private final TreeMap<Long, T> waiting = new TreeMap<>();
    private long next;

    /**
     * @return the items now ready, in order: this one and the ones it unblocked, or nothing if an earlier one is
     * still missing
     */
    public synchronized List<T> offer(long seq, T item) {
        if (seq < this.next)
            return List.of();
        this.waiting.putIfAbsent(seq, item);

        List<T> ready = new ArrayList<>();
        while (!this.waiting.isEmpty() && this.waiting.firstKey() == this.next) {
            ready.add(this.waiting.pollFirstEntry().getValue());
            this.next++;
        }
        return ready;
    }

    /**
     * Items received after a gap: if this grows, a packet was lost.
     */
    public synchronized int waiting() {
        return this.waiting.size();
    }
}
