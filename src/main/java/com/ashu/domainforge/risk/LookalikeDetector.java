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

  /** TLDs disproportionately used for abuse; a brand name on these is more suspicious. */
  private static final Set<String> SUSPICIOUS_TLDS = Set.of(
      "xyz", "biz", "info", "online", "top", "click", "site", "club", "work", "support", "buzz",
      "icu", "cfd", "shop", "live", "link", "rest", "zip", "mov", "tk", "ml", "ga", "cf", "gq");

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

    // Three kinds of normalised forms, because the right rule differs for each:
    //  FULL     whole label, homoglyphs folded       -> exact / typo / containment rules
    //  STRIPPED whole label minus phishing keywords  -> exact match only (no edit distance)
    //  SEGMENT  one hyphen-separated part            -> exact match only
    Set<String> full = new LinkedHashSet<>(Confusables.skeletons(label));
    Set<String> stripped = new LinkedHashSet<>();
    for (String sk : full) {
      String st = stripPhishWords(sk);
      if (!st.equals(sk)) stripped.add(st);
    }
    Set<String> segments = new LinkedHashSet<>();
    if (label.contains("-")) {
      for (String seg : label.split("-")) {
        if (seg.length() >= 4) {
          for (String sk : Confusables.skeletons(seg)) segments.add(stripPhishWords(sk));
        }
      }
    }
    Set<String> query = new LinkedHashSet<>();
    query.addAll(full);
    query.addAll(stripped);
    query.addAll(segments);
    query.removeIf(f -> f.length() < 3);

    Map<String, Double> sims = new HashMap<>();
    for (String f : query) {
      for (Neighbor n : index.nearest(f, K)) sims.merge(n.label(), n.similarity(), Math::max);
    }

    String plain = label.replace("-", "");
    String lastTld = tld.contains(".") ? tld.substring(tld.lastIndexOf('.') + 1) : tld;
    Map<String, LookalikeReport.Match> best = new HashMap<>();
    for (Map.Entry<String, Double> e : sims.entrySet()) {
      String brand = e.getKey();
      boolean prominent = catalog.isProtected(brand);
      Double score = null;
      String reason = null;
      for (String f : full) {
        Double s2 = null;
        String r2 = null;
        if (f.equals(brand)) {
          if (plain.equals(brand) && label.equals(brand)) {
            if (prominent && SUSPICIOUS_TLDS.contains(lastTld)) {
              s2 = 0.55;
              r2 = "brand name on a suspicious TLD (." + lastTld + ")";
            } else if (prominent) {
              s2 = 0.3;
              r2 = "brand name on a different TLD (" + domain + ")";
            }
          } else if (plain.equals(brand)) {
            if (prominent) {
              s2 = 0.5;
              r2 = "hyphenated copy of the brand name";
            }
          } else {
            s2 = 0.95;
            r2 = "visual lookalike of the brand (homoglyph / obfuscation)";
          }
        } else if (prominent && brand.length() >= 5) {
          int d = Confusables.distance(f, brand);
          if (d == 1) {
            s2 = 0.85;
            r2 = "one-character typo of the brand";
          } else if (d == 2 && brand.length() >= 8) {
            s2 = 0.6;
            r2 = "two-character typo of a long brand";
          } else if (f.contains(brand) && f.length() > brand.length() && brand.length() >= 8) {
            s2 = 0.5;
            r2 = "contains the brand name";
          }
        }
        if (s2 != null && (score == null || s2 > score)) { score = s2; reason = r2; }
      }
      if (prominent) {
        for (String st : stripped) {
          if (st.equals(brand) && (score == null || 0.85 > score)) {
            score = 0.85;
            reason = "brand name combined with a phishing keyword";
          }
        }
        if (brand.length() >= 5) {
          for (String sg : segments) {
            if (sg.equals(brand) && (score == null || 0.5 > score)) {
              score = 0.5;
              reason = "brand name as one part of a hyphenated domain";
            }
          }
        }
      }
      if (score != null) {
        best.put(brand, new LookalikeReport.Match(catalog.officialDomainsFor(brand).get(0), reason, score, round(e.getValue())));
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
