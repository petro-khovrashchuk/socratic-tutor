package com.tutor.brain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.errors.ClientException;
import com.google.genai.types.GenerateContentResponse;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.Disposable;
import com.tutor.agent.SocraticPromptBuilder;
import com.tutor.settings.TutorSettingsState;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
public final class BrainCoordinator implements Disposable {

    private static final String FALLBACK_URL = "http://localhost:11434/api/generate";
    private static final String DEFAULT_MODEL = "gemini-3.1-flash";
    private static final long DEFAULT_DAILY_LIMIT = 150_000L;

    private final TutorSettingsState settings = TutorSettingsState.getInstance();
    private final GeminiBrainClient geminiBrainClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long dailyLimit;
    private final String fallbackModel;

    public BrainCoordinator() {
        this(System.getenv("GOOGLE_API_KEY"), System.getenv("BRAIN_DAILY_TOKEN_LIMIT"),
            System.getenv("OLLAMA_MODEL"));
    }

    private BrainCoordinator(@NotNull String apiKey,
                             String limitOverride,
                             String fallbackModelOverride) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GOOGLE_API_KEY must be set to reach Gemini");
        }
        this.geminiBrainClient = new GeminiBrainClient(apiKey);
        this.dailyLimit = parseLimit(limitOverride);
        this.fallbackModel = Optional.ofNullable(fallbackModelOverride).orElse("gemma3");
    }

    public BrainResponse ask(@NotNull BrainRequest request) {
        if (shouldSoftSwitch()) {
            return fallback(request, "quota");
        }
        CompletableFuture<BrainResponse> future = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try {
                future.complete(callGemini(request));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (shouldFallback(cause)) {
                return fallback(request, "rate-limit");
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private BrainResponse callGemini(@NotNull BrainRequest request) throws IOException {
        String prompt = SocraticPromptBuilder.build(request.errorContext(), request.userSkillScore(),
            request.mcpContextSnapshot());
        String model = request.modelName().isBlank() ? DEFAULT_MODEL : request.modelName();
        GenerateContentResponse response = geminiBrainClient.generateContent(model, prompt);
        UsageSummary usage = UsageSummary.from(response.usageMetadata());
        settings.recordTokenUsage(usage.totalTokens());
        return new BrainResponse(getText(response), usage);
    }

    private BrainResponse fallback(@NotNull BrainRequest request, String reason) {
        try {
            String prompt = SocraticPromptBuilder.build(request.errorContext(), request.userSkillScore(),
                request.mcpContextSnapshot());
            Map<String, Object> payload = Map.of(
                "model", fallbackModel,
                "prompt", prompt,
                "raw", true,
                "stream", false
            );
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(FALLBACK_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() != 200) {
                throw new IOException("Fallback HTTP " + httpResponse.statusCode());
            }
            JsonNode root = objectMapper.readTree(httpResponse.body());
            long promptTokens = root.path("prompt_eval_count").asLong(0);
            long completionTokens = root.path("eval_count").asLong(0);
            long totalTokens = promptTokens + completionTokens;
            UsageSummary usage = new UsageSummary(promptTokens, completionTokens, totalTokens);
            settings.recordTokenUsage(totalTokens);
            return new BrainResponse(root.path("response").asText(""), usage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Local fallback interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Local fallback failed", e);
        }
    }

    private boolean shouldSoftSwitch() {
        return settings.shouldSoftSwitch(dailyLimit);
    }

    private static boolean shouldFallback(Throwable cause) {
        if (cause instanceof ClientException clientException) {
            return clientException.code() == 429;
        }
        return cause instanceof IOException;
    }

    private static String getText(@NotNull GenerateContentResponse response) {
        String text = response.text();
        if (text != null && !text.isBlank()) {
            return text;
        }
        return response.candidates().stream()
            .map(candidate -> candidate.text())
            .filter(t -> t != null && !t.isBlank())
            .findFirst()
            .orElse("");
    }

    private static long parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_DAILY_LIMIT;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return DEFAULT_DAILY_LIMIT;
        }
    }

    @Override
    public void dispose() {
        try {
            geminiBrainClient.close();
        } catch (Exception ignored) {
            // intentionally ignored; cleanup best effort
        }
    }
}
