# Modern LG Remote (Samsung Galaxy S26 Ultra Edition) 📱📺✨

> **Native, ultra-fast Android companion app for rooted LG OLED TVs running [Modern LG Hub](https://github.com/paulhoehne2006-a11y/Modern-LG-Hub). Built with Kotlin, Jetpack Compose, Material 3, and deep Samsung One UI system integrations.**

---

## 🌟 Highlights & Features

- **⚡ Blazing Fast Local Performance (< 15 ms Latenz)**
  - Direct local communication with the TV's root backend (Port `8080`) over your home Wi-Fi.
  - Zero cloud lag, no mandatory pairing codes or dialogs.

- **🔘 Samsung Quick-Settings-Kachel (Kontrollzentrum)**
  - Add the **LG OLED** tile to your S26 Ultra's Quick Panel (one swipe down).
  - Tapping opens an obsidian glass **Bottom-Sheet Mini-Remote Overlay** over whatever app you are currently using without leaving it.

- **📱 2x2 Startbildschirm-Widget (Frei konfigurierbar)**
  - Shows real-time TV status (Active app / Standby) and 4 customizable action buttons.
  - Pick your preferred 4 quick actions in the settings:
    1. **Power (Wake-on-LAN / Standby)**
    2. **OLED Display Aus / An** (pure audio playback mode)
    3. **PC (HDMI 1)**
    4. **Mini-Remote Overlay öffnen**
    - Alternative options: Denon ⇄ TV Audio-Switch, Mute, Home, Netflix, YouTube, Spotify.

- **🔔 Live-Notification & Sperrbildschirm-Steuerung**
  - Automatically activates in the Samsung Now Bar & Lockscreen whenever your LG OLED is detected on your home Wi-Fi.
  - Media-style controls: Play/Pause, OLED Display Aus, Mute, and 1-tap overlay access.

- **🔊 Physische Lautstärketasten des Galaxy S26 Ultra**
  - Adjust your TV's volume directly with the hardware volume rocker on your S26 Ultra while the app or overlay is open.

- **🔈 Denon AVR-1803 Audio-Output-Badge & 1-Tap Umschalter**
  - Dedicated audio badge showing the active audio output:
    - `🔊 Denon AVR-1803 (Optischer Digitalausgang)`
    - `🔈 TV-Lautsprecher`
    - `🔊 TV & Denon AVR gleichzeitig`
    - `🎧 Bluetooth-Audio`
  - 1-Tap toggle to switch between optical Denon output and internal TV speakers.

- **🎮 3-Wege-Navigationspad mit feiner S26-Ultra-Haptik**
  1. **Wisch-Gestenpad (Samsung Style)**: Smooth swipes translate into individual D-Pad steps with tactile tick haptics. Tap = OK; Long-press = Long OK (opens context menu on Modern LG Hub).
  2. **Klassisches D-Pad**: Crisp directional buttons and glowing center OK button.
  3. **Magic Cursor Touchpad**: 120Hz smooth touch area directly moving the LG magic mouse pointer on the TV screen + right-side scroll strip.

- **⌨️ Handy-Tastatur für TV-Texteingabe**
  - Type or voice-dictate on your Samsung keyboard to send text instantly into any search or login field on webOS.

- **💡 SmartThings & Gemini KI Integration**
  - Direct control for your SmartThings lights and plugs (`TV-Licht`, `Wohnzimmer`, `Schreibtisch`, etc.).
  - Built-in Gemini voice/text assistant tab for TV recommendations and smart scenes.

- **📲 Samsung Modi & Routinen (One UI)**
  - System shortcuts and intents registered for automation routines (e.g. "Schlafmodus -> TV aus", or double-tap side button -> Mini-Remote).

---

## 🚀 Installation & Download

Pre-compiled, ready-to-install `.apk` builds are automatically generated on every commit:

1. Go to the [Releases](https://github.com/paulhoehne2006-a11y/LG-Remote-Android/releases) tab in this repository.
2. Download `Modern-LG-Remote.apk` onto your Samsung Galaxy S26 Ultra.
3. Tap the file to install (allow installation from browser if prompted).
4. Launch the app – it connects automatically to your LG OLED at `192.168.178.60`!

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0.0
- **UI Framework**: Jetpack Compose, Material 3 (Obsidian OLED Design `#050507`)
- **Networking**: OkHttp 4.12 with persistent HTTP/2 connection pooling & WebSocket (SSAP Pointer Socket)
- **Image Loading**: Coil 2.6
- **Architecture**: Single Activity + Floating Dialog Overlays + Background Presence Service + AppWidgetProvider