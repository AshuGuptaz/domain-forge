package com.ashu.domainforge.eval;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ashu.domainforge.embed.NgramEmbedder;
import com.ashu.domainforge.eval.TyposquatGenerator.Sample;
import com.vex.core.CosineDistance;
import com.vex.core.HnswConfig;
import com.vex.core.HnswIndex;
import com.vex.core.SearchResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Vex HNSW vs exact brute-force search over real domain-name vectors at increasing index sizes.
 * Opt-in: mvn test -Dtest=ScaleBenchmarkTest -Dforge.eval=true -DargLine="-Xmx3g -Dforge.eval=true"
 */
class ScaleBenchmarkTest {

  static final int[] SIZES = {10_000, 50_000, 100_000, 200_000};
  static final int K = 10;
  static final int EF = 64;

  record Row(int n, double buildSec, double hnswP50, double hnswP99, double brutP50, double brutP99, double recall) {}

  @Test
  void run() throws Exception {
    assumeTrue(EvalSupport.enabled(), "evaluation disabled");
    NgramEmbedder emb = new NgramEmbedder();
    int max = SIZES[SIZES.length - 1];

    // unique labels from Tranco (rank order); queries = typosquats of the top brands
    Set<String> labels = new LinkedHashSet<>();
    for (String d : EvalSupport.tranco(1, 600_000)) {
      int dot = d.indexOf('.');
      if (dot < 3) continue;
      String sk = d.substring(0, dot).replace("-", "");
      if (sk.matches("[a-z0-9]+")) labels.add(sk);
      if (labels.size() >= max) break;
    }
    List<String> all = new ArrayList<>(labels);
    List<String> queries = new ArrayList<>();
    for (Sample s : TyposquatGenerator.generate(EvalSupport.curatedBrands(), 5)) {
      String l = s.domain().substring(0, s.domain().indexOf('.')).replace("-", "");
      if (l.matches("[a-z0-9]+")) queries.add(l);
      if (queries.size() >= 400) break;
    }
    float[][] qv = new float[queries.size()][];
    for (int i = 0; i < qv.length; i++) qv[i] = emb.embed(queries.get(i));

    float[][] vecs = new float[all.size()][];
    for (int i = 0; i < vecs.length; i++) vecs[i] = emb.embed(all.get(i));

    List<Row> rows = new ArrayList<>();
    for (int size : SIZES) {
      if (size > vecs.length) break;
      HnswIndex idx = new HnswIndex(new HnswConfig(16, 100, EF, NgramEmbedder.DIM, new CosineDistance(), 42L));
      long t0 = System.nanoTime();
      for (int i = 0; i < size; i++) idx.add(i, vecs[i]);
      double build = (System.nanoTime() - t0) / 1e9;

      // warm-up
      for (int i = 0; i < 50; i++) { idx.query(qv[i % qv.length], K, EF); exact(vecs, size, qv[i % qv.length]); }

      long[] h = new long[qv.length];
      long[] b = new long[qv.length];
      double hit = 0;
      for (int i = 0; i < qv.length; i++) {
        long a = System.nanoTime();
        List<SearchResult> approx = idx.query(qv[i], K, EF);
        h[i] = System.nanoTime() - a;
        a = System.nanoTime();
        Set<Integer> truth = exact(vecs, size, qv[i]);
        b[i] = System.nanoTime() - a;
        Set<Integer> got = new HashSet<>();
        for (SearchResult r : approx) got.add((int) r.id());
        int inter = 0;
        for (int t : truth) if (got.contains(t)) inter++;
        hit += (double) inter / K;
      }
      Arrays.sort(h);
      Arrays.sort(b);
      Row row = new Row(size, build, pct(h, 0.5), pct(h, 0.99), pct(b, 0.5), pct(b, 0.99), hit / qv.length);
      rows.add(row);
      System.out.printf("n=%d build=%.1fs hnsw p50=%.0fus p99=%.0fus | brute p50=%.0fus p99=%.0fus | recall@10=%.3f%n",
          size, row.buildSec, row.hnswP50, row.hnswP99, row.brutP50, row.brutP99, row.recall);
      idx.close();
    }
    write(rows, queries.size());
  }

  /** Exact top-K by dot product (vectors are unit length, so this is cosine). */
  static Set<Integer> exact(float[][] vecs, int n, float[] q) {
    int[] ids = new int[K];
    float[] sc = new float[K];
    Arrays.fill(sc, -Float.MAX_VALUE);
    for (int i = 0; i < n; i++) {
      float d = 0;
      float[] v = vecs[i];
      for (int j = 0; j < v.length; j++) d += v[j] * q[j];
      if (d > sc[K - 1]) {
        int p = K - 1;
        while (p > 0 && sc[p - 1] < d) { sc[p] = sc[p - 1]; ids[p] = ids[p - 1]; p--; }
        sc[p] = d;
        ids[p] = i;
      }
    }
    Set<Integer> out = new HashSet<>();
    for (int id : ids) out.add(id);
    return out;
  }

