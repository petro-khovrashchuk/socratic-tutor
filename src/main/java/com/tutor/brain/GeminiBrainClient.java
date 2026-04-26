package com.tutor.brain;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public final class GeminiBrainClient implements AutoCloseable {

    private final Client delegate;

    public GeminiBrainClient(@NotNull String apiKey) {
        this.delegate = Client.builder()
            .apiKey(apiKey)
            .httpOptions(HttpOptions.builder().apiVersion("v1").build())
            .location("global")
            .build();
    }

    public GenerateContentResponse generateContent(@NotNull String modelName,
                                                   @NotNull String prompt) throws IOException {
        return delegate.models().generateContent(modelName, prompt, null);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
