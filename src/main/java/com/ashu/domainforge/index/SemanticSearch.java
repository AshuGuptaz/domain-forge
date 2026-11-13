package com.ashu.domainforge.index;

import com.ashu.domainforge.embed.MiniLmEmbedder;
import com.ashu.domainforge.index.VexIndex.Neighbor;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Meaning-based word search. A vocabulary of brandable words is embedded with MiniLM and stored in
 * a Vex HNSW index; a business description is embedded and its nearest words become name material
 * ("coffee shop" -> brew, roast, espresso, mocha ...). Loads in the background so startup is fast.
 */
@Component
public class SemanticSearch {
  private static final Logger log = LoggerFactory.getLogger(SemanticSearch.class);

  private final VexIndex wordIndex;
  private final MiniLmEmbedder embedder;
  private volatile boolean ready;

  public SemanticSearch(@Qualifier("wordIndex") VexIndex wordIndex, MiniLmEmbedder embedder) {
    this.wordIndex = wordIndex;
    this.embedder = embedder;
  }

  @PostConstruct
  void startLoading() {
    Thread t = new Thread(this::load, "semantic-loader");
    t.setDaemon(true);
    t.start();
  }

  /** Blocking load; public so tests can run it synchronously. */
  public synchronized void load() {
    if (ready) return;
    try {
      if (!embedder.init()) return;
      long t0 = System.currentTimeMillis();
      try (BufferedReader r = new BufferedReader(new InputStreamReader(
          new ClassPathResource("data/words.txt").getInputStream(), StandardCharsets.UTF_8))) {
        String w;
        while ((w = r.readLine()) != null) {
          if (!w.isBlank()) wordIndex.add(w.trim());
        }
      }
      ready = true;
      log.info("Semantic index ready: {} words embedded + indexed in Vex in {} ms", wordIndex.size(), System.currentTimeMillis() - t0);
    } catch (Exception e) {
      log.warn("Semantic index failed to load: {}", e.toString());
    }
  }

  public boolean ready() {
    return ready;
  }

  public int size() {
    return wordIndex.size();
  }

  /** Nearest vocabulary words to the description by meaning, most similar first. */
  public List<Neighbor> relatedWords(String description, int k) {
    return ready ? wordIndex.nearest(description, k) : List.of();
  }

  /** Cosine similarity (meaning) between two strings, in [-1, 1]. */
  public double similarity(String a, String b) {
    float[] x = embedder.embed(a);
    float[] y = embedder.embed(b);
    double d = 0;
    for (int i = 0; i < x.length; i++) d += x[i] * y[i];
    return d;
  }
}
