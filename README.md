# EVE-TTS

**EVE-TTS** is a lightweight, local text-to-speech desktop application for [EVE Online](https://www.eveonline.com/). It monitors your EVE Online chat logs in real time and synthesizes spoken voice alerts using local neural TTS engines (**Kokoro** or **Piper**).

With EVE-TTS and a virtual audio cable, you can **type in-game** and have your messages spoken over voice comms (Discord, TeamSpeak, Mumble, etc.) in real time without needing a microphone.

---

## Features

- **Modern Dark-Themed JavaFX GUI**: Full graphical desktop interface for configuring settings, managing channels and pilot voice mappings, testing voices, and monitoring live logs.
- **Real-Time Live Chat Log Monitor**: Bounded live log table (FIFO up to 1,000 entries) showing UTC timestamps (EVE time), monitored channels, pilot names, voice models, message text, and event status badges (`SPOKEN`, `FILTERED`, `IGNORED`, `ERROR`).
- **Interactive Search & Filtering**: Filter chat logs by channel, search pilot names or message text, and toggle "Only Spoken" or auto-scrolling.
- **Character Voice Mapping**: Assign distinct voice models (male, female, various accents) to individual pilot names (e.g., Fleet Commanders, scouts, alt characters).
- **String & Pronunciation Replacements**: Real-time case-insensitive text substitutions to expand acronyms (`FC` → `Fleet Commander`) or fix pronunciations (`Jita` → `Ghee-tah`, `dscan` → `dee scan`) with live preview and test auditioning.
- **Built-in Voice Auditioning**: Preview any voice model or pronunciation rule directly from the UI before going live on comms.
- **Audio Device Selection**: Route audio directly to any output device (e.g. [VB-Cable](https://vb-audio.com/Cable/) / `CABLE Input`, specific headphones, or speakers).
- **System Diagnostics & Console**: Live environment checks (chatlog directory detection, audio device status, Java runtime info) and embedded diagnostic console log.
- **100% Offline & Private**: Runs entirely locally via ONNX Runtime. No external APIs, cloud subscriptions, or internet connection required for speech synthesis.
- **CPU & NVIDIA GPU (CUDA) Support**: Runs cleanly on CPU out of the box, with toggleable NVIDIA CUDA GPU acceleration.

---

## User Interface Overview

EVE-TTS provides four dedicated tabs in its desktop interface:

| Tab | Description |
| :--- | :--- |
| **Live Chat Logs** | Monitor real-time chat messages as they appear in your EVE client logs. Filter by channel, search by pilot or message content, inspect status tooltips, and view synthesis states. |
| **Configuration & Voices** | Manage general settings (EVE Chatlogs directory, polling interval, TTS engine, audio device, CPU/GPU mode), add/remove monitored chat channels, map pilot names to voice models, and audition voices. |
| **String Replacements** | Configure custom substitution rules for acronyms and pronunciation adjustments. Includes a live test input field showing real-time synthesizer previews and an audition button. |
| **Diagnostics & Console** | View runtime health, log directory online/offline status, audio device connectivity, and a rolling diagnostic console log. |

---

## Quick Start

### Option 1: Standalone Windows Bundle (No Java Required)

1. Download `eve-tts-windows.zip` from the **[Releases](../../releases)** page and extract it to a folder.
2. Download & install [VB-Cable](https://vb-audio.com/Cable/) (or any virtual audio cable driver).
3. Run `run.bat` to launch the EVE-TTS GUI.
4. In the **Configuration & Voices** tab:
   - Verify your **EVE Chatlogs Directory** (defaults to `%USERPROFILE%\Documents\EVE\logs\Chatlogs`).
   - Select your **Audio Output Device** (e.g., `CABLE Input`).
   - Add the in-game chat channels you want to monitor (e.g., `Fleet`, `Corp`, `Intel`).
   - Add your pilot character names and assign them preferred voices.
   - Click **Save Configuration**.
5. In your voice comms app (Discord, TeamSpeak, Mumble, etc.), set your input microphone device to **`CABLE Output`**.
6. Click **Start Service** in the top header.
7. Type in your monitored in-game channel and hear your messages spoken on comms!

---

### Option 2: Run from Source (JDK 25+)

```powershell
# Clone repository
git clone git@github.com:ynvaser/eve-tts.git
cd eve-tts

# Run JavaFX GUI application
.\gradlew.bat run

# Build standalone self-contained Windows bundle zip (includes trimmed JRE)
.\gradlew.bat zipWindowsBundle
# Output: build/distributions/eve-tts-windows.zip
```

---

## Configuration (`config.json`)

All GUI settings are persisted to `config.json` in the application directory. You can configure them through the GUI or edit the file directly:

```json
{
  "eveLogDirectory": "%USERPROFILE%\\Documents\\EVE\\logs\\Chatlogs",
  "channels": [
    "Fleet",
    "Intel"
  ],
  "pollIntervalMs": 500,
  "startFromTimestamp": null,
  "enableTts": true,
  "ttsEngine": "kokoro",
  "useGpu": false,
  "audioDevice": "",
  "characterVoices": {
    "Davos Skyworth": "am_michael",
    "Strelok Holmes": "bf_isabella"
  },
  "stringReplacements": {
    "dscan": "dee scan",
    "FC": "Fleet Commander",
    "Jita": "Ghee-tah"
  }
}
```

### Configuration Options

| Setting | Description | Default |
| :--- | :--- | :--- |
| `eveLogDirectory` | Path to EVE chat log folder (supports `%USERPROFILE%`, `~`, `$HOME`). | `%USERPROFILE%\Documents\EVE\logs\Chatlogs` |
| `channels` | List of EVE chat channel names to monitor. | `["Fleet"]` |
| `pollIntervalMs` | Log file polling interval in milliseconds. | `500` |
| `startFromTimestamp` | Optional cutoff timestamp (`"YYYY.MM.DD HH:MM:SS"` UTC). Set to `null` to read from app startup. | `null` |
| `enableTts` | Enable audio playback (`true`) or console logging only (`false`). | `true` |
| `ttsEngine` | Speech synthesis engine: `"kokoro"` or `"piper"`. | `"kokoro"` |
| `useGpu` | Set to `true` for NVIDIA CUDA GPU acceleration (requires CUDA 12.x & cuDNN 9.x), or `false` for CPU mode. | `false` |
| `audioDevice` | Target audio output device substring (e.g. `"CABLE Input"`, `"Speakers"`). Blank auto-selects first detected device. | `""` (first detected device) |
| `characterVoices` | Map of pilot character names to voice model aliases. | *Required (at least one)* |
| `stringReplacements` | Map of case-insensitive string replacements for pronunciations/acronyms. | `{}` |

---

## NVIDIA GPU (CUDA) Acceleration

To enable NVIDIA GPU acceleration (`"useGpu": true` in configuration or checked in the GUI):

- Install **CUDA Toolkit version 12.x** (instead of 13.x!) and **cuDNN 9.x** for the required runtime binaries to be available.
- Ensure the CUDA and cuDNN `bin` directories are added to your system `PATH`.

**Downloads:**
- **CUDA Toolkit 12.x**: [NVIDIA CUDA 12.9.2 Archive](https://developer.nvidia.com/cuda-12-9-2-download-archive)
- **cuDNN 9.x**: [NVIDIA cuDNN 9.23.2 Archive](https://developer.nvidia.com/cudnn-9-23-2-download-archive)

---

## Supported Voice Models

### Kokoro Voices (`"ttsEngine": "kokoro"`)
- `am_michael` / `michael` (American Male - Michael)
- `am_adam` / `adam` (American Male - Adam)
- `af_sarah` / `sarah` (American Female - Sarah - Default)
- `af_bella` / `bella` (American Female - Bella)
- `af_nicole` / `nicole` (American Female - Nicole)
- `af_sky` / `sky` (American Female - Sky)
- `bf_emma` / `emma` (British Female - Emma)
- `bf_isabella` / `isabella` (British Female - Isabella)
- `bm_george` / `george` (British Male - George)
- `bm_fable` / `fable` (British Male - Fable)
- `af` (Default Female)

### Piper Voices (`"ttsEngine": "piper"`)
- `alba` / `alba_medium` (British Female - Alba - Default)
- `bryce` / `bryce_medium` (US Male - Bryce)
- `cori` / `cori_high` (US Female - Cori High)
- `hfc_female` / `hfc_female_medium` (US Female - HFC)
- `hfc_male` / `hfc_male_medium` (US Male - HFC)
- `joe` / `joe_medium` (US Male - Joe)
- `john` / `john_medium` (US Male - John)
- `kristin` / `kristin_medium` (US Female - Kristin)
- `kusal` / `kusal_medium` (US Male - Kusal)
- `lessac` / `lessac_high` (US Female - Lessac High)
- `norman` / `norman_medium` (US Male - Norman)
- `ryan` / `ryan_high` (US Male - Ryan High)
- `sam` / `sam_medium` (US Male - Sam)
- `alan` / `alan_medium` (US Male - Alan)
- `jenny` / `jenny_dioco` (US Female - Jenny)
- `amy` / `sweetbbak_amy` (US Female - Amy)
- `aru` / `aru_medium` (US Female - Aru)
- `northern_english_male` (British Male - Northern English)
- `fr_fr_siwis` / `siwis` (French Female - Siwis)
- `nl_nl_ronnie` / `ronnie` (Dutch Male - Ronnie)

---

## How It Works

1. **Log Monitoring**: The application continuously monitors your local EVE Online chat logs in `%USERPROFILE%\Documents\EVE\logs\Chatlogs` for configured channel files.
2. **Parsing & Deduplication**: Incoming chat log lines are parsed for sender, channel, and UTC timestamp. State is saved in `userdata.json` to prevent re-reading previously processed lines.
3. **String Substitutions**: Configured string replacement rules are applied case-insensitively before synthesis to fix game jargon and expand acronyms.
4. **Local ONNX Synthesis**: Real-time neural speech synthesis runs locally using the [`hcoles/voices`](https://github.com/hcoles/voices) library on CPU or NVIDIA CUDA GPU. Voice models are downloaded and cached in the `./voices/` directory upon first use.
5. **Audio Streaming**: Synthesized audio chunks are routed directly to the selected audio output device with low latency.