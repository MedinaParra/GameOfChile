#!/usr/bin/env python3
"""Prepare a pinned NekoSpeak checkout as Voz Flaite Clonada alpha7.

The resulting app uses PocketTTS Spanish ONNX models, records an authorized
voice reference, stores the embedding locally, and generates full phrases in
batch mode. Third-party source is fetched at a pinned commit by CI.
"""

from __future__ import annotations

import pathlib
import re
import sys


APP_ID = "cl.medina.flaiteclone"
MODEL_BASE = "https://huggingface.co/lookbe/pocket-tts-onnx/resolve/main/spanish"
GUIDED_TEXT = (
    "Oe, compare, esta es mi voz y autorizo usar esta grabación solamente para "
    "crear un perfil local en este teléfono. Voy a hablar tranquilo, pero con mi "
    "forma natural, mis pausas y mi tono. Ya po, escucha bien: ¿qué cuestión querí "
    "que diga? Contame la firme al tiro, porque no voy a estar esperando todo el día. "
    "A veces hablo rápido, otras veces bajo la voz, y cuando me enojo le pongo más energía. "
    "Esta grabación es mía o tengo permiso explícito de la persona que está hablando."
)


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: pathlib.Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def replace_required(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"No se encontró el patrón requerido: {label}")
    return text.replace(old, new)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Uso: prepare_alpha7.py <directorio-nekospeak>")

    root = pathlib.Path(sys.argv[1]).resolve()
    if not (root / "app").is_dir():
        raise RuntimeError(f"No parece un checkout de NekoSpeak: {root}")

    # App identity and version. Keep Kotlin namespaces intact; only Android's
    # installable application ID changes.
    gradle = root / "app/build.gradle.kts"
    text = read(gradle)
    text = replace_required(text, 'applicationId = "com.nekospeak.tts"', f'applicationId = "{APP_ID}"', "applicationId")
    text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 7', text, count=1)
    text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.7.0-alpha7-pocket-es-clone"', text, count=1)
    write(gradle, text)

    # Any literal package used to instantiate Android TextToSpeech must point
    # to the new application ID. Package declarations are intentionally kept.
    for kotlin_file in (root / "app/src/main/java").rglob("*.kt"):
        source = read(kotlin_file)
        patched = source.replace('"com.nekospeak.tts"', f'"{APP_ID}"')
        if patched != source:
            write(kotlin_file, patched)

    # Spanish PocketTTS ONNX bundle. The six files total about 165 MB and are
    # downloaded once by the app, then used fully offline.
    model_repo = root / "app/src/main/java/com/nekospeak/tts/data/ModelRepository.kt"
    text = read(model_repo)
    text = re.sub(
        r'private const val HF_BASE = "[^"]+"',
        f'private const val HF_BASE = "{MODEL_BASE}"',
        text,
        count=1,
    )
    text = text.replace('$HF_BASE/onnx/', '$HF_BASE/')
    text = text.replace(
        'Required for voice cloning. Includes encoder, decoder, and flow matching models (~200MB total).',
        'Motor español para clonación de voz local. Descarga inicial aproximada: 165 MB.'
    )
    text = text.replace('Pocket-TTS (Experimental)', 'PocketTTS español — clonación local')
    write(model_repo, text)

    # Start directly with PocketTTS, a stable temperature and batch decoding.
    prefs = root / "app/src/main/java/com/nekospeak/tts/data/PrefsManager.kt"
    text = read(prefs)
    text = text.replace('prefs.getString(KEY_VOICE, "af_heart") ?: "af_heart"', 'prefs.getString(KEY_VOICE, "alba") ?: "alba"')
    text = text.replace('prefs.getString(KEY_MODEL, "kokoro_v1.0") ?: "kokoro_v1.0"', 'prefs.getString(KEY_MODEL, "pocket_v1") ?: "pocket_v1"')
    text = text.replace('prefs.getFloat(KEY_POCKET_TEMP, 0.7f)', 'prefs.getFloat(KEY_POCKET_TEMP, 0.55f)')
    text = text.replace('prefs.getInt(KEY_POCKET_LSD, 10)', 'prefs.getInt(KEY_POCKET_LSD, 8)')
    text = text.replace('prefs.getString(KEY_POCKET_DECODE, "batch") ?: "batch"', 'prefs.getString(KEY_POCKET_DECODE, "batch") ?: "batch"')
    text = text.replace('prefs.getInt(KEY_POCKET_CHUNK, 15)', 'prefs.getInt(KEY_POCKET_CHUNK, 20)')
    write(prefs, text)

    # Make PocketTTS the selected onboarding engine and translate the critical
    # first-run instructions.
    onboarding = root / "app/src/main/java/com/nekospeak/tts/ui/screens/OnboardingScreen.kt"
    text = read(onboarding)
    text = text.replace('mutableStateOf("kokoro_v1.0")', 'mutableStateOf("pocket_v1")', 1)
    text = text.replace('mutableStateOf("af_heart")', 'mutableStateOf("alba")', 1)
    replacements = {
        'Welcome to NekoSpeak': 'Voz Flaite Clonada',
        'Private, on-device AI Text-to-Speech.': 'Clona tu propia voz y úsala dentro del teléfono.',
        '1. Choose AI Model': '1. Descarga el motor de voz',
        '2. Choose Starter Voice': '2. Voz temporal de instalación',
        '3. Customize Your Experience': '3. Termina la configuración',
        'Pocket-TTS': 'PocketTTS español',
        'Voice cloning engine. Create custom voices.': 'Motor local que aprende una voz desde una grabación autorizada.',
        'Download Required (~70MB)': 'Descarga requerida (~165 MB)',
        'Enable System-wide TTS (Optional)': 'Uso como voz del sistema (opcional)',
        'Open TTS Settings': 'Abrir ajustes de voz',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    write(onboarding, text)

    # Guided flaite performance script and explicit authorization. The text is
    # only a reading guide; PocketTTS conditions on the audio itself.
    recorder = root / "app/src/main/java/com/nekospeak/tts/ui/screens/VoiceRecorderScreen.kt"
    text = read(recorder)
    text = re.sub(
        r'val sampleTranscript = """.*?"""\.trimIndent\(\)',
        'val sampleTranscript = """' + GUIDED_TEXT + '""".trimIndent()',
        text,
        count=1,
        flags=re.DOTALL,
    )
    text = replace_required(
        text,
        'var recordedPath by remember { mutableStateOf<String?>(null) }',
        'var recordedPath by remember { mutableStateOf<String?>(null) }\n    var consentConfirmed by remember { mutableStateOf(false) }',
        'estado de consentimiento',
    )
    old_block = '''            } else {
                // Recording UI
                RecordingContent(
                    recordingState = recordingState,'''
    new_block = '''            } else {
                // Consent is required before recording a biometric voice profile.
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = consentConfirmed,
                            onCheckedChange = { consentConfirmed = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Confirmo que esta es mi voz o que tengo autorización explícita del hablante. El perfil queda guardado localmente.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Recording UI
                RecordingContent(
                    recordingState = recordingState,'''
    text = replace_required(text, old_block, new_block, "tarjeta de consentimiento")
    text = replace_required(
        text,
        '''                    onStartRecording = {
                        scope.launch {
                            audioRecorder.startRecording()
                        }
                    },''',
        '''                    onStartRecording = {
                        if (consentConfirmed) {
                            scope.launch { audioRecorder.startRecording() }
                        }
                    },''',
        "bloque de inicio de grabación",
    )
    text = text.replace('Text("Record Voice")', 'Text("Grabar mi voz")')
    text = text.replace('Text("Microphone Access Required")', 'Text("Permiso de micrófono")')
    text = text.replace(
        '"To clone your voice, we need access to your microphone to record a sample."',
        '"El motor necesita una grabación limpia para crear tu perfil vocal local."'
    )
    text = text.replace('Text("Grant Microphone Access")', 'Text("Dar permiso al micrófono")')
    text = text.replace('Text("Recording...")', 'Text("Grabando...")')
    text = text.replace('Text("Try Again")', 'Text("Intentar otra vez")')
    text = text.replace('Text("Re-record")', 'Text("Volver a grabar")')
    text = text.replace('Text("Continue")', 'Text("Crear perfil")')
    text = text.replace('Text("Clone Voice")', 'Text("Clonar voz")')
    text = text.replace('"Recording too short. Please record at least 3 seconds."', '"La grabación es demasiado corta. Lee al menos 15 segundos."')
    text = text.replace('recorded.durationMs >= AudioRecorder.MIN_DURATION_MS', 'recorded.durationMs >= 15000L')
    text = text.replace('Text("• Record 15-30 seconds for best quality")', 'Text("• Lee entre 20 y 30 segundos para capturar el estilo flaite")')
    text = text.replace('Text("• Speak clearly and naturally")', 'Text("• Actúa la voz como realmente querís que hable")')
    text = text.replace('Text("• Find a quiet environment")', 'Text("• Graba sin música, televisión ni otras personas")')
    text = text.replace('Text("Read this text aloud:")', 'Text("Lee este texto con voz flaite natural:")')
    text = text.replace('"(Any clear speech works - this text is just a guide)"', '"La actuación, el ritmo y la ronquera de esta lectura pasarán al perfil."')
    write(recorder, text)

    # Disable importing arbitrary audio in this prototype. The supported path
    # is an in-app recording with an explicit consent statement.
    voices = root / "app/src/main/java/com/nekospeak/tts/ui/screens/VoicesScreen.kt"
    text = read(voices)
    text = text.replace('var testText by remember { mutableStateOf("Hello, I am NekoSpeak.") }', 'var testText by remember { mutableStateOf("Oe, compare, decime la firme al tiro po. ¿Qué cuestión querí hablar?") }')
    text = text.replace('Text("Voices")', 'Text("Perfiles de voz")')
    text = text.replace('Text("Clone Voice")', 'Text("Clonar mi voz")')
    text = text.replace('Text("Record Voice")', 'Text("Leer guion y grabar")')
    text = text.replace('Text("Record 5-10 seconds of speech")', 'Text("Recomendado: 20 a 30 segundos, sin ruido")')
    text = text.replace('// Upload option\n                    ListItem(', '// Importación desactivada en esta alpha: solo grabación autorizada.\n                    if (false) ListItem(')
    write(voices, text)

    # Branding and Spanish defaults.
    strings = root / "app/src/main/res/values/strings.xml"
    text = read(strings)
    text = text.replace('<string name="app_name">NekoSpeak</string>', '<string name="app_name">Voz Flaite Clonada</string>')
    text = text.replace('<string name="app_description">Cat-powered offline text-to-speech with Kokoro and Kitten TTS</string>', '<string name="app_description">Clonación de voz autorizada y síntesis local en español chileno</string>')
    text = text.replace('<string name="tts_default_lang">eng</string>', '<string name="tts_default_lang">spa</string>')
    text = text.replace('<string name="tts_default_country">USA</string>', '<string name="tts_default_country">CHL</string>')
    text = text.replace('Neko TTS', 'Voz Flaite Clonada')
    text = text.replace('Hello, this is Neko TTS speaking! I can read any text for you.', 'Oe, compare, esta es mi voz clonada localmente. ¿Cómo quedó la cuestión?')
    write(strings, text)

    # Add an in-app notice bundled with the APK for attribution and boundaries.
    notice = root / "app/src/main/assets/ALPHA7_NOTICE.txt"
    notice.parent.mkdir(parents=True, exist_ok=True)
    notice.write_text(
        "Voz Flaite Clonada alpha7\n"
        "Motor derivado de NekoSpeak (MIT) y PocketTTS/ONNX.\n"
        "Utilice únicamente una voz propia o una voz con autorización explícita.\n"
        "La aplicación no contiene el audio del video de referencia.\n",
        encoding="utf-8",
    )

    print("Alpha7 preparada correctamente en", root)


if __name__ == "__main__":
    main()
