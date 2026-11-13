package com.ashu.domainforge.embed;

import org.springframework.stereotype.Component;

/**
 * Deterministic, dependency-free text embedder: hashed character 1-3 grams with signed buckets,
 * L2-normalised. It captures <em>orthographic</em> similarity (spelling), which is exactly what
 * typosquat / lookalike detection needs. It does not capture meaning; semantic expansion is done by
 * the lexicon in the generator instead.
 */
@Component
public class NgramEmbedder implements Embedder {

  public static final int DIM = 256;

  @Override
  public int dimension() {
    return DIM;
  }

  @Override
  public float[] embed(String text) {
    String s = "^" + normalise(text) + "$";
    float[] v = new float[DIM];
    for (int n = 1; n <= 3; n++) {
      float weight = n == 1 ? 0.5f : (n == 2 ? 1.0f : 1.5f);
      for (int i = 0; i + n <= s.length(); i++) {
        int h = fnv(s, i, n);
        int bucket = (h >>> 1) % DIM;
        v[bucket] += ((h & 1) == 0 ? 1f : -1f) * weight;
      }
    }
    double norm = 0;
    for (float x : v) norm += x * x;
    norm = Math.sqrt(norm);
    if (norm == 0) {
      v[0] = 1f;
      return v;
    }
    for (int i = 0; i < DIM; i++) v[i] /= (float) norm;
    return v;
  }

  private static String normalise(String t) {
    StringBuilder sb = new StringBuilder();
    for (char c : t.toLowerCase().toCharArray()) {
      if (Character.isLetterOrDigit(c)) sb.append(c);
    }
    return sb.toString();
  }

  private static int fnv(String s, int off, int len) {
    int h = 0x811c9dc5 ^ (len * 31);
    for (int i = off; i < off + len; i++) {
      h ^= s.charAt(i);
      h *= 0x01000193;
    }
    return h;
  }

  /** Cosine similarity of two unit vectors. */
  public static double cosine(float[] a, float[] b) {
    double d = 0;
    for (int i = 0; i < a.length; i++) d += a[i] * b[i];
    return d;
  }
}
