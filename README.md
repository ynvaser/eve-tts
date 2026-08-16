# EVE-TTS

EVE-TTS is a lightweight, local text-to-speech service for EVE Online. It monitors your EVE Online chat logs in real time and reads out incoming messages using local TTS engines (**Kokoro** or **Piper**).

What this means in practice is that you can *type in game* and with a bit of configuration, use that to communicate instead of a real microphone.

---

## Features

- **100% Offline**: Runs entirely on your machine. No external APIs, no account sign-ups, and no internet connection required for speech synthesis.
- **Character Voice Mapping**: Assign different voices (male, female, regional accents) to specific pilot names (e.g., Fleet Commanders, Intel scouts, alt characters).
- **Audio Device Selection**: Route TTS audio directly to any output device (e.g. Virtual Audio Cable / `CABLE Input` for TeamSpeak or Discord, specific headphones, or speakers).
- **Instant Pre-Loading**: Voice models are loaded into memory at startup so incoming chat alerts trigger immediately without first-message delay.
- **CPU & Optional GPU Support**: Runs cleanly on CPU out of the box, with optional NVIDIA CUDA GPU support.

---

## Quick Start

### Option 1: Standalone Windows Bundle (No Java Install Required)

- Download `eve-tts-windows.zip` from the **[Releases](../../releases)** page, extract it somewhere.
- Download & install [VB-Cable](https://vb-audio.com/Cable/)
- Make yourself a channel ingame
- Open `config.json` and set your channel, your character names and preferred voices (see `config_example_kokoro.json.txt` or `config_example_piper.json.txt` for reference).
- Configure your voice comms program of choice (Discord, Teamspeak, Mumble, whatever) to use the microphone named "Cable Output"
- Run `run.bat`.
- Chat with your friends on comms without having to alt+tab out of eve or having to wake up your wife/husband/child/dog/cat sleeping next to you.

---

### Option 2: Run from Source (JDK 25+)

```powershell
# Clone repository
git clone git@github.com:ynvaser/eve-tts.git
cd eve-tts

# Build standalone distribution zip
.\gradlew.bat zipWindowsBundle

# Unzip build/distributions/eve-tts-windows.zip and run run.bat
```

---

## Configuration (`config.json`)

Configure your settings in `config.json` alongside `run.bat`:

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
  "audioDevice": "CABLE Input",
  "characterVoices": {
    "Davos Skyworth": "am_michael",
    "Strelok Holmes": "bf_isabella"
  }
}
```

### Options

| Setting | Description | Default                                                                                                 |
| :--- | :--- |:--------------------------------------------------------------------------------------------------------|
| `eveLogDirectory` | Path to your EVE chat log folder (supports `%USERPROFILE%`). | `%USERPROFILE%\Documents\EVE\logs\Chatlogs`                                                             |
| `channels` | List of EVE chat channel names to monitor. | `["Fleet", "Corp", "Spectre Fleet"]`                                                                    |
| `pollIntervalMs` | Log file polling interval in milliseconds. | `500`                                                                                                   |
| `startFromTimestamp` | Optional cutoff timestamp (`"YYYY.MM.DD HH:MM:SS"`). Set to `null` to read from app startup. | `null`                                                                                                  |
| `enableTts` | Set to `true` for audio playback, or `false` for console logging only. | `true`                                                                                                  |
| `ttsEngine` | Engine to use: `"kokoro"` or `"piper"`. | `"kokoro"`                                                                                              |
| `useGpu` | Set to `true` for NVIDIA CUDA GPU acceleration (requires CUDA 12.x drivers), or `false` for CPU mode. | `false`                                                                                                 |
| `audioDevice` | Substring match for audio output device (e.g. `"CABLE Input"`, `"Speakers"`). | `"CABLE Input"`                                                                                         |
| `characterVoices` | Key-value map of character names to voice model aliases. | *(Required)* Make sure to not mix and match piper and kokoro voices, stick to what you've chosen above. |

---

## Voice Model Aliases

### Kokoro Voices (`"ttsEngine": "kokoro"`)
- `am_michael` (American Male - Michael)
- `am_adam` (American Male - Adam)
- `af_bella` (American Female - Bella)
- `af_sarah` (American Female - Sarah)
- `af_nicole` (American Female - Nicole)
- `af_sky` (American Female - Sky)
- `bf_isabella` (British Female - Isabella)
- `bf_emma` (British Female - Emma)
- `bm_george` (British Male - George)
- `bm_fable` (British Male - Fable)

### Piper Voices (`"ttsEngine": "piper"`)
- `ryan` / `ryan_high` (US Male - High Quality)
- `lessac` / `lessac_high` (US Female - High Quality)
- `cori` / `cori_high` (US Female - High Quality)
- `hfc_male` / `hfcMaleMedium` (US Male - HFC Medium)
- `hfc_female` / `hfcFemaleMedium` (US Female - HFC Medium)
- `bryce` / `bryce_medium` (US Male - Bryce Medium)
- `joe` / `joe_medium` (US Male - Joe Medium)
- `john` / `john_medium` (US Male - John Medium)
- `kristin` / `kristin_medium` (US Female - Kristin Medium)
- `alba` / `alba_medium` (British Female - Alba Medium)
- `alan` / `alan_medium` (US Male - Alan Medium)

---

## How It Works

EVE-TTS is written in Java 25 using the [`hcoles/voices`](https://github.com/hcoles/voices) library for running ONNX models. When the app starts up, configured voice models are downloaded and cached in the `./voices/` directory. Incoming chat log lines are parsed, split into sentence chunks, and streamed to the selected audio output device.