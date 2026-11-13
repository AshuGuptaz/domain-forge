package com.ashu.domainforge.eval;

import com.ashu.domainforge.embed.NgramEmbedder;
import com.ashu.domainforge.index.VexIndex;
import com.ashu.domainforge.risk.BrandCatalog;
import com.ashu.domainforge.risk.LookalikeDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared helpers for the (opt-in) evaluation tests. Run with -Dforge.eval=true. */
final class EvalSupport {
  private EvalSupport() {}

  static final Path TRANCO = Path.of("data/top-1m.csv");

  static boolean enabled() {
    return Boolean.getBoolean("forge.eval") && Files.exists(TRANCO);
  }

  /** Tranco domains in rank order, rank 1 first. */
  static List<String> tranco(int fromRank, int toRank) throws IOException {
    List<String> out = new ArrayList<>();
    try (var lines = Files.lines(TRANCO)) {
      var it = lines.iterator();
      while (it.hasNext()) {
        String[] p = it.next().split(",");
        int rank = Integer.parseInt(p[0]);
        if (rank < fromRank) continue;
        if (rank > toRank) break;
        out.add(p[1].toLowerCase());
      }
    }
    return out;
  }

  static List<String> curatedBrands() throws IOException {
    try (var in = EvalSupport.class.getResourceAsStream("/data/brands.txt")) {
      return new String(in.readAllBytes()).lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
  }

  /** Brand set = hand-curated brands + the first {@code topN} Tranco domains (deduplicated by label). */
  static List<String> brandSet(int topN) throws IOException {
    Set<String> labels = new LinkedHashSet<>();
    List<String> out = new ArrayList<>();
    for (String d : curatedBrands()) {
      if (labels.add(d.substring(0, d.indexOf('.')).replace("-", ""))) out.add(d);
    }
    for (String d : tranco(1, topN)) {
      int dot = d.indexOf('.');
      if (dot < 3) continue;
      String sk = d.substring(0, dot).replace("-", "");
      if (sk.matches("[a-z0-9]+") && labels.add(sk)) out.add(d);
    }
    return out;
  }

  record Rig(NgramEmbedder embedder, VexIndex index, BrandCatalog catalog, LookalikeDetector detector) {}

  /** Brands at index < protectedCount get full rules; the rest are long-tail (exact-skeleton only). */
  static Rig rig(List<String> brandDomains, int protectedCount) {
    NgramEmbedder emb = new NgramEmbedder();
    VexIndex index = new VexIndex(emb);
    BrandCatalog catalog = new BrandCatalog(index);
    for (int i = 0; i < brandDomains.size(); i++) catalog.register(brandDomains.get(i), i < protectedCount);
    return new Rig(emb, index, catalog, new LookalikeDetector(index, catalog));
  }
}
