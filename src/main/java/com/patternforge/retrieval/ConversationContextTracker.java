package com.patternforge.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-conversation context state for Dynamic Context Pruning (DCP).
 *
 * <p>For each active conversation this service remembers:
 * <ul>
 *   <li>Which pattern IDs have already been sent — so they can be skipped on subsequent turns
 *       unless the new task makes them highly relevant again (re-injection threshold).
 *   <li>The embedding vector of the last task — used to detect task shifts and decide
 *       whether to emit a {@code dropPatternIds} signal.
 * </ul>
 *
 * <p>State is held in-memory and expires after {@value #TTL_HOURS} hour(s) of inactivity.
 * A scheduled sweep runs every 10 minutes to evict stale entries.
 */
@Service
@Slf4j
public class ConversationContextTracker {

    private static final long TTL_HOURS = 1;

    /** Per-conversation state. */
    private record ConversationState(
            Set<String> sentPatternIds,
            float[] lastTaskEmbedding,
            Instant lastUpdated) {

        ConversationState withUpdated(Set<String> addedIds, float[] embedding, Instant now) {
            Set<String> merged = new HashSet<>(sentPatternIds);
            merged.addAll(addedIds);
            return new ConversationState(merged, embedding, now);
        }
    }

    private final Map<String, ConversationState> states = new ConcurrentHashMap<>();

    /**
     * Returns the set of pattern IDs already sent in this conversation.
     * Returns an empty (unmodifiable) set for unknown conversation IDs.
     */
    public Set<String> getSentPatternIds(String conversationId) {
        ConversationState state = states.get(conversationId);
        return state == null ? Collections.emptySet() : Collections.unmodifiableSet(state.sentPatternIds());
    }

    /**
     * Returns the task embedding recorded during the previous turn of this conversation.
     * Empty when this is the first turn or when Ollama was unavailable on the prior turn.
     */
    public Optional<float[]> getLastTaskEmbedding(String conversationId) {
        ConversationState state = states.get(conversationId);
        if (state == null || state.lastTaskEmbedding() == null) {
            return Optional.empty();
        }
        return Optional.of(state.lastTaskEmbedding());
    }

    /**
     * Records the patterns sent during this turn and the task embedding used to retrieve them.
     *
     * @param conversationId  conversation being updated
     * @param newlySentIds    pattern IDs included in this turn's response
     * @param taskEmbedding   embedding for the current task (may be {@code null} when Ollama unavailable)
     */
    public void update(String conversationId, Set<String> newlySentIds, float[] taskEmbedding) {
        Instant now = Instant.now();
        states.merge(
            conversationId,
            new ConversationState(new HashSet<>(newlySentIds), taskEmbedding, now),
            (existing, incoming) -> existing.withUpdated(newlySentIds, taskEmbedding, now)
        );
        log.debug("DCP tracker updated: conversationId={}, +{}ids, totalSent={}, hasEmbedding={}",
            conversationId, newlySentIds.size(),
            states.get(conversationId).sentPatternIds().size(),
            taskEmbedding != null);
    }

    /**
     * Evicts conversation entries that have been idle for more than {@value #TTL_HOURS} hour(s).
     * Runs every 10 minutes.
     */
    @Scheduled(fixedDelay = 600_000)
    public void evictStaleConversations() {
        Instant cutoff = Instant.now().minus(TTL_HOURS, ChronoUnit.HOURS);
        int before = states.size();
        states.entrySet().removeIf(entry -> entry.getValue().lastUpdated().isBefore(cutoff));
        int evicted = before - states.size();
        if (evicted > 0) {
            log.info("DCP tracker evicted {} stale conversation(s) (remaining={})", evicted, states.size());
        }
    }
}
