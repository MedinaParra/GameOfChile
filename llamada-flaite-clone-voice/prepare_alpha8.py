#!/usr/bin/env python3
"""Prepare Voz Flaite Clonada alpha8 with demand-loaded PocketTTS.

Alpha7 kept a TextToSpeech service alive while opening the recorder and while
creating a clone-only encoder. On memory-constrained Android devices this could
load two ONNX engine graphs in one process and cause a native/OOM process kill.
Alpha8 keeps the full synthesis engine unloaded until the user explicitly taps
preview, closes it after the utterance, and uses only two CPU threads while
encoding a new voice profile.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys


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
        raise SystemExit("Uso: prepare_alpha8.py <directorio-nekospeak>")

    root = pathlib.Path(sys.argv[1]).resolve()
    alpha7 = pathlib.Path(__file__).with_name("prepare_alpha7.py")
    subprocess.run([sys.executable, str(alpha7), str(root)], check=True)

    # New installable version while preserving the independent alpha7 package.
    gradle = root / "app/build.gradle.kts"
    text = read(gradle)
    text = re.sub(r"versionCode\s*=\s*7", "versionCode = 8", text, count=1)
    text = re.sub(
        r'versionName\s*=\s*"0\.7\.0-alpha7-pocket-es-clone"',
        'versionName = "0.8.0-alpha8-a26-memory-fix"',
        text,
        count=1,
    )
    write(gradle, text)

    # Fewer inference threads reduce native memory pressure and thread stacks on
    # mid-range phones. This is slower but substantially safer for cloning.
    prefs = root / "app/src/main/java/com/nekospeak/tts/data/PrefsManager.kt"
    text = read(prefs)
    text = text.replace("prefs.getInt(KEY_THREADS, 6)", "prefs.getInt(KEY_THREADS, 2)")
    write(prefs, text)

    voices = root / "app/src/main/java/com/nekospeak/tts/ui/screens/VoicesScreen.kt"
    text = read(voices)

    # Do not start the app's TTS service as soon as the voice list opens. That
    # service loads the complete PocketTTS graph and was still resident when the
    # clone-only encoder was created.
    text = replace_required(
        text,
        '    var tts: TextToSpeech? by remember { mutableStateOf(null) }\n',
        '',
        'estado persistente TextToSpeech',
    )
    text, removed = re.subn(
        r"\n    DisposableEffect\(Unit\) \{.*?\n    \}\n    \s*// Sync ViewModel selection with Prefs",
        "\n    // The full neural engine is opened only for an explicit preview and is\n"
        "    // shut down when that utterance finishes.\n    // Sync ViewModel selection with Prefs",
        text,
        count=1,
        flags=re.DOTALL,
    )
    if removed != 1:
        raise RuntimeError("No se pudo eliminar la inicialización persistente de TTS")

    old_preview = '''                        onClick = {
                             val voiceId = uiState.selectedVoiceId ?: prefs.currentVoice
                             val params = android.os.Bundle()
                             params.putString("voiceName", voiceId)
                              
                             // Graceful recovery: stop, and if isSpeaking was true after stop, recreate TTS
                             val wasSpeaking = tts?.isSpeaking == true
                             tts?.stop()
                              
                             // If TTS was stuck or in an error state, recreate it
                             if (wasSpeaking) {
                                 // Give a brief moment for stop to take effect
                                 tts?.shutdown()
                                 tts = TextToSpeech(context, { _ -> }, "cl.medina.flaiteclone")
                             }
                              
                             // Set the speech rate from preferences
                             tts?.setSpeechRate(prefs.speechSpeed)
                              
                             tts?.speak(testText, TextToSpeech.QUEUE_FLUSH, params, "test_id")
                        },'''
    new_preview = '''                        onClick = {
                             val voiceId = uiState.selectedVoiceId ?: prefs.currentVoice
                             val params = android.os.Bundle().apply {
                                 putString("voiceName", voiceId)
                             }
                             var previewEngine: TextToSpeech? = null
                             previewEngine = TextToSpeech(context, { status ->
                                 if (status == TextToSpeech.SUCCESS) {
                                     previewEngine?.setOnUtteranceProgressListener(
                                         object : android.speech.tts.UtteranceProgressListener() {
                                             override fun onStart(utteranceId: String?) = Unit
                                             override fun onDone(utteranceId: String?) {
                                                 previewEngine?.shutdown()
                                                 previewEngine = null
                                             }
                                             @Deprecated("Deprecated in Java")
                                             override fun onError(utteranceId: String?) {
                                                 previewEngine?.shutdown()
                                                 previewEngine = null
                                             }
                                             override fun onError(utteranceId: String?, errorCode: Int) {
                                                 previewEngine?.shutdown()
                                                 previewEngine = null
                                             }
                                         }
                                     )
                                     previewEngine?.setSpeechRate(prefs.speechSpeed)
                                     previewEngine?.speak(
                                         testText,
                                         TextToSpeech.QUEUE_FLUSH,
                                         params,
                                         "alpha8_preview_${System.currentTimeMillis()}"
                                     )
                                 } else {
                                     previewEngine?.shutdown()
                                     previewEngine = null
                                     scope.launch {
                                         snackbarHostState.showSnackbar(
                                             "No se pudo abrir el motor. Revisa que el modelo español esté descargado."
                                         )
                                     }
                                 }
                             }, context.packageName)
                        },'''
    text = replace_required(text, old_preview, new_preview, 'reproducción bajo demanda')
    write(voices, text)

    # Selecting an already active PocketTTS profile must not trigger a needless
    # service reload. This also protects users who configured the app as Android's
    # system TTS engine before installing alpha8.
    view_model = root / "app/src/main/java/com/nekospeak/tts/ui/viewmodel/VoicesViewModel.kt"
    text = read(view_model)
    text = replace_required(
        text,
        '            prefs.currentModel = newModel\n',
        '            if (prefs.currentModel != newModel) {\n'
        '                prefs.currentModel = newModel\n'
        '            }\n',
        'evitar recarga redundante al seleccionar voz',
    )

    text = replace_required(
        text,
        '                // Initialize a lightweight clone-only engine to avoid OOM/native crashes\n'
        '                val engine = com.nekospeak.tts.engine.pocket.PocketTtsEngine(context, cloneOnly = true)',
        '                // Keep cloning isolated from the full synthesis graph. Two threads\n'
        '                // reduce native memory pressure on devices such as Galaxy A26.\n'
        '                com.nekospeak.tts.data.PrefsManager(context).cpuThreads = 2\n'
        '                System.gc()\n'
        '                val engine = com.nekospeak.tts.engine.pocket.PocketTtsEngine(context, cloneOnly = true)',
        'encoder liviano con memoria controlada',
    )
    text = replace_required(
        text,
        '                    permanentFile.delete()\n                }\n            } catch (e: Exception) {',
        '                    permanentFile.delete()\n                    System.gc()\n                }\n            } catch (e: Throwable) {',
        'captura robusta de error de clonación',
    )
    text = text.replace(
        'it.copy(cloneErrorMessage = "Voice cloning failed. Please try again.")',
        'it.copy(cloneErrorMessage = "No se pudo crear el perfil. Cierra otras aplicaciones, reinicia esta app y vuelve a intentarlo con una grabación de 20 segundos.")',
        1,
    )
    write(view_model, text)

    notice = root / "app/src/main/assets/ALPHA8_NOTICE.txt"
    notice.write_text(
        "Voz Flaite Clonada alpha8\n"
        "Corrección Galaxy A26: motor completo bajo demanda, encoder aislado y dos hilos.\n"
        "Utilice únicamente una voz propia o autorizada.\n",
        encoding="utf-8",
    )

    print("Alpha8 preparada correctamente en", root)


if __name__ == "__main__":
    main()
