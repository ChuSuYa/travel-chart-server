package com.travelchart.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tracks search keywords via Redis sorted set for hot words and autocomplete.
 *
 * Data structure:
 * - search:hotwords -> sorted set with keyword as member, search count as score
 * - search:suggest:{prefix} -> sorted set for prefix-based autocomplete
 *   (populated asynchronously; falls back to scanning search:hotwords via ZRANGEBYLEX)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotWordsService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String HOTWORDS_KEY = "search:hotwords";
    private static final long TTL_DAYS = 7;

    // Seed hot words — popular Chinese travel searches
    private static final List<String> SEED_HOT_WORDS = List.of(
            "故宫", "西湖", "迪士尼", "熊猫", "兵马俑",
            "海岛", "雪山", "古镇", "美食", "自驾"
    );

    /**
     * Initialize seed hot words in Redis if the sorted set is empty.
     */
    @PostConstruct
    public void initSeedWords() {
        Long size = stringRedisTemplate.opsForZSet().size(HOTWORDS_KEY);
        if (size == null || size == 0) {
            log.info("Seeding hot words...");
            for (String word : SEED_HOT_WORDS) {
                stringRedisTemplate.opsForZSet().add(HOTWORDS_KEY, word, 1);
            }
            stringRedisTemplate.expire(HOTWORDS_KEY, TTL_DAYS, TimeUnit.DAYS);
        }
    }

    /**
     * Record a search keyword: increment its score in the sorted set.
     */
    public void recordSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        String trimmed = keyword.trim();
        stringRedisTemplate.opsForZSet().incrementScore(HOTWORDS_KEY, trimmed, 1);
        // Refresh TTL on each write
        stringRedisTemplate.expire(HOTWORDS_KEY, TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * Get the top N hot words ranked by search volume.
     */
    public List<Map<String, Object>> getTopHotWords(int limit) {
        Set<String> members = stringRedisTemplate.opsForZSet()
                .reverseRange(HOTWORDS_KEY, 0, limit - 1);

        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String member : members) {
            Double score = stringRedisTemplate.opsForZSet().score(HOTWORDS_KEY, member);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("keyword", member);
            item.put("score", score != null ? score.longValue() : 0);
            result.add(item);
        }
        return result;
    }

    /**
     * Get autocomplete suggestions for a given prefix.
     * Returns matching keywords from the hot words sorted set by scanning
     * with ZRANGEBYLEX on a secondary sorted set, or by filtering the top hot words.
     *
     * For production with high volume, use ES completion suggester or a dedicated
     * Redis sorted set with lexicographic ordering.
     */
    public List<String> getSuggestions(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return getTopHotWords(limit).stream()
                    .map(m -> (String) m.get("keyword"))
                    .collect(Collectors.toList());
        }

        String trimmed = prefix.trim();
        List<String> suggestions = new ArrayList<>();

        // Strategy: get top 200 hot words and filter by prefix match.
        // For high-scale production, maintain a separate sorted set with
        // lexicographic ordering for O(log N) prefix queries.
        Set<String> allMembers = stringRedisTemplate.opsForZSet()
                .reverseRange(HOTWORDS_KEY, 0, 199);

        if (allMembers != null) {
            for (String member : allMembers) {
                if (member.startsWith(trimmed) && suggestions.size() < limit) {
                    suggestions.add(member);
                }
            }
        }

        return suggestions;
    }
}
