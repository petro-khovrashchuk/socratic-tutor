package com.tutor.brain;

import com.tutor.agent.SocraticPromptBuilder;
import org.jetbrains.annotations.NotNull;

public record BrainRequest(@NotNull SocraticPromptBuilder.ErrorContext errorContext,
                           int userSkillScore,
                           @NotNull String mcpContextSnapshot,
                           @NotNull String modelName) {
}
