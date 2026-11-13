package com.ashu.domainforge.risk;

import com.ashu.domainforge.index.VexIndex;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Loads protected brands at startup and indexes their skeletons into Vex. */
@Component
public class BrandCatalog {
  private static final Logger log = LoggerFactory.getLogger(BrandCatalog.class);

  private final VexIndex index;
  private final Set<String> officialDomains = new HashSet<>();
  private final Map<String, List<String>> domainsBySkeleton = new HashMap<>();

  public BrandCatalog(@Qualifier("brandIndex") VexIndex index) {
    this.index = index;
  }

  @PostConstruct
  void load() throws Exception {
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(new ClassPathResource("data/brands.txt").getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        line = line.trim().toLowerCase();
        if (line.isEmpty() || line.startsWith("#")) continue;
        register(line);
      }
    }
    log.info("Indexed {} brand skeletons into Vex HNSW", index.size());
  }

  public void register(String officialDomain) {
    officialDomains.add(officialDomain);
    String label = officialDomain.substring(0, officialDomain.indexOf('.'));
    String skeleton = label.replace("-", "");
    List<String> list = domainsBySkeleton.computeIfAbsent(skeleton, k -> new ArrayList<>());
    if (list.isEmpty()) index.add(skeleton);
    list.add(officialDomain);
  }

  public boolean isOfficial(String domain) {
    return officialDomains.contains(domain);
  }

  public List<String> officialDomainsFor(String skeleton) {
    return domainsBySkeleton.getOrDefault(skeleton, List.of());
  }

  public boolean knows(String skeleton) {
    return domainsBySkeleton.containsKey(skeleton);
  }

  public int size() {
    return domainsBySkeleton.size();
  }
}
