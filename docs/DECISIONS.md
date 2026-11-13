# Design decisions and what the evaluation changed

These are the decisions that were driven by measurement rather than taste. Numbers are from `docs/EVAL.md` and `docs/SCALE.md`.

## 1. Two embedding spaces, not one
Lookalike detection needs *spelling* similarity ("paypa1" ~ "paypal"); name ideas need *meaning* similarity ("coffee" ~ "brew"). A single model can't serve both well, so there are two Vex indexes: hashed character n-grams (256-d) and MiniLM (384-d).

## 2. Brand tiers (cut false positives 21.4% -> ~2.4%)
First version treated every indexed brand equally. With 10,000 Tranco domains as brands, **21.4%** of real legitimate domains were flagged (`docs/EVAL-baseline.md`): two unrelated popular sites one letter apart (`cgbchina` / `cmbchina`) looked like an attack.

Fix: only *prominent* brands (hand-curated + Tranco top 1,000) get typo / keyword / containment rules. The long tail is flagged only on an exact homoglyph match.

## 3. Severity depends on the TLD
`visa.de`, `expedia.co.uk`, `homedepot.com.mx` are legitimate regional domains. A brand name on a different TLD is now LOW risk, and MEDIUM only on TLDs commonly abused (`.xyz`, `.biz`, `.info`, `.online`, ...). Cost: recall on `.net` / `.co` swaps is 69%, a deliberate trade-off because those are often legitimate or defensive registrations.

## 4. Rules by form, not one rule for everything
Edit distance is applied only to the whole label. After stripping phishing keywords or splitting on hyphens, only *exact* matches count. Before this, `emailverification.info` matched `gmail` (strip "verification" -> `email` is one edit from `gmail`) and `canon-europe.com` was reported as a homoglyph attack.

## Rejected experiment
Raising the minimum brand length for typo/keyword rules from 5 to 6 letters cut false positives (2.4% -> 1.8%) but dropped overall recall from 97.3% to 87.5%, because famous short brands (apple, gmail) were no longer protected. Reverted.

## Known limitations (measured)
- Adjacent-letter transposition of a short brand can be missed (`gamil.com` vs `gmail.com`): the n-gram vector of a transposition differs a lot, so the ANN step does not retrieve the brand. Affects ~1 in 1,000 attacks in the test set.
- False-positive rate is on a *sample of real domains treated as benign*. Some are genuinely ambiguous (`google.ml`, `samsung-gamelauncher.com`), so the true rate is likely lower than reported.
- Attack samples are synthetic (dnstwist-style) generated from the same families the rules target, apart from the held-out keyword set. Real attackers are more varied; treat recall as an upper bound.
