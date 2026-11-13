package com.ashu.domainforge.rank;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Transparent 0-100 brandability score; every component is returned so the UI can explain it. */
@Component
public class NameScorer {

  public record Breakdown(double length, double pronounceability, double cleanliness, double tld, double relevance, double riskPenalty, double total) {}

  private static final Map<String, Double> TLD_WEIGHT = Map.ofEntries(
      Map.entry("com", 1.0), Map.entry("ai", 0.85), Map.entry("io", 0.8), Map.entry("co", 0.75),
      Map.entry("in", 0.72), Map.entry("net", 0.7), Map.entry("org", 0.7), Map.entry("app", 0.7),
      Map.entry("dev", 0.65), Map.entry("shop", 0.6), Map.entry("store", 0.55), Map.entry("xyz", 0.4));

  public Breakdown score(String label, String tld, double relevance, double risk) {
    double len = lengthScore(label.length());
    double pron = pronounceability(label);
    double clean = (label.contains("-") ? 0.3 : 1.0) * (label.matches(".*\\d.*") ? 0.5 : 1.0);
    double tldW = TLD_WEIGHT.getOrDefault(tld, 0.5);
    double raw = 100 * (0.28 * len + 0.25 * pron + 0.15 * clean + 0.20 * tldW + 0.12 * relevance);
    double penalty = 45 * risk;
    double total = Math.max(0, Math.min(100, raw - penalty));
    return new Breakdown(r(len), r(pron), r(clean), r(tldW), r(relevance), r(penalty), r(total));
  }

  static double lengthScore(int n) {
    // Sweet spot 5-9 characters; linear fall-off outside it.
    if (n >= 5 && n <= 9) return 1.0;
    if (n < 5) return Math.max(0, 1 - (5 - n) * 0.25);
    return Math.max(0, 1 - (n - 9) * 0.12);
  }

  public static double pronounceability(String label) {
    String s = label.replaceAll("[^a-z]", "");
    if (s.length() < 2) return 0;
    long vowels = s.chars().filter(c -> "aeiou".indexOf(c) >= 0).count();
    double ratio = (double) vowels / s.length();
    double score = 1.0 - Math.min(1.0, Math.abs(ratio - 0.42) / 0.42);
    int run = 0;
    int maxRun = 0;
    for (char c : s.toCharArray()) {
      run = "aeiou".indexOf(c) >= 0 ? 0 : run + 1;
      maxRun = Math.max(maxRun, run);
    }
    if (maxRun >= 4) score *= 0.4;
    else if (maxRun == 3) score *= 0.8;
    if (s.matches(".*(.)\\1\\1.*")) score *= 0.5;
    return Math.max(0, Math.min(1, score));
  }

  private static double r(double d) {
    return Math.round(d * 1000.0) / 1000.0;
  }
}
