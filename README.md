<div align="center">

# 🪐 Kosmix

### A YouTube-style desktop client, built to push **skia-fx** to its limits.

**Search · stream · scrub · download — all GPU-rendered through Skia.**

`Java 25` · `Gradle 9` · `skia-fx (Skia-powered OpenJFX 25)` · `ytnfx` · `nfx-listview` · `Windows x64`

</div>

---

> ## ⚠️ READ THIS FIRST — DEMO / SHOWCASE ONLY
>
> **Kosmix is a technology demo. It is NOT a product, NOT stable, and NOT supported.**
>
> It exists for one reason: to **stress-test and show off the [skia-fx](https://github.com/xdsswar/skiafx) SDK** —
> a Skia-powered fork of OpenJFX — with a real, demanding application (custom window
> chrome, GPU video, dual-source media, vector SVG, a virtualized grid, live network
> data). Expect bugs, crashes, rough edges, half-finished corners, and breaking
> changes. Nothing here is hardened, audited, or meant for daily use.
>
> ### 🚫 Do NOT use Kosmix for piracy, redistribution, or any malicious purpose.
>
> - Kosmix is **not affiliated with, endorsed by, or sponsored by YouTube or Google.**
> - All content accessed through it belongs to its respective owners and is governed by
>   **YouTube's Terms of Service** — which generally **prohibit downloading**.
> - The download feature exists to **demonstrate the SDK's media-muxing pipeline**, not to
>   enable infringement. **You are solely responsible** for how you use it. Only download
>   content you own, content that is public-domain/Creative-Commons, or content you have
>   explicit permission to save.
> - Don't redistribute downloaded content. Don't use this to rip, mirror, scrape at scale,
>   or circumvent anything. **If your intent is piracy, you are using the wrong tool — stop.**
>
> By building or running Kosmix you accept that it is provided **"AS IS", with no warranty
> of any kind**, and that you alone are responsible for complying with all applicable laws
> and third-party terms. See [`innosetup/License.txt`](innosetup/License.txt).

---

## ✨ What it does

Kosmix looks and feels like the YouTube web app — light theme, Roboto, rounded cards —
but every pixel is painted by **Skia on the GPU** through the skia-fx pipeline.

- 🔎 **Search** YouTube with live suggestions, infinite-scroll results, and a virtualized
  lazy grid (recycled cells, cached thumbnails).
- ▶️ **Watch** with a real player: a **dual-source media engine** that plays a separate
  video-only and audio-only stream as one synchronized timeline (hardware-decoded,
  zero-copy to the GPU), an auto-hiding control bar, a volume slider, a seek-time bubble,
  and an **in-player quality menu** (Auto picks a resolution from a live network probe).
- 🧭 **Browse** like a browser — back/forward history, a responsive watch page that moves
  the related list below the description when the window is narrow, and true edge-to-edge
  fullscreen.
- ⬇️ **Download** (for the formats you're entitled to) — pick a quality, and Kosmix pulls
  the best video + audio streams and **muxes them into a single MP4** off the UI thread,
  with a Chrome-style downloads history in the title bar and a folder button to reveal the
  file.
- 🎛️ **Configure** extraction client / User-Agent, default quality, decode strategy
  (CPU/GPU/Auto), download folder, and concurrency limits.

## 🧱 Built on

| Piece | Role |
|---|---|
| [**skia-fx**](https://github.com/xdsswar/skiafx) | Skia-powered OpenJFX 25 fork — the whole point. Custom primary `Stage`, dual-source `Media`, `MediaMixer`, vector `SvgImageView`, GPU pipeline. |
| **ytnfx** | YouTube data extraction (search, details, streams, storyboards) — runs the signature solver in-process via GraalVM JS. |
| **nfx-listview** | Virtualized, recycling list/grid control for the results. |
| **Roboto** | UI typeface (bundled, loaded at runtime). |

## 🏗️ Architecture (the short version)

A single `Context` interface is the spine — implemented by the `Launcher`
(`Application<Window>`), it hands out the executor, settings, window, and the YouTube /
download services. A `Skeleton` owns the custom chrome and cross-fades between `Provider`
views (home grid, player, settings). Everything is **widget-oriented, built in code — no
FXML** — and heavily commented. Blocking ytnfx calls run on a virtual-thread pool and hop
results back to the FX thread.

## 🚀 Build & run

**Requirements:** Java **25**, Windows **x64**. (skia-fx is Windows-only for now.)

```bash
gradlew :kosmix:run                # run it
gradlew build                      # compile everything
gradlew :kosmix:jlink              # custom runtime image
gradlew :kosmix:jpackageImage      # native app-image
gradlew :kosmix:prepareInnoSetup   # stage the app-image for the Inno Setup installer
```

On first run Kosmix downloads a pinned **ffmpeg** runtime into
`%APPDATA%\.kosmix\ffmpeg` (needed for WebM/Opus playback and muxing). Settings live in
`%APPDATA%\.kosmix\settings.cfg`.

## 🧪 Known rough edges (it's a demo, remember?)

- **Windows x64 only** — that's all skia-fx targets today.
- **No WebP decode** in the SDK's image pipeline, so animated hover thumbnails and
  storyboard scrub-frames are disabled (YouTube serves both as WebP). Static thumbnails
  work fine.
- "Related videos" is approximated with a title-seeded search — ytnfx exposes no
  watch-next feed.
- Things will break. That is expected and, honestly, the point.

## 📜 License & credits

Provided **as-is, no warranty**, for evaluation only — see
[`innosetup/License.txt`](innosetup/License.txt). Built on the shoulders of the
**OpenJFX**, **Skia**, **GraalVM**, and **ytnfx** projects, and the broader JavaFX
community.

<div align="center">

**Kosmix** — *not a product. A playground for what Skia + JavaFX can do.*

Use it to learn. Don't use it to steal.

</div>
