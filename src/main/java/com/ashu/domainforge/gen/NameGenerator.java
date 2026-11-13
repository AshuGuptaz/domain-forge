package com.ashu.domainforge.gen;

import java.util.List;

public interface NameGenerator {
  String name();

  /** Returns raw candidate labels (no TLD) for a business description. */
  List<String> generate(String description, List<String> keywords, int limit);
}
