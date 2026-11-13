package com.ashu.domainforge.gen;

import com.ashu.domainforge.risk.LookalikeReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** LLM-written explanations: why a name fits the business, and what a risk finding means. */
@Component
public class AiAdvisor {
  private final LlmClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AiAdvisor(LlmClient client) {
    this.client = client;
  }

  public boolean enabled() {
    return client.enabled();
  }

  /** One-line rationale per domain, in a single LLM call. Empty map if AI is unavailable. */
  public Map<String, String> explainNames(String description, List<String> domains) {
    Map<String, String> out = new HashMap<>();
    if (!client.enabled() || domains.isEmpty()) return out;
    String prompt = "Business: \"" + description.replace("\"", "'") + "\". For each domain below write ONE short sentence "
        + "(max 18 words) on why it works as a brand name (memorability, meaning, sound). "
        + "Reply with ONLY a JSON object mapping each domain to its sentence.\nDomains: "
        + String.join(", ", domains);
    Optional<String> text = client.complete(prompt, 900);
    if (text.isEmpty()) return out;
    try {
      String json = LlmClient.extractJson(text.get(), '{', '}');
      if (json == null) return out;
      JsonNode root = mapper.readTree(json);
      root.fields().forEachRemaining(e -> out.put(e.getKey().toLowerCase(), e.getValue().asText()));
    } catch (Exception ignored) {
      // malformed model output -> no explanations
    }
    return out;
  }

  /** Plain-English explanation of a lookalike finding for a non-technical user. */
  public Optional<String> explainRisk(LookalikeReport r) {
    if (!client.enabled() || r.matches().isEmpty()) return Optional.empty();
    var m = r.matches().get(0);
    String prompt = "A domain-security tool flagged \"" + r.domain() + "\" as " + r.level() + " risk. Top finding: it resembles "
        + m.brand() + " because: " + m.reason() + ". Other flags: " + r.flags()
        + ". In 2 sentences, explain to a small-business owner what this means and what to do. No markdown.";
    return client.complete(prompt, 250).map(String::trim);
  }
}
