# Changelog

## 1.1.0

- License changed to the Hoverfly Community License: free for products with up to 10,000 monthly
  active devices per platform, commercial license above that. No code or model changes.
- 1.0.0 and earlier stay under Apache-2.0.

## 1.0.0

- First version: `Gatekeeper(context).check(text, sensitivity)` → `Verdict(isToxic, score, categories)`.
- Detects toxic messages (abuse, hate, threats, sexual harassment) in English, Hindi, Tamil, Telugu, Malayalam,
  Kannada, romanised Hinglish / Tanglish and about 15 other languages. Five category hints.
- `Sensitivity.STRICT`, `BALANCED`, `RELAXED`, or your own threshold.
- Pure Kotlin inference with no dependencies, int8 weights (3.7 MB). minSdk 21.
