package com.tutor.agent;

import static java.lang.StringTemplate.RAW;
import static java.lang.StringTemplate.STR;

import java.lang.StringTemplate;
import org.jetbrains.annotations.NotNull;

public final class SocraticPromptBuilder {

    private SocraticPromptBuilder() {
    }

    public static @NotNull String build(@NotNull ErrorContext context,
                                        int userSkillScore,
                                        @NotNull String mcpContextSnapshot) {
        String explanation = buildExplanation(context);
        String analogy = buildAnalogy(context);
        String question = buildQuestion(context, playerHintLevel(userSkillScore));
        String snapshot = normalizeContext(mcpContextSnapshot);

        StringTemplate template = RAW."""
            Explain:
            \{explanation}
            
            Analogy:
            \{analogy}
            
            Question:
            \{question}
            
            MCP Context Snapshot:
            \{snapshot}
            """;

        return STR.process(template);
    }

    private static String buildExplanation(@NotNull ErrorContext context) {
        return "At " + context.location() + " the verifier is flagging " + context.errorMessage()
            + "; notice that the behavior deviates from " + context.expectedBehavior() + ".";
    }

    private static String buildAnalogy(@NotNull ErrorContext context) {
        return switch (context.difficulty()) {
            case BEGINNER -> "Imagine the code as a recipe: you're missing a key ingredient and the dish can no longer rise.";
            case INTERMEDIATE -> "It's like a train that arrives too early because the schedule (the type) doesn't match the platform (the declaration).";
            case ADVANCED -> "Think of the call stack as a relay race: the baton (data contract) changes hands but the next runner (consumer) still expects the previous pace.";
        };
    }

    private static String buildQuestion(@NotNull ErrorContext context, double vagueness) {
        return context.difficulty().question(vagueness, context.expectedBehavior(), context.location());
    }

    private static double playerHintLevel(int userSkillScore) {
        return Math.min(1d, Math.max(0d, userSkillScore / 20d));
    }

    private static String normalizeContext(@NotNull String context) {
        if (context.isBlank()) {
            return "No MCP context captured in this turn.";
        }
        return context;
    }

    public enum Difficulty {
        BEGINNER("Why does the IDE raise this?"),
        INTERMEDIATE("How could you guide the type checker toward acceptance?"),
        ADVANCED("Which assumptions is the runtime making about this call?");

        private final String questionPrompt;

        Difficulty(String questionPrompt) {
            this.questionPrompt = questionPrompt;
        }

        public String question(double vagueness, String expected, String location) {
            double hint = Math.min(1d, Math.max(0d, vagueness));
            if (hint > 0.6) {
                return String.format("What subtle assumption about %s near %s keeps it from compiling?", expected, location);
            }
            if (hint > 0.3) {
                return String.format("How might aligning the behavior with %s at %s remove the error?", expected, location);
            }
            return questionPrompt + " at " + location + "?";
        }
    }

    public record ErrorContext(@NotNull String errorMessage,
                               @NotNull String location,
                               @NotNull String expectedBehavior,
                               @NotNull Difficulty difficulty) {
    }
}
