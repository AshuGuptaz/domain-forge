package com.ashu.domainforge.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Creative generator: asks the LLM for brandable names. No key / any failure = no candidates. */
@Component
public class LlmNameGenerator implements NameGenerator {

  private final LlmClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public LlmNameGenerator(LlmClient client) {
    this.client = client;
  }

  public boolean enabled() {
    return client.enabled();
  }

  @Override
  public String name() {
    return "llm";
  }

  @Override
  public List<String> generate(String description, List<String> keywords, int limit) {
    String prompt = "Suggest " + Math.min(limit, 40)
        + " short, brandable, easy-to-spell domain name labels (lowercase letters only, 4-12 chars, no TLD, no hyphens) for this business: \""
        + description.replace("\"", "'")
        + "\". Mix real words, blends and invented names. Reply with ONLY a JSON array of strings.";
    return client.complete(prompt, 600).map(text -> {
      List<String> out = new ArrayList<>();
      try {
        String json = LlmClient.extractJson(text, '[', ']');
        if (json == null) return out;
        for (JsonNode n : mapper.readTree(json)) {
          out.add(n.asText().toLowerCase().replaceAll("[^a-z0-9]", ""));
        }
      } catch (Exception ignored) {
        // malformed model output -> no candidates
      }
      return out;
    }).orElse(List.of());
  }
}
