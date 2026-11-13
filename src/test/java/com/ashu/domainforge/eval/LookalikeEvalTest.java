package com.ashu.domainforge.eval;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ashu.domainforge.eval.TyposquatGenerator.Sample;
import com.ashu.domainforge.risk.LookalikeReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Precision / recall of the lookalike detector against synthetic attacks and real legitimate
 * domains. Opt-in: mvn test -Dtest=LookalikeEvalTest -Dforge.eval=true (needs scripts/fetch_tranco.sh).
 */
class LookalikeEvalTest {

  static final int BRAND_TOP_N = Integer.getInteger("forge.brands", 10_000);
  /** The most prominent Tranco domains that get full lookalike rules; ranks beyond are long-tail. */
  static final int PROTECTED_TOP_N = Integer.getInteger("forge.protected", 1_000);

  @Test
  void evaluate() throws Exception {
    assumeTrue(EvalSupport.enabled(), "evaluation disabled (set -Dforge.eval=true and fetch Tranco data)");

    List<String> brands = EvalSupport.brandSet(BRAND_TOP_N);
    int protectedCount = EvalSupport.curatedBrands().size() + PROTECTED_TOP_N;
    var rig = EvalSupport.rig(brands, protectedCount);

    // Attack samples are generated from the hand-curated, recognisable brands only.
    List<String> curated = EvalSupport.curatedBrands();
    List<Sample> positives = TyposquatGenerator.generate(curated, 7);

    // Negatives: real legitimate domains from Tranco ranks 10,001-60,000 (not in the brand set).
    List<String> pool = new ArrayList<>(EvalSupport.tranco(10_001, 60_000));
    pool.removeIf(d -> rig.catalog().isOfficial(d) || !d.matches("[a-z0-9.-]+"));
    java.util.Collections.shuffle(pool, new Random(11));
    List<String> negatives = pool.subList(0, Math.min(5000, pool.size()));

    // Negatives #2: names shaped like the product's own suggestions (word + affix + tld).
    List<String> words = Files.readAllLines(Path.of("src/main/resources/data/words.txt"));
    String[] affix = {"hub", "labs", "ly", "ify", "box", "kit", "base", "works"};
    String[] tlds = {"com", "ai", "in", "net"};
    Random r = new Random(13);
    List<String> synthetic = new ArrayList<>();
    for (int i = 0; i < 3000; i++) {
      String w = words.get(r.nextInt(words.size()));
      synthetic.add(w + affix[r.nextInt(affix.length)] + "." + tlds[r.nextInt(tlds.length)]);
    }

    // ---- positives
    Map<String, int[]> perCat = new TreeMap<>(); // [total, flaggedMedium+, flaggedHigh, correctBrand]
    List<String> misses = new ArrayList<>();
    for (Sample s : positives) {
      LookalikeReport rep = rig.detector().analyze(s.domain());
      int[] c = perCat.computeIfAbsent(s.category(), k -> new int[4]);
      c[0]++;
      boolean med = rep.risk() >= 0.5;
      boolean high = rep.risk() >= 0.8;
      if (med) c[1]++;
      if (high) c[2]++;
      if (med && !rep.matches().isEmpty() && rep.matches().get(0).brand().equals(s.brandDomain())) c[3]++;
      if (!med && misses.size() < 400) misses.add(s.domain() + "  (" + s.category() + " of " + s.brandDomain() + ")");
    }

    // ---- negatives
    int[] real = score(rig, negatives);
    int[] syn = score(rig, synthetic);

    int tot = 0, med = 0, high = 0, correct = 0;
    for (int[] c : perCat.values()) { tot += c[0]; med += c[1]; high += c[2]; correct += c[3]; }

    StringBuilder md = new StringBuilder();
    md.append("# Lookalike detector evaluation\n\n");
    md.append("Generated ").append(LocalDate.now()).append(" by `LookalikeEvalTest` (`mvn test -Dtest=LookalikeEvalTest -Dforge.eval=true`).\n\n");
    md.append("**Brand index:** ").append(rig.catalog().size()).append(" brands in Vex (136 hand-curated + Tranco top ").append(BRAND_TOP_N).append("). Full rules apply to the curated brands + Tranco top ").append(PROTECTED_TOP_N).append("; the long tail is flagged only on exact homoglyph match.\n\n");
    md.append("**Attack set:** ").append(positives.size()).append(" synthetic domains generated from the hand-curated brands across ").append(perCat.size()).append(" attack types (dnstwist-style).\n");
    md.append("**Benign set A:** ").append(negatives.size()).append(" real domains sampled from Tranco ranks 10,001-60,000.\n");
    md.append("**Benign set B:** ").append(synthetic.size()).append(" product-style generated names (word + affix + TLD).\n\n");
    md.append("Flag threshold: *flagged* = risk >= 0.5 (MEDIUM or HIGH); *high* = risk >= 0.8.\n\n");
    md.append("## Recall by attack type\n\n| Attack type | n | flagged | high | recall | correct brand named |\n|---|---:|---:|---:|---:|---:|\n");
    for (var e : perCat.entrySet()) {
      int[] c = e.getValue();
      md.append(String.format("| %s | %d | %d | %d | %.1f%% | %.1f%% |%n", e.getKey(), c[0], c[1], c[2], 100.0 * c[1] / c[0], 100.0 * c[3] / Math.max(1, c[1])));
    }
    md.append(String.format("| **all** | %d | %d | %d | **%.1f%%** | %.1f%% |%n%n", tot, med, high, 100.0 * med / tot, 100.0 * correct / Math.max(1, med)));
    md.append("## False positives\n\n| Benign set | n | flagged (>=0.5) | FPR | high (>=0.8) | FPR(high) |\n|---|---:|---:|---:|---:|---:|\n");
    md.append(String.format("| A: real Tranco 10k-60k | %d | %d | %.2f%% | %d | %.2f%% |%n", negatives.size(), real[0], 100.0 * real[0] / negatives.size(), real[1], 100.0 * real[1] / negatives.size()));
    md.append(String.format("| B: product-style names | %d | %d | %.2f%% | %d | %.2f%% |%n%n", synthetic.size(), syn[0], 100.0 * syn[0] / synthetic.size(), syn[1], 100.0 * syn[1] / synthetic.size()));
    double precision = (double) med / (med + real[0] * (positives.size() / (double) negatives.size()));
    md.append(String.format("Illustrative precision at a 1:1 attack:benign mix (set A): **%.1f%%**.%n%n", 100.0 * med / (med + (double) real[0] / negatives.size() * positives.size())));
    md.append("## Error analysis\n\n### Sample of missed attacks\n\n```\n");
    for (int i = 0; i < Math.min(25, misses.size()); i++) md.append(misses.get(i)).append('\n');
    md.append("```\n\n### Sample of false positives on real domains\n\n```\n");
    for (String s : real(rig, negatives, 25)) md.append(s).append('\n');
    md.append("```\n");

    Files.createDirectories(Path.of("docs"));
    Files.writeString(Path.of("docs/EVAL.md"), md.toString());
    System.out.println(md);
  }

  /** returns [flaggedMedium+, flaggedHigh] */
  private static int[] score(EvalSupport.Rig rig, List<String> domains) {
    int med = 0, high = 0;
    for (String d : domains) {
      double risk = rig.detector().analyze(d).risk();
      if (risk >= 0.5) med++;
      if (risk >= 0.8) high++;
    }
    return new int[] {med, high};
  }

  private static List<String> real(EvalSupport.Rig rig, List<String> domains, int limit) {
    List<String> out = new ArrayList<>();
    for (String d : domains) {
      LookalikeReport rep = rig.detector().analyze(d);
      if (rep.risk() >= 0.5 && out.size() < limit) {
        var m = rep.matches().get(0);
        out.add(String.format("%-32s risk %.2f  ~ %s  (%s)", d, rep.risk(), m.brand(), m.reason()));
      }
    }
    return out;
  }
}
