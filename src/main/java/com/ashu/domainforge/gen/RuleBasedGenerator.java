package com.ashu.domainforge.gen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Offline generator: affixes, blends, evocative-word combos and invented endings. */
@Component
public class RuleBasedGenerator implements NameGenerator {

  private static final String[] PREFIX = {"get", "try", "go", "hey", "the", "my", "meet", "join", "use", "hello"};
  private static final String[] SUFFIX = {"hub", "labs", "hq", "box", "base", "works", "nest", "ly", "ify", "ful", "kit", "co", "spot", "stack"};
  private static final String[] ENDING = {"ora", "ivo", "eo", "ario", "ena", "ia", "ex"};

  private final Lexicon lexicon;

  public RuleBasedGenerator(Lexicon lexicon) {
    this.lexicon = lexicon;
  }

  @Override
  public String name() {
    return "rules";
  }

  @Override
  public List<String> generate(String description, List<String> keywords, int limit) {
    return generate(description, keywords, List.of(), limit);
  }

  /** {@code extraWords} are semantically related words found by vector search (may be empty). */
  public List<String> generate(String description, List<String> keywords, List<String> extraWords, int limit) {
    Set<String> out = new LinkedHashSet<>();
    List<String> core = new ArrayList<>(keywords.size() > 4 ? keywords.subList(0, 4) : keywords);
    List<String> evocative = new ArrayList<>();
    for (String k : core) evocative.addAll(lexicon.relatedTo(k));
    evocative.addAll(extraWords);

    for (String k : core) {
      out.add(k);
      for (String p : PREFIX) out.add(p + k);
      for (String s : SUFFIX) out.add(join(k, s));
      out.add(trimVowel(k) + "ly");
      out.add(trimVowel(k) + "ify");
      for (String e : ENDING) out.add(trimVowel(k) + e);
    }
    for (String e : evocative) {
      out.add(e);
      for (String k : core) {
        out.add(join(e, k));
        out.add(join(k, e));
        out.add(blend(k, e));
      }
      for (String s : SUFFIX) out.add(join(e, s));
      for (String p : PREFIX) out.add(p + e);
    }
    for (int i = 0; i < core.size(); i++) {
      for (int j = 0; j < core.size(); j++) {
        if (i != j) {
          out.add(join(core.get(i), core.get(j)));
          out.add(blend(core.get(i), core.get(j)));
        }
      }
    }
    List<String> result = new ArrayList<>();
    for (String s : out) {
      if (s != null && s.length() >= 3) result.add(s);
      if (result.size() >= limit) break;
    }
    return result;
  }

  /** Joins two words, collapsing a doubled letter at the seam (brew + water -> brewater is avoided). */
  static String join(String a, String b) {
    if (a.charAt(a.length() - 1) == b.charAt(0)) return a + b.substring(1);
    return a + b;
  }

  /** Portmanteau: front of a + back of b, e.g. coffee + roast -> coffroast / cofroast. */
  static String blend(String a, String b) {
    int ca = Math.max(3, (a.length() + 1) / 2);
    int cb = Math.max(2, b.length() / 2);
    if (ca >= a.length() || cb >= b.length()) return null;
    return a.substring(0, ca) + b.substring(b.length() - cb);
  }

  static String trimVowel(String s) {
    char c = s.charAt(s.length() - 1);
    return "aeiou".indexOf(c) >= 0 && s.length() > 3 ? s.substring(0, s.length() - 1) : s;
  }
}
