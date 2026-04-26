package com.tutor.brain;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public record UsageSummary(long promptTokens,
                           long completionTokens,
                           long totalTokens) {

    public static @NotNull UsageSummary from(@NotNull Optional<GenerateContentResponseUsageMetadata> metadata) {
        if (metadata.isEmpty()) {
            return new UsageSummary(0, 0, 0);
        }
        GenerateContentResponseUsageMetadata usage = metadata.get();
        long prompt = toLong(usage.promptTokenCount());
        long completion = toLong(usage.candidatesTokenCount());
        long total = toLong(usage.totalTokenCount());
        if (total == 0) {
            total = prompt + completion;
        }
        return new UsageSummary(prompt, completion, total);
    }

    private static long toLong(@NotNull Optional<Integer> optional) {
        return optional.map(Integer::longValue).orElse(0L);
    }
}
