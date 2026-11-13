package com.ashu.domainforge.index;

import com.ashu.domainforge.embed.Embedder;
import com.vex.core.CosineDistance;
import com.vex.core.HnswConfig;
import com.vex.core.HnswIndex;
import com.vex.core.SearchResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin adapter over the Vex HNSW index. Maps Vex's {@code long} ids to string labels and exposes
 * cosine <em>similarity</em> (1 - distance).
 */
public class VexIndex {

  public record Neighbor(String label, double similarity) {}

  private final HnswIndex index;
  private final Embedder embedder;
  private final List<String> labels = new ArrayList<>();

  public VexIndex(Embedder embedder) {
    this.embedder = embedder;
    this.index = new HnswIndex(new HnswConfig(16, 200, 64, embedder.dimension(), new CosineDistance(), 42L));
  }

  public synchronized void add(String label) {
    long id = labels.size();
    labels.add(label);
    index.add(id, embedder.embed(label));
  }

  public synchronized String labelOf(long id) {
    return labels.get((int) id);
  }

  public int size() {
    return index.size();
  }

  /** Approximate nearest neighbours of {@code text}, most similar first. */
  public List<Neighbor> nearest(String text, int k) {
    if (index.size() == 0) return List.of();
    List<SearchResult> raw = index.query(embedder.embed(text), Math.min(k, index.size()));
    List<Neighbor> out = new ArrayList<>(raw.size());
    for (SearchResult r : raw) {
      out.add(new Neighbor(labelOf(r.id()), 1.0 - r.distance()));
    }
    return out;
  }
}
