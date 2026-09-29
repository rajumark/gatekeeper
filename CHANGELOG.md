# Changelog

## 2.0.0

- Kotlin Multiplatform: Android, JVM desktop, iOS (arm64 device + simulator), macOS arm64,
  JavaScript and WebAssembly, published to Maven Central as `io.github.rajumark:gatekeeper`.
- New constructor `Gatekeeper()`: the model ships inside the library on every platform, so no
  `Context` is needed. `Gatekeeper(context)` still compiles on Android (deprecated).
- Same model and same results as 1.x; parity with the reference (140 vectors) is tested on every target.
- The sample is now a Compose Multiplatform app (Android, desktop, iOS) plus a web page (JS and Wasm).

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
