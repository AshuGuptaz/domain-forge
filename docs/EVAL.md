# Lookalike detector evaluation

Generated 2026-11-14 by `LookalikeEvalTest` (`mvn test -Dtest=LookalikeEvalTest -Dforge.eval=true`).

**Brand index:** 8917 brands in Vex (136 hand-curated + Tranco top 10000). Full rules apply to the curated brands + Tranco top 1000; the long tail is flagged only on exact homoglyph match.

**Attack set:** 1283 synthetic domains generated from the hand-curated brands across 12 attack types (dnstwist-style).
**Benign set A:** 5000 real domains sampled from Tranco ranks 10,001-60,000.
**Benign set B:** 3000 product-style generated names (word + affix + TLD).

Flag threshold: *flagged* = risk >= 0.5 (MEDIUM or HIGH); *high* = risk >= 0.8.

## Recall by attack type

| Attack type | n | flagged | high | recall | correct brand named |
|---|---:|---:|---:|---:|---:|
| brand+known-keyword | 218 | 218 | 218 | 100.0% | 99.1% |
| brand+unseen-keyword | 109 | 109 | 0 | 100.0% | 99.1% |
| digit-swap | 66 | 66 | 66 | 100.0% | 98.5% |
| hyphen-split | 109 | 109 | 4 | 100.0% | 90.8% |
| insertion | 109 | 109 | 109 | 100.0% | 99.1% |
| key-substitution | 109 | 109 | 109 | 100.0% | 99.1% |
| omission | 109 | 109 | 109 | 100.0% | 98.2% |
| repetition | 109 | 109 | 109 | 100.0% | 99.1% |
| rn-for-m | 26 | 26 | 26 | 100.0% | 96.2% |
| tld-swap | 108 | 75 | 4 | 69.4% | 86.7% |
| transposition | 109 | 108 | 108 | 99.1% | 98.1% |
| unicode-homoglyph | 102 | 102 | 102 | 100.0% | 99.0% |
| **all** | 1283 | 1249 | 964 | **97.3%** | 97.4% |

## False positives

| Benign set | n | flagged (>=0.5) | FPR | high (>=0.8) | FPR(high) |
|---|---:|---:|---:|---:|---:|
| A: real Tranco 10k-60k | 5000 | 121 | 2.42% | 50 | 1.00% |
| B: product-style names | 3000 | 35 | 1.17% | 12 | 0.40% |

Illustrative precision at a 1:1 attack:benign mix (set A): **97.6%**.

## Error analysis

### Sample of missed attacks

```
google.net  (tld-swap of google.com)
youtube.co  (tld-swap of youtube.com)
gamil.com  (transposition of gmail.com)
instagram.net  (tld-swap of instagram.com)
whatsapp.net  (tld-swap of whatsapp.com)
amazon.net  (tld-swap of amazon.com)
stackoverflow.net  (tld-swap of stackoverflow.com)
salesforce.net  (tld-swap of salesforce.com)
intel.net  (tld-swap of intel.com)
wordpress.co  (tld-swap of wordpress.com)
americanexpress.net  (tld-swap of americanexpress.com)
wellsfargo.co  (tld-swap of wellsfargo.com)
hdfcbank.net  (tld-swap of hdfcbank.com)
flipkart.co  (tld-swap of flipkart.com)
swiggy.net  (tld-swap of swiggy.com)
airbnb.co  (tld-swap of airbnb.com)
walmart.co  (tld-swap of walmart.com)
target.net  (tld-swap of target.com)
discord.net  (tld-swap of discord.com)
pinterest.net  (tld-swap of pinterest.com)
tumblr.co  (tld-swap of tumblr.com)
medium.co  (tld-swap of medium.com)
notion.net  (tld-swap of notion.so)
figma.co  (tld-swap of figma.com)
trello.net  (tld-swap of trello.com)
```

### Sample of false positives on real domains

```
alertmedia.com                   risk 0.85  ~ media.net  (brand name combined with a phishing keyword)
10times.com                      risk 0.85  ~ latimes.com  (one-character typo of the brand)
cloudflare.bz                    risk 0.60  ~ cloudflare.com  (two-character typo of a long brand)
netcraze.link                    risk 0.55  ~ netcraze.io  (brand name on a suspicious TLD (.link))
ykimg.com                        risk 0.85  ~ ytimg.com  (one-character typo of the brand)
academia-assets.com              risk 0.50  ~ academia.edu  (contains the brand name)
my-salesforce-scrt.com           risk 0.50  ~ salesforce.com  (contains the brand name)
samsung-gamelauncher.com         risk 0.50  ~ samsung.com  (brand name as one part of a hyphenated domain)
salesforcescrt.com               risk 0.50  ~ salesforce.com  (contains the brand name)
angular.dev                      risk 0.60  ~ singular.net  (two-character typo of a long brand)
h1cloud.net                      risk 0.95  ~ hicloud.com  (visual lookalike of the brand (homoglyph / obfuscation))
vercel-storage.com               risk 0.50  ~ vercel.app  (brand name as one part of a hyphenated domain)
teams-dc-msedge.net              risk 0.50  ~ msedge.net  (brand name as one part of a hyphenated domain)
autoweek.nl                      risk 0.60  ~ autodesk.com  (two-character typo of a long brand)
ipcenter.ru                      risk 0.60  ~ appcenter.ms  (two-character typo of a long brand)
dzone.com                        risk 0.85  ~ ozone.ru  (one-character typo of the brand)
ebay-kleinanzeigen.de            risk 0.50  ~ kleinanzeigen.de  (contains the brand name)
yandexcloud.kz                   risk 0.60  ~ yandexcloud.net  (two-character typo of a long brand)
media-server.com                 risk 0.50  ~ media.net  (brand name as one part of a hyphenated domain)
chatapp.online                   risk 0.60  ~ whatsapp.com  (two-character typo of a long brand)
tattle.life                      risk 0.85  ~ battle.net  (one-character typo of the brand)
envato-static.com                risk 0.50  ~ static.microsoft  (brand name as one part of a hyphenated domain)
nethinks.com                     risk 0.60  ~ nexthink.cloud  (two-character typo of a long brand)
google.ml                        risk 0.55  ~ google.com  (brand name on a suspicious TLD (.ml))
ktcdn.co.kr                      risk 0.85  ~ kwcdn.com  (one-character typo of the brand)
```
