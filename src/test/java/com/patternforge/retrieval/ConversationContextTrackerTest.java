package com.patternforge.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConversationContextTracker.
 */
class ConversationContextTrackerTest {

    private ConversationContextTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ConversationContextTracker();
    }

    // ── getSentPatternIds ──────────────────────────────────────────────────────

    @Test
    void getSentPatternIds_returnsEmptySetForUnknownConversation() {
        Set<String> ids = tracker.getSentPatternIds("unknown-conv");
        assertThat(ids).isEmpty();
    }

    @Test
    void getSentPatternIds_returnsIdsAfterUpdate() {
        tracker.update("conv-1", Set.of("id-a", "id-b"), null);

        Set<String> ids = tracker.getSentPatternIds("conv-1");
        assertThat(ids).containsExactlyInAnyOrder("id-a", "id-b");
    }

    @Test
    void getSentPatternIds_returnsUnmodifiableView() {
        tracker.update("conv-1", Set.of("id-a"), null);

        Set<String> ids = tracker.getSentPatternIds("conv-1");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> ids.add("should-fail"));
    }

    // ── getLastTaskEmbedding ───────────────────────────────────────────────────

    @Test
    void getLastTaskEmbedding_returnsEmptyForUnknownConversation() {
        Optional<float[]> emb = tracker.getLastTaskEmbedding("unknown-conv");
        assertThat(emb).isEmpty();
    }

    @Test
    void getLastTaskEmbedding_returnsEmptyWhenNullEmbeddingStored() {
        tracker.update("conv-1", Set.of("id-a"), null);

        Optional<float[]> emb = tracker.getLastTaskEmbedding("conv-1");
        assertThat(emb).isEmpty();
    }

    @Test
    void getLastTaskEmbedding_returnsEmbeddingAfterUpdate() {
        float[] embedding = {0.1f, 0.2f, 0.3f};
        tracker.update("conv-1", Set.of(), embedding);

        Optional<float[]> result = tracker.getLastTaskEmbedding("conv-1");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(embedding);
    }

    // ── update accumulation ────────────────────────────────────────────────────

    @Test
    void update_accumulatesIdsAcrossMultipleCalls() {
        tracker.update("conv-1", Set.of("id-a", "id-b"), null);
        tracker.update("conv-1", Set.of("id-c"), null);

        Set<String> ids = tracker.getSentPatternIds("conv-1");
        assertThat(ids).containsExactlyInAnyOrder("id-a", "id-b", "id-c");
    }

    @Test
    void update_replacesEmbeddingOnSubsequentCall() {
        float[] first  = {0.1f, 0.2f};
        float[] second = {0.9f, 0.8f};

        tracker.update("conv-1", Set.of("id-a"), first);
        tracker.update("conv-1", Set.of("id-b"), second);

        Optional<float[]> result = tracker.getLastTaskEmbedding("conv-1");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(second);
    }

    @Test
    void update_isolatesStateAcrossConversations() {
        tracker.update("conv-1", Set.of("id-a"), null);
        tracker.update("conv-2", Set.of("id-b"), null);

        assertThat(tracker.getSentPatternIds("conv-1")).containsOnly("id-a");
        assertThat(tracker.getSentPatternIds("conv-2")).containsOnly("id-b");
    }

    // ── eviction ──────────────────────────────────────────────────────────────

    @Test
    void evictStaleConversations_removesEntriesOlderThanOneHour() throws Exception {
        tracker.update("fresh-conv", Set.of("id-fresh"), null);
        tracker.update("stale-conv", Set.of("id-stale"), null);

        // Manually backdating the stale entry via reflection
        backdateConversation("stale-conv", 65);

        tracker.evictStaleConversations();

        assertThat(tracker.getSentPatternIds("stale-conv")).isEmpty();
        assertThat(tracker.getSentPatternIds("fresh-conv")).containsOnly("id-fresh");
    }

    @Test
    void evictStaleConversations_keepsEntriesYoungerThanOneHour() throws Exception {
        tracker.update("recent-conv", Set.of("id-x"), null);

        // Backdate by only 30 minutes — should survive eviction
        backdateConversation("recent-conv", 30);

        tracker.evictStaleConversations();

        assertThat(tracker.getSentPatternIds("recent-conv")).containsOnly("id-x");
    }

    // ── thread safety ──────────────────────────────────────────────────────────

    @Test
    void update_isSafeUnderConcurrentAccess() throws InterruptedException {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String id = "pattern-" + i;
            executor.submit(() -> {
                try {
                    tracker.update("shared-conv", Set.of(id), new float[]{0.1f});
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errors).isEmpty();
        assertThat(tracker.getSentPatternIds("shared-conv")).hasSize(threads);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Backdates a stored ConversationState via reflection for eviction tests.
     */
    @SuppressWarnings("unchecked")
    private void backdateConversation(String conversationId, long minutesAgo) throws Exception {
        Field statesField = ConversationContextTracker.class.getDeclaredField("states");
        statesField.setAccessible(true);
        java.util.Map<String, ?> states = (java.util.Map<String, ?>) statesField.get(tracker);
        Object existing = states.get(conversationId);
        if (existing == null) return;

        // ConversationState is a private record; recreate it via its canonical constructor
        Class<?> stateClass = existing.getClass();
        Field sentField = stateClass.getDeclaredField("sentPatternIds");
        sentField.setAccessible(true);
        Field embField  = stateClass.getDeclaredField("lastTaskEmbedding");
        embField.setAccessible(true);

        Set<String> sent = (Set<String>) sentField.get(existing);
        float[] emb = (float[]) embField.get(existing);
        Instant backdated = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);

        // Re-create via canonical constructor
        var ctor = stateClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object backdatedState = ctor.newInstance(sent, emb, backdated);

        ((java.util.Map<String, Object>) states).put(conversationId, backdatedState);
    }
}
