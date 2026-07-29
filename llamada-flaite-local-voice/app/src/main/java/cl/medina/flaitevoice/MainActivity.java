package cl.medina.flaitevoice;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.TtsKt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String MODEL_DIR = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private OfflineTts tts;
    private AudioTrack currentTrack;
    private volatile boolean stopRequested;
    private volatile boolean generating;

    private TextView statusView;
    private TextView spokenView;
    private EditText inputView;
    private Spinner speakerSpinner;
    private Spinner intensitySpinner;
    private Spinner speedSpinner;
    private Spinner profileSpinner;
    private Button speakButton;
    private Button randomButton;
    private Button stopButton;

    private final String[] samplePhrases = {
            "Oe, hermano, ¿qué cuestión querí? Habla al tiro po, que no tengo todo el día.",
            "Ya po, loco, no me vengái con cuentos. Decime la firme de una vez.",
            "Wena, compare. ¿Dónde andái metío? Hace caleta que no aparecí por ningún lao.",
            "Oe, te estoy preguntando en buena. ¿Vai a venir o vai a puro dar jugo?",
            "Mira, hermano, la cuestión es cortita: hablai claro y no le pongái tanto color.",
            "Aló... ¿quién habla? Oe, si vai a decir una cuestión, decila al tiro po.",
            "Eh... ya po... ¿qué pasó ahora? Contame bien la cuestión y no me dejí esperando.",
            "No, no, no... pará un poco, compare. Esa cuestión no fue así y vo lo sabí."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 19, 24));
        window.setNavigationBarColor(Color.rgb(16, 19, 24));
        createUi();
        initializeTts();
    }

    private void createUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 19, 24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView badge = label("100% LOCAL · VOZ ORIGINAL · SIN CLONAR", 11, Color.rgb(108, 229, 165));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(16), 0, dp(16), 0);
        badge.setBackground(rounded(Color.rgb(31, 49, 43), 30));
        root.addView(badge, params(-2, dp(36), 0, 0, 0, 18));

        TextView title = label("Voz flaite — perfil extremo", 27, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params(-1, -2, 0, 0, 0, 6));

        TextView subtitle = label("Actuación nasal, áspera y quebrada basada en rasgos acústicos", 14, Color.rgb(174, 180, 190));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, params(-1, -2, 0, 0, 0, 18));

        statusView = card("Cargando modelo neuronal local…", Color.rgb(108, 229, 165));
        root.addView(statusView, params(-1, -2, 0, 0, 0, 16));

        TextView analysis = card(
                "Perfil del audio: cambios grandes de tono, entradas explosivas, energía irregular y énfasis nasal. La identidad vocal no se copia.",
                Color.rgb(190, 199, 213)
        );
        root.addView(analysis, params(-1, -2, 0, 0, 0, 16));

        root.addView(sectionTitle("TEXTO"), params(-1, -2, 0, 0, 0, 7));
        inputView = new EditText(this);
        inputView.setText(samplePhrases[0]);
        inputView.setTextColor(Color.WHITE);
        inputView.setHintTextColor(Color.rgb(130, 137, 150));
        inputView.setTextSize(17);
        inputView.setGravity(Gravity.TOP | Gravity.START);
        inputView.setPadding(dp(16), dp(14), dp(16), dp(14));
        inputView.setMinLines(4);
        inputView.setBackground(rounded(Color.rgb(28, 33, 42), 18));
        root.addView(inputView, params(-1, dp(132), 0, 0, 0, 14));

        LinearLayout optionsTop = new LinearLayout(this);
        optionsTop.setOrientation(LinearLayout.HORIZONTAL);
        optionsTop.setGravity(Gravity.CENTER);
        root.addView(optionsTop, params(-1, -2, 0, 0, 0, 10));

        speakerSpinner = new Spinner(this);
        intensitySpinner = new Spinner(this);
        setSpinner(speakerSpinner, new String[]{
                "Voz 0", "Voz 1", "Voz 2", "Voz 3", "Voz 4",
                "Voz 5", "Voz 6", "Voz 7", "Voz 8", "Voz 9"
        });
        setSpinner(intensitySpinner, new String[]{"Flaite suave", "Flaite medio", "Flaite terrible"});
        speakerSpinner.setSelection(6);
        intensitySpinner.setSelection(2);
        optionsTop.addView(speakerSpinner, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(54), 1);
        second.leftMargin = dp(8);
        optionsTop.addView(intensitySpinner, second);

        profileSpinner = new Spinner(this);
        setSpinner(profileSpinner, new String[]{
                "Nasal quebrado",
                "Ronco callejero",
                "Viejo choro acelerado",
                "Quiebre extremo"
        });
        profileSpinner.setSelection(3);
        root.addView(profileSpinner, params(-1, dp(54), 0, 0, 0, 10));

        speedSpinner = new Spinner(this);
        setSpinner(speedSpinner, new String[]{"Calmado 0,96×", "Natural 1,00×", "Acelerado 1,08×", "Desbocado 1,14×"});
        speedSpinner.setSelection(2);
        root.addView(speedSpinner, params(-1, dp(54), 0, 0, 0, 15));

        root.addView(sectionTitle("ASÍ LO VA A ACTUAR"), params(-1, -2, 0, 0, 0, 7));
        spokenView = card("El texto adaptado aparecerá aquí.", Color.rgb(214, 219, 228));
        root.addView(spokenView, params(-1, -2, 0, 0, 0, 18));

        speakButton = actionButton("▶  HABLAR TERRIBLE FLAITE", Color.rgb(42, 190, 105));
        speakButton.setEnabled(false);
        root.addView(speakButton, params(-1, dp(60), 0, 0, 0, 10));

        LinearLayout lowerButtons = new LinearLayout(this);
        lowerButtons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(lowerButtons, params(-1, dp(54), 0, 0, 0, 12));

        randomButton = actionButton("Otra frase", Color.rgb(48, 57, 72));
        stopButton = actionButton("Detener", Color.rgb(111, 52, 56));
        lowerButtons.addView(randomButton, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, -1, 1);
        stopParams.leftMargin = dp(8);
        lowerButtons.addView(stopButton, stopParams);

        TextView note = label(
                "Alpha5 experimental: genera una voz original. No contiene ni reutiliza el audio de la persona del video.",
                12,
                Color.rgb(133, 141, 154)
        );
        note.setGravity(Gravity.CENTER);
        root.addView(note, params(-1, -2, 0, 4, 0, 0));

        speakButton.setOnClickListener(v -> synthesize());
        randomButton.setOnClickListener(v -> {
            inputView.setText(samplePhrases[random.nextInt(samplePhrases.length)]);
            spokenView.setText("Pulsa Hablar para aplicar la actuación.");
        });
        stopButton.setOnClickListener(v -> stopPlayback());
        setContentView(scroll);
    }

    private void initializeTts() {
        executor.execute(() -> {
            try {
                OfflineTtsConfig config = TtsKt.getOfflineTtsConfig(
                        MODEL_DIR,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        4,
                        false,
                        true,
                        "duration_predictor.int8.onnx",
                        "text_encoder.int8.onnx",
                        "vector_estimator.int8.onnx",
                        "vocoder.int8.onnx",
                        "tts.json",
                        "unicode_indexer.bin",
                        "voice.bin"
                );
                tts = new OfflineTts(getAssets(), config);
                runOnUiThread(() -> {
                    statusView.setText("Modelo listo. Parte con Voz 6 + Quiebre extremo.");
                    speakButton.setEnabled(true);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> statusView.setText("No pude cargar el modelo local: " + safeMessage(error)));
            }
        });
    }

    private void synthesize() {
        if (tts == null || generating) return;
        String source = inputView.getText().toString().trim();
        if (source.isEmpty()) {
            statusView.setText("Escribe una frase primero.");
            return;
        }

        int intensity = intensitySpinner.getSelectedItemPosition();
        int speaker = speakerSpinner.getSelectedItemPosition();
        VoiceProfile profile = selectedProfile();
        float globalSpeed = selectedSpeed();
        String performedText = makeFlaite(source, intensity);
        spokenView.setText(performedText);
        List<String> segments = splitForPerformance(performedText);

        generating = true;
        stopRequested = false;
        speakButton.setEnabled(false);
        statusView.setText("Preparando actuación local…");

        executor.execute(() -> {
            try {
                for (int i = 0; i < segments.size() && !stopRequested; i++) {
                    String segment = segments.get(i).trim();
                    if (segment.isEmpty()) continue;

                    float segmentVariation = 1.0f + randomRange(-profile.speedJitter, profile.speedJitter);
                    float generationSpeed = clamp(globalSpeed * profile.generationSpeed * segmentVariation, 0.82f, 1.35f);
                    GenerationConfig generation = new GenerationConfig(
                            0.10f,
                            generationSpeed,
                            speaker,
                            null,
                            0,
                            null,
                            8,
                            Collections.singletonMap("lang", "es")
                    );

                    final int current = i + 1;
                    final int total = segments.size();
                    runOnUiThread(() -> statusView.setText("Actuando fragmento " + current + " de " + total + "…"));
                    GeneratedAudio audio = tts.generateWithConfig(segment, generation);
                    if (audio.getSamples().length == 0) {
                        throw new IllegalStateException("el modelo no produjo audio");
                    }

                    float[] processed = processVoice(audio.getSamples(), audio.getSampleRate(), profile, i);
                    playFloatAudio(processed, audio.getSampleRate(), profile, i);
                    if (!stopRequested && i + 1 < segments.size()) {
                        try {
                            Thread.sleep(55L + random.nextInt(120));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                runOnUiThread(() -> statusView.setText(stopRequested ? "Reproducción detenida." : "Listo. Cambia perfil o voz y compara."));
            } catch (Throwable error) {
                runOnUiThread(() -> statusView.setText("Falló la generación local: " + safeMessage(error)));
            } finally {
                generating = false;
                runOnUiThread(() -> speakButton.setEnabled(tts != null));
            }
        });
    }

    private List<String> splitForPerformance(String text) {
        String normalized = text.replace(";", "; ").replace("…", "… ").replaceAll("\\s+", " ").trim();
        String[] rough = normalized.split("(?<=[,;.!?…])\\s+");
        List<String> result = new ArrayList<>();
        for (String part : rough) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            if (value.length() <= 105) {
                result.add(value);
                continue;
            }
            String[] words = value.split(" ");
            StringBuilder chunk = new StringBuilder();
            for (String word : words) {
                if (chunk.length() + word.length() + 1 > 90 && chunk.length() > 0) {
                    result.add(chunk.toString().trim());
                    chunk.setLength(0);
                }
                chunk.append(word).append(' ');
            }
            if (chunk.length() > 0) result.add(chunk.toString().trim());
        }
        if (result.isEmpty()) result.add(text);
        return result;
    }

    private float[] processVoice(float[] input, int sampleRate, VoiceProfile profile, int segmentIndex) {
        float[] output = new float[input.length];
        double dt = 1.0 / Math.max(8000, sampleRate);
        double highPassRc = 1.0 / (2.0 * Math.PI * profile.highPassHz);
        float hpAlpha = (float) (highPassRc / (highPassRc + dt));
        float nasalFastAlpha = lowPassAlpha(profile.nasalHighHz, sampleRate);
        float nasalSlowAlpha = lowPassAlpha(profile.nasalLowHz, sampleRate);

        float previousInput = 0.0f;
        float highPassed = 0.0f;
        float nasalFast = 0.0f;
        float nasalSlow = 0.0f;
        float peak = 0.001f;
        float tanhDrive = (float) Math.tanh(profile.drive);
        double flutterPhase = random.nextDouble() * Math.PI * 2.0;
        double raspPhase = random.nextDouble() * Math.PI * 2.0;

        for (int i = 0; i < input.length; i++) {
            float x = input[i];
            highPassed = hpAlpha * (highPassed + x - previousInput);
            previousInput = x;

            nasalFast += nasalFastAlpha * (highPassed - nasalFast);
            nasalSlow += nasalSlowAlpha * (highPassed - nasalSlow);
            float nasalBand = nasalFast - nasalSlow;

            float y = highPassed + profile.nasalAmount * nasalBand;
            double time = i / (double) sampleRate;
            float flutter = 1.0f + profile.flutterAmount * (float) Math.sin(
                    2.0 * Math.PI * profile.flutterHz * time + flutterPhase
            );
            float roughness = 1.0f + profile.roughAmplitude * (float) Math.sin(
                    2.0 * Math.PI * profile.roughHz * time + raspPhase
            );
            y *= flutter * roughness;

            if (Math.abs(y) > 0.012f) {
                y += (random.nextFloat() * 2.0f - 1.0f) * profile.breathNoise;
            }

            y = (float) Math.tanh(y * profile.drive) / Math.max(0.1f, tanhDrive);
            float absolute = Math.abs(y);
            if (absolute > profile.compressionThreshold) {
                float excess = absolute - profile.compressionThreshold;
                absolute = profile.compressionThreshold + excess / profile.compressionRatio;
                y = Math.copySign(absolute, y);
            }

            if (profile.microBreaks && i > sampleRate / 5) {
                int period = Math.max(1, sampleRate / 7);
                int position = (i + segmentIndex * 977) % period;
                if (position < 18) y *= position / 18.0f;
            }

            output[i] = y;
            peak = Math.max(peak, Math.abs(y));
        }

        float gain = Math.min(1.55f, 0.93f / peak);
        for (int i = 0; i < output.length; i++) {
            output[i] *= gain;
        }
        return output;
    }

    private float lowPassAlpha(float cutoff, int sampleRate) {
        float value = (float) (1.0 - Math.exp(-2.0 * Math.PI * cutoff / Math.max(8000, sampleRate)));
        return clamp(value, 0.001f, 0.95f);
    }

    private void playFloatAudio(float[] samples, int sampleRate, VoiceProfile profile, int segmentIndex) {
        stopPlaybackInternal();
        int minimum = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
        );
        if (minimum < 4096) minimum = 4096;

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minimum)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        float pitchDirection = (segmentIndex % 3 == 0 ? 1.0f : segmentIndex % 3 == 1 ? -0.55f : 0.35f);
        float pitch = clamp(profile.pitch + pitchDirection * profile.pitchSwing + randomRange(-0.018f, 0.018f), 0.78f, 1.32f);
        float playbackSpeed = clamp(profile.playbackSpeed + randomRange(-0.015f, 0.025f), 0.88f, 1.18f);
        try {
            PlaybackParams params = new PlaybackParams();
            params.setPitch(pitch);
            params.setSpeed(playbackSpeed);
            track.setPlaybackParams(params);
        } catch (Throwable ignored) {
            // Algunos fabricantes ignoran pitch independiente; el DSP y la velocidad TTS siguen activos.
        }

        currentTrack = track;
        track.play();
        int offset = 0;
        final int chunk = 4096;
        while (offset < samples.length && !stopRequested) {
            int count = Math.min(chunk, samples.length - offset);
            int written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING);
            if (written <= 0) break;
            offset += written;
        }
        stopPlaybackInternal();
    }

    private void stopPlayback() {
        stopRequested = true;
        stopPlaybackInternal();
        statusView.setText("Detenido.");
    }

    private synchronized void stopPlaybackInternal() {
        AudioTrack track = currentTrack;
        currentTrack = null;
        if (track != null) {
            try { track.pause(); } catch (Exception ignored) { }
            try { track.flush(); } catch (Exception ignored) { }
            try { track.stop(); } catch (Exception ignored) { }
            try { track.release(); } catch (Exception ignored) { }
        }
    }

    private String makeFlaite(String source, int intensity) {
        String text = source.trim()
                .replaceAll("(?i)\\boye\\b", "oe")
                .replaceAll("(?i)\\bpara el\\b", "pal")
                .replaceAll("(?i)\\bpara la\\b", "pa la")
                .replaceAll("(?i)\\bpara\\b", "pa")
                .replaceAll("(?i)\\bquieres\\b", "querí")
                .replaceAll("(?i)\\bquieras\\b", "querái")
                .replaceAll("(?i)\\bestás\\b", "estái")
                .replaceAll("(?i)\\bpuedes\\b", "podí")
                .replaceAll("(?i)\\bvas\\b", "vai")
                .replaceAll("(?i)\\bdices\\b", "decí")
                .replaceAll("(?i)\\bdime\\b", "decime")
                .replaceAll("(?i)\\bcuéntame\\b", "contame")
                .replaceAll("(?i)\\bocupado\\b", "ocupao")
                .replaceAll("(?i)\\bmetido\\b", "metío")
                .replaceAll("(?i)\\benojado\\b", "enojáo")
                .replaceAll("(?i)\\bcansado\\b", "cansáo")
                .replaceAll("(?i)\\btodo\\b", "to")
                .replaceAll("(?i)\\bnada\\b", "ná")
                .replaceAll("(?i)\\bverdad\\b", "verdá")
                .replaceAll("(?i)\\bentonces\\b", "entonce")
                .replaceAll("(?i)\\badónde\\b", "aónde")
                .replaceAll("(?i)\\bdónde\\b", "ónde")
                .replaceAll("(?i)\\bamigo\\b", "compare")
                .replaceAll("(?i)\\bpersona\\b", "loco")
                .replaceAll("(?i)\\bustedes\\b", "ustede")
                .replaceAll("(?i)\\bnosotros\\b", "nosotro");

        text = text.replaceAll("(?i)\\b([a-záéíóúñ]+)ado\\b", "$1ao")
                .replaceAll("(?i)\\b([a-záéíóúñ]+)ido\\b", "$1ío");

        if (intensity == 0) {
            return ensurePrefix(text, "Oe, compare… ") + " po.";
        }
        if (intensity == 1) {
            return ensurePrefix(text, "Wena, hermano… eh… ") + " ya po.";
        }

        String[] starts = {
                "¡Oe, oe, oe!… mira… ",
                "Ya po, hermano… eh… ",
                "Wena, compare… oe… ",
                "Mira, loco… la cuestión es así… ",
                "No, no, no… pará un poco… "
        };
        String[] endings = {
                "… ¿cachái o no?",
                "… ya po, hablai al tiro.",
                "… no me dejí esperando, po.",
                "… esa es la firme.",
                "… ¿me entendí?"
        };
        String result = ensurePrefix(text, starts[random.nextInt(starts.length)]);
        result = result.replaceAll("\\.\\s*", "… ")
                .replaceAll(",\\s*", ", eh… ")
                .replaceAll("\\?\\s*", "… ¿cachái? ");
        if (!result.toLowerCase(Locale.ROOT).contains(" po")) result += " po";
        if (random.nextBoolean()) result += endings[random.nextInt(endings.length)];
        return result.replaceAll("\\s+", " ").trim();
    }

    private String ensurePrefix(String text, String prefix) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("oe") || lower.startsWith("wena") || lower.startsWith("ya po") || lower.startsWith("mira") || lower.startsWith("no, no")) {
            return text;
        }
        return prefix + Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private VoiceProfile selectedProfile() {
        switch (profileSpinner.getSelectedItemPosition()) {
            case 0:
                return new VoiceProfile(1.09f, 0.060f, 1.04f, 1.05f, 0.050f, 1.85f, 0.62f, 760f, 1650f, 115f, 0.035f, 6.2f, 0.025f, 27f, 0.0017f, 0.36f, 3.0f, false);
            case 1:
                return new VoiceProfile(0.92f, 0.035f, 1.00f, 1.02f, 0.035f, 2.55f, 0.30f, 620f, 1400f, 95f, 0.022f, 5.0f, 0.050f, 31f, 0.0032f, 0.32f, 4.0f, false);
            case 2:
                return new VoiceProfile(1.02f, 0.070f, 1.07f, 1.11f, 0.065f, 2.20f, 0.45f, 690f, 1550f, 105f, 0.050f, 6.8f, 0.045f, 34f, 0.0025f, 0.34f, 3.5f, true);
            default:
                return new VoiceProfile(1.12f, 0.095f, 1.06f, 1.10f, 0.075f, 2.75f, 0.72f, 780f, 1780f, 125f, 0.065f, 7.5f, 0.060f, 38f, 0.0030f, 0.30f, 4.5f, true);
        }
    }

    private float selectedSpeed() {
        switch (speedSpinner.getSelectedItemPosition()) {
            case 0: return 0.96f;
            case 2: return 1.08f;
            case 3: return 1.14f;
            default: return 1.0f;
        }
    }

    private float randomRange(float minimum, float maximum) {
        return minimum + random.nextFloat() * (maximum - minimum);
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text, 11, Color.rgb(132, 141, 157));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView card(String text, int color) {
        TextView view = label(text, 15, color);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setMinHeight(dp(66));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackground(rounded(Color.rgb(28, 33, 42), 18));
        return view;
    }

    private Button actionButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(color, 18));
        return button;
    }

    private void setSpinner(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(13);
                view.setGravity(Gravity.CENTER);
                view.setBackground(rounded(Color.rgb(36, 42, 52), 16));
                return view;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundDrawable(rounded(Color.rgb(36, 42, 52), 10));
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @Override
    protected void onDestroy() {
        stopRequested = true;
        stopPlaybackInternal();
        executor.shutdownNow();
        if (tts != null) {
            try { tts.release(); } catch (Throwable ignored) { }
        }
        super.onDestroy();
    }

    private static final class VoiceProfile {
        final float pitch;
        final float pitchSwing;
        final float playbackSpeed;
        final float generationSpeed;
        final float speedJitter;
        final float drive;
        final float nasalAmount;
        final float nasalLowHz;
        final float nasalHighHz;
        final float highPassHz;
        final float flutterAmount;
        final float flutterHz;
        final float roughAmplitude;
        final float roughHz;
        final float breathNoise;
        final float compressionThreshold;
        final float compressionRatio;
        final boolean microBreaks;

        VoiceProfile(
                float pitch,
                float pitchSwing,
                float playbackSpeed,
                float generationSpeed,
                float speedJitter,
                float drive,
                float nasalAmount,
                float nasalLowHz,
                float nasalHighHz,
                float highPassHz,
                float flutterAmount,
                float flutterHz,
                float roughAmplitude,
                float roughHz,
                float breathNoise,
                float compressionThreshold,
                float compressionRatio,
                boolean microBreaks
        ) {
            this.pitch = pitch;
            this.pitchSwing = pitchSwing;
            this.playbackSpeed = playbackSpeed;
            this.generationSpeed = generationSpeed;
            this.speedJitter = speedJitter;
            this.drive = drive;
            this.nasalAmount = nasalAmount;
            this.nasalLowHz = nasalLowHz;
            this.nasalHighHz = nasalHighHz;
            this.highPassHz = highPassHz;
            this.flutterAmount = flutterAmount;
            this.flutterHz = flutterHz;
            this.roughAmplitude = roughAmplitude;
            this.roughHz = roughHz;
            this.breathNoise = breathNoise;
            this.compressionThreshold = compressionThreshold;
            this.compressionRatio = compressionRatio;
            this.microBreaks = microBreaks;
        }
    }
}
