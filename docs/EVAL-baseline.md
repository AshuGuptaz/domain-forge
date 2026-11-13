# Lookalike detector evaluation

Generated 2026-11-14 by `LookalikeEvalTest` (`mvn test -Dtest=LookalikeEvalTest -Dforge.eval=true`).

**Protected brand index:** 8917 brands in Vex (136 hand-curated + Tranco top 10000).

**Attack set:** 1283 synthetic domains generated from the hand-curated brands across 12 attack types (dnstwist-style).
**Benign set A:** 5000 real domains sampled from Tranco ranks 10,001-60,000.
**Benign set B:** 3000 product-style generated names (word + affix + TLD).

Flag threshold: *flagged* = risk >= 0.5 (MEDIUM or HIGH); *high* = risk >= 0.8.

## Recall by attack type

| Attack type | n | flagged | high | recall | correct brand named |
|---|---:|---:|---:|---:|---:|
| brand+known-keyword | 218 | 218 | 218 | 100.0% | 72.0% |
| brand+unseen-keyword | 109 | 109 | 109 | 100.0% | 99.1% |
| digit-swap | 66 | 66 | 66 | 100.0% | 98.5% |
| hyphen-split | 109 | 109 | 28 | 100.0% | 62.4% |
| insertion | 109 | 109 | 109 | 100.0% | 99.1% |
| key-substitution | 109 | 109 | 109 | 100.0% | 98.2% |
| omission | 109 | 109 | 109 | 100.0% | 97.2% |
| repetition | 109 | 109 | 109 | 100.0% | 99.1% |
| rn-for-m | 26 | 26 | 26 | 100.0% | 96.2% |
| tld-swap | 108 | 108 | 11 | 100.0% | 74.1% |
| transposition | 109 | 108 | 108 | 99.1% | 97.2% |
| unicode-homoglyph | 102 | 102 | 102 | 100.0% | 99.0% |
| **all** | 1283 | 1282 | 1104 | **99.9%** | 88.8% |

## False positives

| Benign set | n | flagged (>=0.5) | FPR | high (>=0.8) | FPR(high) |
|---|---:|---:|---:|---:|---:|
| A: real Tranco 10k-60k | 5000 | 1068 | 21.36% | 374 | 7.48% |
| B: product-style names | 3000 | 249 | 8.30% | 42 | 1.40% |

Illustrative precision at a 1:1 attack:benign mix (set A): **82.4%**.

## Error analysis

### Sample of missed attacks

```
gamil.com  (transposition of gmail.com)
```

### Sample of false positives on real domains

```
simplypos.de                     risk 0.60  ~ simply.com  (contains the brand name)
personio-dev.com                 risk 0.95  ~ personio.com  (visual lookalike of the brand (homoglyph / obfuscation))
visa.de                          risk 0.55  ~ visa.com  (brand name registered on a different domain (visa.de))
canon-europe.com                 risk 0.95  ~ canon.com  (visual lookalike of the brand (homoglyph / obfuscation))
emailverification.info           risk 0.85  ~ gmail.com  (one-character typo of the brand)
windowscentral.com               risk 0.60  ~ windows.net  (contains the brand name)
powerappsportals.com             risk 0.85  ~ powerapps.com  (one-character typo of the brand)
viralan.space                    risk 0.60  ~ viralkand.com  (two-character typo of a long brand)
cgbchina.com.cn                  risk 0.85  ~ cmbchina.com  (one-character typo of the brand)
lmcdn.ru                         risk 0.85  ~ dmcdn.net  (one-character typo of the brand)
meraki.direct                    risk 0.55  ~ meraki.com  (brand name registered on a different domain (meraki.direct))
google.nu                        risk 0.55  ~ google.com  (brand name registered on a different domain (google.nu))
win-blog.com                     risk 0.95  ~ blog.google  (visual lookalike of the brand (homoglyph / obfuscation))
geocities.ws                     risk 0.85  ~ neocities.org  (one-character typo of the brand)
simplyrecipes.com                risk 0.60  ~ simply.com  (contains the brand name)
homedepot.com.mx                 risk 0.55  ~ homedepot.com  (brand name registered on a different domain (homedepot.com.mx))
unt.se                           risk 0.55  ~ unt.edu  (brand name registered on a different domain (unt.se))
alertmedia.com                   risk 0.85  ~ imedia.cz  (one-character typo of the brand)
cyberimpact.com                  risk 0.60  ~ impact.com  (contains the brand name)
expedia.co.uk                    risk 0.55  ~ expedia.com  (brand name registered on a different domain (expedia.co.uk))
vivor.net                        risk 0.85  ~ vivox.com  (one-character typo of the brand)
mangalib.me                      risk 0.85  ~ mangalik.net  (one-character typo of the brand)
unisg.ch                         risk 0.85  ~ unisa.ac.za  (one-character typo of the brand)
myd-cdn.com                      risk 0.85  ~ mycdn.me  (one-character typo of the brand)
10times.com                      risk 0.85  ~ ibtimes.com  (one-character typo of the brand)
```
