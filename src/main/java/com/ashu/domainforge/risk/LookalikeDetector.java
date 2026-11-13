package com.ashu.domainforge.risk;

import com.ashu.domainforge.index.VexIndex;
import com.ashu.domainforge.index.VexIndex.Neighbor;
import java.net.IDN;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Flags typosquats, homoglyph tricks and "brand + phishing keyword" domains.
 *
 * <p>Candidate brands are retrieved with an approximate nearest-neighbour query against the Vex
 * HNSW index (so cost does not grow linearly with the brand list), then confirmed with exact rules
 * (skeleton equality, edit distance, containment).
 */
@Component
public class LookalikeDetector {

  private static final List<String> PHISH_WORDS = List.of(
      "login", "signin", "secure", "security", "verify", "verification", "support", "account",
      "update", "wallet", "billing", "payment", "help", "service", "official", "online", "portal",
      "recovery", "customer", "care", "refund", "alert");

  private static final int K = 6;

  private final VexIndex index;
  private final BrandCatalog catalog;

  public LookalikeDetector(@Qualifier("brandIndex") VexIndex index, BrandCatalog catalog) {
    this.index = index;
    this.catalog = catalog;
  }

  public LookalikeReport analyze(String rawDomain) {
    String domain = rawDomain.trim().toLowerCase(Locale.ROOT);
    if (domain.startsWith("http://")) domain = domain.substring(7);
    if (domain.startsWith("https://")) domain = domain.substring(8);
    int slash = domain.indexOf('/');
    if (slash >= 0) domain = domain.substring(0, slash);
    if (domain.startsWith("www.")) domain = domain.substring(4);

    List<String> flags = new ArrayList<>();
    if (domain.contains("xn--")) {
      try {
        String uni = IDN.toUnicode(domain);
        flags.add("punycode: decodes to " + uni);
        domain = uni;
      } catch (Exception ignored) {
        flags.add("malformed punycode");
      }
    }
    if (catalog.isOfficial(domain)) return LookalikeReport.none(domain, true, flags);

    int dot = domain.indexOf('.');
    String label = dot < 0 ? domain : domain.substring(0, dot);
    String tld = dot < 0 ? "" : domain.substring(dot + 1);

    if (Confusables.mixedScript(label)) flags.add("mixed scripts in one label (classic homograph attack)");
    else if (Confusables.hasNonAscii(label)) flags.add("non-ASCII characters in label");
    if (label.chars().filter(c -> c == '-').count() >= 2) flags.add("multiple hyphens");

    Set<String> forms = new LinkedHashSet<>();
    for (String sk : Confusables.skeletons(label)) {
      forms.add(sk);
      forms.add(stripPhishWords(sk));
    }
    for (String seg : label.split("-")) {
      if (seg.length() >= 4 && !seg.equals(label)) {
        for (String sk : Confusables.skeletons(seg)) forms.add(stripPhishWords(sk));
      }
    }
    forms.removeIf(f -> f.length() < 3);

    Map<String, Double> sims = new HashMap<>();
    for (String f : forms) {
      for (Neighbor n : index.nearest(f, K)) sims.merge(n.label(), n.similarity(), Math::max);
    }

    String plain = label.replace("-", "");
    boolean usedKeyword = false;
    Map<String, LookalikeReport.Match> best = new HashMap<>();
    for (Map.Entry<String, Double> e : sims.entrySet()) {
      String brand = e.getKey();
      for (String f : forms) {
        Double score = null;
        String reason = null;
        boolean keywordHit = false;
        for (String sk : Confusables.skeletons(label)) {
          if (stripPhishWords(sk).equals(brand) && !sk.equals(brand)) keywordHit = true;
        }
        if (f.equals(brand)) {
          if (keywordHit) {
            score = 0.85;
            reason = "brand name combined with a phishing keyword";
          } else if (plain.equals(brand)) {
            score = 0.55;
            reason = "brand name registered on a different domain (" + domain + ")";
          } else {
            score = 0.95;
            reason = "visual lookalike of the brand (homoglyph / obfuscation)";
          }
        } else if (brand.length() >= 5) {
          int d = Confusables.distance(f, brand);
          if (d == 1) {
            score = 0.85;
            reason = "one-character typo of the brand";
          } else if (d == 2 && brand.length() >= 8) {
            score = 0.6;
            reason = "two-character typo of a long brand";
          } else if (f.contains(brand) && f.length() > brand.length() && brand.length() >= 6) {
            score = 0.6;
            reason = "contains the brand name";
          }
        }
        if (score != null) {
          LookalikeReport.Match cur = best.get(brand);
          if (cur == null || score > cur.score()) {
            best.put(brand, new LookalikeReport.Match(catalog.officialDomainsFor(brand).get(0), reason, score, round(e.getValue())));
          }
        }
      }
    }

    List<LookalikeReport.Match> matches = new ArrayList<>(best.values());
    matches.sort(Comparator.comparingDouble(LookalikeReport.Match::score).reversed());
    double risk = matches.isEmpty() ? 0.0 : matches.get(0).score();
    if (flags.stream().anyMatch(f -> f.startsWith("mixed scripts"))) risk = Math.min(1.0, risk + 0.1);
    if (matches.isEmpty() && flags.stream().anyMatch(f -> f.startsWith("mixed scripts"))) risk = 0.5;
    String level = risk >= 0.8 ? "HIGH" : risk >= 0.5 ? "MEDIUM" : risk > 0 ? "LOW" : "NONE";
    return new LookalikeReport(domain, round(risk), level, false, matches.stream().limit(3).toList(), flags);
  }

  static String stripPhishWords(String s) {
    String out = s;
    boolean changed = true;
    while (changed) {
      changed = false;
      for (String w : PHISH_WORDS) {
        if (out.length() > w.length() + 2 && out.contains(w)) {
          out = out.replace(w, "");
          changed = true;
        }
      }
    }
    return out;
  }

  private static double round(double d) {
    return Math.round(d * 1000.0) / 1000.0;
  }
}
