package com.ashu.domainforge.gen;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Industry keyword -> evocative brand words, plus keyword extraction from free text. */
@Component
public class Lexicon {

  private static final Set<String> STOP = Set.of(
      "a", "an", "the", "and", "or", "for", "of", "in", "on", "at", "to", "my", "our", "your", "is",
      "are", "with", "that", "this", "i", "we", "want", "need", "open", "opening", "start",
      "starting", "build", "building", "run", "running", "new", "business", "company", "site",
      "website", "online", "app", "selling", "sell", "sells", "make", "making", "small", "local",
      "best", "good", "great", "from", "by", "like", "about", "it", "its", "be", "will", "would");

  private final Map<String, List<String>> related = new HashMap<>();

  @PostConstruct
  void load() throws Exception {
    try (BufferedReader r = new BufferedReader(new InputStreamReader(
        new ClassPathResource("data/lexicon.txt").getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        int c = line.indexOf(':');
        if (c < 0) continue;
        String key = line.substring(0, c).trim();
        List<String> words = new ArrayList<>();
        for (String w : line.substring(c + 1).split(",")) {
          if (!w.isBlank()) words.add(w.trim());
        }
        related.put(key, words);
      }
    }
  }

  public List<String> keywords(String description) {
    Set<String> out = new LinkedHashSet<>();
    for (String tok : description.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (tok.length() < 3 || STOP.contains(tok)) continue;
      out.add(tok);
    }
    return new ArrayList<>(out);
  }

  public List<String> relatedTo(String keyword) {
    List<String> direct = related.get(keyword);
    if (direct != null) return direct;
    // light stemming: coffees -> coffee, baking -> bak(e)
    String k = keyword;
    if (k.endsWith("s") && related.containsKey(k.substring(0, k.length() - 1))) {
      return related.get(k.substring(0, k.length() - 1));
    }
    if (k.endsWith("ing")) {
      String stem = k.substring(0, k.length() - 3);
      for (String cand : new String[] {stem, stem + "e"}) {
        if (related.containsKey(cand)) return related.get(cand);
      }
    }
    return List.of();
  }
}
