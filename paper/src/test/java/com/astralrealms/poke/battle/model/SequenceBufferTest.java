package com.astralrealms.poke.battle.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SequenceBufferTest {

    @Test
    void releasesInOrderOnceTheGapIsFilled() {
        SequenceBuffer<String> buffer = new SequenceBuffer<>();

        assertThat(buffer.offer(1, "b")).isEmpty();
        assertThat(buffer.offer(2, "c")).isEmpty();
        assertThat(buffer.waiting()).isEqualTo(2);
        assertThat(buffer.offer(0, "a")).containsExactly("a", "b", "c");
        assertThat(buffer.offer(3, "d")).containsExactly("d");
        assertThat(buffer.waiting()).isZero();
    }

    @Test
    void dropsDuplicates() {
        SequenceBuffer<String> buffer = new SequenceBuffer<>();

        assertThat(buffer.offer(0, "a")).containsExactly("a");
        assertThat(buffer.offer(0, "a again")).isEmpty();
        assertThat(buffer.offer(2, "c")).isEmpty();
        assertThat(buffer.offer(2, "c again")).isEmpty();
        assertThat(buffer.offer(1, "b")).containsExactly("b", "c");
    }
}
