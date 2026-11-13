package com.ashu.domainforge.config;

import com.ashu.domainforge.embed.MiniLmEmbedder;
import com.ashu.domainforge.embed.NgramEmbedder;
import com.ashu.domainforge.index.VexIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Two Vex HNSW indexes: spelling space for lookalikes, meaning space for name ideas. */
@Configuration
public class IndexConfig {

  @Bean
  VexIndex brandIndex(NgramEmbedder ngram) {
    return new VexIndex(ngram);
  }

  @Bean
  VexIndex wordIndex(MiniLmEmbedder minilm) {
    return new VexIndex(minilm);
  }
}
