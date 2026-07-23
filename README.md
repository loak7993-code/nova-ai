# Nova AI

A privacy-focused Android AI chat client with streaming, tool calling, image vision, and web search.

## Features

- **Multi-model support** — GLM-5.2, MiniMax M3, Kimi K2.7 Code (W&B Inference API)
- **Streaming responses** with collapsible reasoning/thinking display
- **Tool calling** — built-in calculator, current time, and web search
- **Image vision** — attach images and ask questions (Kimi K2.7 Code, MiniMax M3)
- **Self-hosted web search** — SearXNG integration, no API key required
- **Markdown rendering** — tables, code blocks, lists, headings, inline formatting
- **Conversation management** — chat history, rename, delete, export
- **No tracking, no telemetry, no ads**

## Setup

1. Get a W&B Inference API key from [wandb.ai/settings](https://wandb.ai/settings)
2. Open Settings, enter your API key
3. (Optional) Set a SearXNG URL for web search — self-host with Docker:
   ```bash
   docker run -d --name searxng -p 8888:8080 searxng/searxng:latest
   ```
4. Start chatting

## Tech

- Java 17, Android SDK 35, minSdk 24
- OkHttp, Gson, Material Components
- No proprietary SDKs, no Google Play Services dependency
- All dependencies are FOSS (Apache 2.0)

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

Geist font is licensed under the SIL Open Font License. See [app/src/main/assets/fonts/OFL.txt](app/src/main/assets/fonts/OFL.txt).
