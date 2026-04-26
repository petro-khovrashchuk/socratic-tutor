package com.tutor.brain;

import org.jetbrains.annotations.NotNull;

public record BrainResponse(@NotNull String content,
                            @NotNull UsageSummary usageSummary) {
}
