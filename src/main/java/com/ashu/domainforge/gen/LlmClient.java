package com.ashu.domainforge.gen;

import com.ashu.domainforge.config.DomainForgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Minimal client for any OpenAI-compatible chat endpoint (Ollama, Groq, LM Studio, vLLM...).
 * Enabled only when a model name is configured. Returns empty on any failure so callers degrade.
 */
@Component
public class LlmClient {
  private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

  private final DomainForgeProperties.Llm cfg;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public LlmClient(DomainForgeProperties props) {
    this.cfg = props.getLlm();
  }

  public boolean enabled() {
    return cfg.getModel() != null && !cfg.getModel().isBlank();
  }

  public Optional<String> complete(String prompt, int maxTokens) {
    if (!enabled()) return Optional.empty();
    try {
      String body = mapper.writeValueAsString(Map.of(
          "model", cfg.getModel(),
          "max_tokens", maxTokens,
          "temperature", 0.8,
          "messages", List.of(Map.of("role", "user", "content", prompt))));
      HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(cfg.getBaseUrl() + "/chat/completions"))
          .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
          .header("content-type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
      if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
        rb.header("authorization", "Bearer " + cfg.getApiKey());
      }
      HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        log.warn("LLM endpoint returned HTTP {}", resp.statusCode());
        return Optional.empty();
      }
      String text = mapper.readTree(resp.body()).path("choices").path(0).path("message").path("content").asText("");
      return text.isBlank() ? Optional.empty() : Optional.of(text);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      log.warn("LLM call failed: {}", e.toString());
      return Optional.empty();
    }
  }

  /** Extracts the first [...] or {...} JSON block from a model reply. */
  public static String extractJson(String text, char open, char close) {
    int a = text.indexOf(open);
    int b = text.lastIndexOf(close);
    return (a < 0 || b < a) ? null : text.substring(a, b + 1);
  }
}
