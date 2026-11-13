package com.ashu.domainforge.embed;

public interface Embedder {
  int dimension();

  float[] embed(String text);
}
