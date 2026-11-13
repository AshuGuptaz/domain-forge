package com.ashu.domainforge.embed;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.ashu.domainforge.config.DomainForgeProperties;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real neural sentence embeddings (all-MiniLM-L6-v2, 384-dim) executed in-process with ONNX Runtime.
 * No API key and no network at query time; the model files are downloaded once to a local cache.
 */
@Component
public class MiniLmEmbedder implements Embedder {
  private static final Logger log = LoggerFactory.getLogger(MiniLmEmbedder.class);
  public static final int DIM = 384;
  private static final String BASE = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/";

  private final DomainForgeProperties.Embedding cfg;
  private volatile OrtEnvironment env;
  private volatile OrtSession session;
  private volatile HuggingFaceTokenizer tokenizer;
  private volatile boolean ready;

  private final Map<String, float[]> cache = new LinkedHashMap<>(256, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, float[]> e) {
      return size() > 4000;
    }
  };

  public MiniLmEmbedder(DomainForgeProperties props) {
    this.cfg = props.getEmbedding();
  }

  @Override
  public int dimension() {
    return DIM;
  }

  public boolean ready() {
    return ready;
  }

  /** Downloads (first run only) and loads the model. Returns false if unavailable; callers degrade. */
  public synchronized boolean init() {
    if (ready) return true;
    if (!"auto".equalsIgnoreCase(cfg.getMode())) return false;
    try {
      Path dir = Path.of(cfg.getDir().replaceFirst("^~", System.getProperty("user.home")));
      Files.createDirectories(dir);
      Path model = dir.resolve("model.onnx");
      Path tok = dir.resolve("tokenizer.json");
      download(BASE + "onnx/model.onnx", model, 80_000_000L);
      download(BASE + "tokenizer.json", tok, 100_000L);
      tokenizer = HuggingFaceTokenizer.newInstance(tok, Map.of("truncation", "true", "maxLength", "64"));
      env = OrtEnvironment.getEnvironment();
      session = env.createSession(model.toString(), new OrtSession.SessionOptions());
      ready = true;
      log.info("MiniLM embedding model ready ({} dims)", DIM);
    } catch (Exception | UnsatisfiedLinkError e) {
      log.warn("MiniLM unavailable, semantic features disabled: {}", e.toString());
    }
    return ready;
  }

  private static void download(String url, Path target, long minBytes) throws Exception {
    if (Files.exists(target) && Files.size(target) >= minBytes) return;
    log.info("Downloading {} ...", url);
    Path tmp = target.resolveSibling(target.getFileName() + ".part");
    try (InputStream in = URI.create(url).toURL().openStream()) {
      Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
    }
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public float[] embed(String text) {
    if (!ready) throw new IllegalStateException("MiniLM not initialised");
    synchronized (cache) {
      float[] hit = cache.get(text);
      if (hit != null) return hit.clone();
    }
    float[] v = compute(text);
    synchronized (cache) {
      cache.put(text, v);
    }
    return v.clone();
  }

  private float[] compute(String text) {
    try {
      Encoding enc = tokenizer.encode(text);
      long[] ids = enc.getIds();
      long[] mask = enc.getAttentionMask();
      long[] types = enc.getTypeIds();
      try (OnnxTensor a = OnnxTensor.createTensor(env, new long[][] {ids});
          OnnxTensor b = OnnxTensor.createTensor(env, new long[][] {mask});
          OnnxTensor c = OnnxTensor.createTensor(env, new long[][] {types});
          OrtSession.Result out = session.run(Map.of("input_ids", a, "attention_mask", b, "token_type_ids", c))) {
        float[][] hidden = ((float[][][]) out.get(0).getValue())[0];
        float[] pooled = new float[DIM];
        double n = 0;
        for (int t = 0; t < hidden.length; t++) {
          if (mask[t] == 0) continue;
          n++;
          for (int d = 0; d < DIM; d++) pooled[d] += hidden[t][d];
        }
        double norm = 0;
        for (int d = 0; d < DIM; d++) {
          pooled[d] /= (float) Math.max(1, n);
          norm += pooled[d] * pooled[d];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) for (int d = 0; d < DIM; d++) pooled[d] /= (float) norm;
        return pooled;
      }
    } catch (Exception e) {
      throw new IllegalStateException("embedding failed", e);
    }
  }
}