  static double pct(long[] sorted, double p) {
    return sorted[Math.min(sorted.length - 1, (int) (sorted.length * p))] / 1000.0;
  }

  static void write(List<Row> rows, int nq) throws Exception {
    StringBuilder md = new StringBuilder();
    md.append("# Scale benchmark: Vex HNSW vs brute force\n\n");
    md.append("Generated ").append(LocalDate.now()).append(" by `ScaleBenchmarkTest`. Real domain labels from the Tranco list, 256-dim character n-gram vectors (cosine), ")
        .append(nq).append(" typosquat queries, k=10. HNSW: M=16, efConstruction=100, efSearch=").append(EF).append(". Single thread, exact baseline is a straight scan with a top-k insertion buffer.\n\n");
    md.append("| Vectors | HNSW build | HNSW p50 | HNSW p99 | Brute p50 | Brute p99 | Speed-up (p50) | Recall@10 |\n|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (Row r : rows) {
      md.append(String.format("| %,d | %.1f s | %.0f us | %.0f us | %.0f us | %.0f us | **%.0fx** | %.3f |%n",
          r.n, r.buildSec, r.hnswP50, r.hnswP99, r.brutP50, r.brutP99, r.brutP50 / r.hnswP50, r.recall));
    }
    md.append("\n![latency](scale.svg)\n");
    Files.createDirectories(Path.of("docs"));
    Files.writeString(Path.of("docs/SCALE.md"), md.toString());
    Files.writeString(Path.of("docs/scale.svg"), svg(rows));
  }

  /** Dependency-free line chart: p50 query latency (us) vs index size. */
  static String svg(List<Row> rows) {
    int w = 640, h = 340, l = 70, r = 20, t = 30, b = 50;
    double maxY = 0;
    for (Row x : rows) maxY = Math.max(maxY, Math.max(x.brutP50, x.hnswP50));
    maxY = Math.ceil(maxY / 100.0) * 100.0;
    double maxX = rows.get(rows.size() - 1).n;
    StringBuilder s = new StringBuilder();
    s.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 %d %d' font-family='system-ui,sans-serif' font-size='12'>", w, h));
    s.append(String.format("<rect width='%d' height='%d' fill='white'/>", w, h));
    s.append(String.format("<text x='%d' y='18' font-weight='bold' font-size='14'>p50 query latency vs index size (lower is better)</text>", l));
    for (int i = 0; i <= 4; i++) {
      double y = t + (h - t - b) * (1 - i / 4.0);
      s.append(String.format("<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke='#e5e7eb'/>", l, y, w - r, y));
      s.append(String.format("<text x='%d' y='%.1f' text-anchor='end' fill='#555'>%.0f us</text>", l - 6, y + 4, maxY * i / 4));
    }
    String[] colors = {"#d63a3a", "#0a7cc2"};
    for (int series = 0; series < 2; series++) {
      StringBuilder pts = new StringBuilder();
      for (Row x : rows) {
        double px = l + (w - l - r) * (x.n / maxX);
        double v = series == 0 ? x.brutP50 : x.hnswP50;
        double py = t + (h - t - b) * (1 - v / maxY);
        pts.append(String.format("%.1f,%.1f ", px, py));
        s.append(String.format("<circle cx='%.1f' cy='%.1f' r='3.5' fill='%s'/>", px, py, colors[series]));
      }
      s.append(String.format("<polyline points='%s' fill='none' stroke='%s' stroke-width='2.5'/>", pts, colors[series]));
    }
    for (Row x : rows) {
      double px = l + (w - l - r) * (x.n / maxX);
      s.append(String.format("<text x='%.1f' y='%d' text-anchor='middle' fill='#555'>%,dk</text>", px, h - b + 18, x.n / 1000));
    }
    s.append(String.format("<text x='%d' y='%d' text-anchor='middle' fill='#555'>vectors indexed</text>", (l + w - r) / 2, h - 10));
    s.append(String.format("<rect x='%d' y='%d' width='12' height='3' fill='%s'/><text x='%d' y='%d'>brute-force scan</text>", w - 200, 40, colors[0], w - 182, 44));
    s.append(String.format("<rect x='%d' y='%d' width='12' height='3' fill='%s'/><text x='%d' y='%d'>Vex HNSW (ef=%d)</text>", w - 200, 58, colors[1], w - 182, 62, EF));
    return s.append("</svg>").toString();
  }
}
