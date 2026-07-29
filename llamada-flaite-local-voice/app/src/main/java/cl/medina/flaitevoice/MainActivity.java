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
import android.os.SystemClock;
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

import java.util.Collections;
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
            "Aló, ¿quién habla? Oe, si vai a decir una cuestión, decila al tiro po.",
            "Ya po, ¿qué pasó ahora? Contame bien la cuestión y no me dejí esperando.",
            "Pará un poco, compare. Esa cuestión no fue así y vo lo sabí."
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

        TextView badge = label("100% LOCAL · TOMA COMPLETA · SIN CORTES", 11, Color.rgb(108, 229, 165));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(16), 0, dp(16), 0);
        badge.setBackground(rounded(Color.rgb(31, 49, 43), 30));
        root.addView(badge, params(-2, dp(36), 0, 0, 0, 18));

        TextView title = label("Voz flaite — modo fluido", 27, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params(-1, -2, 0, 0, 0, 6));

        TextView subtitle = label("Espera la generación completa y reproduce una sola toma continua", 14, Color.rgb(174, 180, 190));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, params(-1, -2, 0, 0, 0, 18));

        statusView = card("Cargando modelo neuronal local…", Color.rgb(108, 229, 165));
        root.addView(statusView, params(-1, -2, 0, 0, 0, 16));

        TextView analysis = card(
                "Alpha6 prioriza fluidez: una inferencia completa, mayor calidad y audio precargado antes de reproducir.",
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
        setSpinner(intensitySpinner, new String[]{"Flaite suave", "Flaite medio", "Flaite marcado"});
        speakerSpinner.setSelection(6);
        intensitySpinner.setSelection(2);
        optionsTop.addView(speakerSpinner, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(54), 1);
        second.leftMargin = dp(8);
        optionsTop.addView(intensitySpinner, second);

        profileSpinner = new Spinner(this);
        setSpinner(profileSpinner, new String[]{
                "Natural callejero",
                "Nasal natural",
                "Ronco fluido",
                "Flaite fluido"
        });
        profileSpinner.setSelection(3);
        root.addView(profileSpinner, params(-1, dp(54), 0, 0, 0, 10));

        speedSpinner = new Spinner(this);
        setSpinner(speedSpinner, new String[]{"Pausado 0,96×", "Natural 1,00×", "Ágil 1,04×", "Rápido 1,08×"});
        speedSpinner.setSelection(1);
        root.addView(speedSpinner, params(-1, dp(54), 0, 0, 0, 15));

        root.addView(sectionTitle("ASÍ LO VA A DECIR"), params(-1, -2, 0, 0, 0, 7));
        spokenView = card("El texto adaptado aparecerá aquí.", Color.rgb(214, 219, 228));
        root.addView(spokenView, params(-1, -2, 0, 0, 0, 18));

        speakButton = actionButton("▶  GENERAR TOMA COMPLETA", Color.rgb(42, 190, 105));
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
                "La generación tarda más porque la frase se prepara completa antes de sonar. No se divide ni se transmite por partes.",
                12,
                Color.rgb(133, 141, 154)
        );
        note.setGravity(Gravity.CENTER);
        root.addView(note, params(-1, -2, 0, 4, 0, 0));

        speakButton.setOnClickListener(v -> synthesize());
        randomButton.setOnClickListener(v -> {
            inputView.setText(samplePhrases[random.nextInt(samplePhrases.length)]);
            spokenView.setText("Pulsa Generar para preparar una toma completa.");
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
                    statusView.setText("Modelo listo. Parte con Voz 6 + Flaite fluido + velocidad natural.");
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
        float speed = clamp(selectedSpeed() * profile.generationSpeed, 0.90f, 1.12f);
        String performedText = makeFlaiteNatural(source, intensity);
        spokenView.setText(performedText);

        generating = true;
        stopRequested = false;
        speakButton.setEnabled(false);
        statusView.setText("Generando la toma completa… puede tardar un poco.");

        executor.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            try {
                GenerationConfig generation = new GenerationConfig(
                        0.10f,
                        speed,
                        speaker,
                        null,
                        0,
                        null,
                        16,
                        Collections.singletonMap("lang", "es")
                );

                GeneratedAudio audio = tts.generateWithConfig(performedText, generation);
                if (audio.getSamples().length == 0) {
                    throw new IllegalStateException("el modelo no produjo audio");
                }

                runOnUiThread(() -> statusView.setText("Suavizando y precargando la voz…"));
                float[] processed = processContinuousVoice(audio.getSamples(), audio.getSampleRate(), profile);
                long elapsed = SystemClock.elapsedRealtime() - started;
                runOnUiThread(() -> statusView.setText("Toma lista en " + (elapsed / 1000.0f) + " s. Reproduciendo sin cortes…"));
                playBufferedAudio(processed, audio.getSampleRate(), profile);
                runOnUiThread(() -> statusView.setText(stopRequested ? "Reproducción detenida." : "Listo. La toma se reprodujo completa."));
            } catch (Throwable error) {
                runOnUiThread(() -> statusView.setText("Falló la generación local: " + safeMessage(error)));
            } finally {
                generating = false;
                runOnUiThread(() -> speakButton.setEnabled(tts != null));
            }
        });
    }

    private float[] processContinuousVoice(float[] input, int sampleRate, VoiceProfile profile) {
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
        float envelope = 0.0f;
        float peak = 0.001f;
        float tanhDrive = Math.max(0.1f, (float) Math.tanh(profile.drive));
        float attack = (float) Math.exp(-1.0 / (0.006 * sampleRate));
        float release = (float) Math.exp(-1.0 / (0.090 * sampleRate));

        for (int i = 0; i < input.length; i++) {
            float x = input[i];
            highPassed = hpAlpha * (highPassed + x - previousInput);
            previousInput = x;

            nasalFast += nasalFastAlpha * (highPassed - nasalFast);
            nasalSlow += nasalSlowAlpha * (highPassed - nasalSlow);
            float nasalBand = nasalFast - nasalSlow;

            float y = highPassed + profile.nasalAmount * nasalBand;
            y = (float) Math.tanh(y * profile.drive) / tanhDrive;

            float absolute = Math.abs(y);
            if (absolute > envelope) {
                envelope = attack * envelope + (1.0f - attack) * absolute;
            } else {
                envelope = release * envelope + (1.0f - release) * absolute;
            }

            if (envelope > profile.compressionThreshold) {
                float compressed = profile.compressionThreshold
                        + (envelope - profile.compressionThreshold) / profile.compressionRatio;
                float gainReduction = compressed / Math.max(0.0001f, envelope);
                y *= gainReduction;
            }

            output[i] = y;
            peak = Math.max(peak, Math.abs(y));
        }

        float gain = Math.min(profile.outputGain, 0.94f / peak);
        int fadeSamples = Math.min(output.length / 4, Math.max(1, sampleRate / 50));
        for (int i = 0; i < output.length; i++) {
            float fade = 1.0f;
            if (i < fadeSamples) fade = i / (float) fadeSamples;
            int remaining = output.length - 1 - i;
            if (remaining < fadeSamples) fade = Math.min(fade, remaining / (float) fadeSamples);
            output[i] *= gain * Math.max(0.0f, fade);
        }
        return output;
    }

    private float lowPassAlpha(float cutoff, int sampleRate) {
        float value = (float) (1.0 - Math.exp(-2.0 * Math.PI * cutoff / Math.max(8000, sampleRate)));
        return clamp(value, 0.001f, 0.95f);
    }

    private void playBufferedAudio(float[] samples, int sampleRate, VoiceProfile profile) {
        stopPlaybackInternal();
        int minimum = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
        );
        int requestedBytes = Math.max(minimum, samples.length * 4);

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
                .setBufferSizeInBytes(requestedBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            try { track.release(); } catch (Throwable ignored) { }
            playStreamingFallback(samples, sampleRate, profile);
            return;
        }

        try {
            PlaybackParams params = new PlaybackParams();
            params.setPitch(profile.pitch);
            params.setSpeed(profile.playbackSpeed);
            track.setPlaybackParams(params);
        } catch (Throwable ignored) {
            // Algunos fabricantes no permiten modificar pitch y velocidad por separado.
        }

        int offset = 0;
        while (offset < samples.length && !stopRequested) {
            int written = track.write(samples, offset, samples.length - offset, AudioTrack.WRITE_BLOCKING);
            if (written <= 0) break;
            offset += written;
        }
        if (offset <= 0 || stopRequested) {
            try { track.release(); } catch (Throwable ignored) { }
            return;
        }

        currentTrack = track;
        track.play();
        while (!stopRequested && track.getPlaybackHeadPosition() < offset) {
            SystemClock.sleep(18L);
        }
        stopPlaybackInternal();
    }

    private void playStreamingFallback(float[] samples, int sampleRate, VoiceProfile profile) {
        int minimum = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
        );
        if (minimum < 16384) minimum = 16384;
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(minimum)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        try {
            track.setPlaybackParams(new PlaybackParams().setPitch(profile.pitch).setSpeed(profile.playbackSpeed));
        } catch (Throwable ignored) { }
        currentTrack = track;
        track.play();
        int offset = 0;
        while (offset < samples.length && !stopRequested) {
            int count = Math.min(8192, samples.length - offset);
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

    private String makeFlaiteNatural(String source, int intensity) {
        String text = source.trim()
                .replace('…', ',')
                .replaceAll("\\.{3,}", ",")
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
                .replaceAll("(?i)\\bnada\\b", "ná")
                .replaceAll("(?i)\\bverdad\\b", "verdá")
                .replaceAll("(?i)\\bentonces\\b", "entonce")
                .replaceAll("(?i)\\badónde\\b", "aónde")
                .replaceAll("(?i)\\bdónde\\b", "ónde")
                .replaceAll("(?i)\\bamigo\\b", "compare")
                .replaceAll("(?i)\\bpersona\\b", "loco")
                .replaceAll("(?i)\\bustedes\\b", "ustede")
                .replaceAll("(?i)\\bnosotros\\b", "nosotro")
                .replaceAll("(?i)\\b([a-záéíóúñ]+)ado\\b", "$1ao")
                .replaceAll("(?i)\\b([a-záéíóúñ]+)ido\\b", "$1ío")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\s+", " ")
                .trim();

        if (intensity == 0) {
            return ensureNaturalPrefix(text, "Oe, compare, ");
        }
        if (intensity == 1) {
            String result = ensureNaturalPrefix(text, "Wena, hermano, ");
            return ensureNaturalEnding(result, " ya po.");
        }

        String[] starts = {
                "Oe, hermano, ",
                "Ya po, compare, ",
                "Mira, loco, ",
                "Wena, hermano, "
        };
        String result = ensureNaturalPrefix(text, starts[random.nextInt(starts.length)]);
        return ensureNaturalEnding(result, " po.");
    }

    private String ensureNaturalPrefix(String text, String prefix) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("oe") || lower.startsWith("wena") || lower.startsWith("ya po") || lower.startsWith("mira")) {
            return text;
        }
        return prefix + Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private String ensureNaturalEnding(String text, String ending) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" po.") || lower.endsWith(" po") || lower.endsWith(" ya po.")) return text;
        if (text.endsWith(".") || text.endsWith("?") || text.endsWith("!")) {
            return text.substring(0, text.length() - 1) + ending;
        }
        return text + ending;
    }

    private VoiceProfile selectedProfile() {
        switch (profileSpinner.getSelectedItemPosition()) {
            case 0:
                return new VoiceProfile(1.00f, 0.98f, 1.00f, 78f, 700f, 1450f, 0.13f, 1.24f, 0.52f, 2.0f, 1.20f);
            case 1:
                return new VoiceProfile(0.99f, 1.03f, 1.00f, 82f, 760f, 1650f, 0.25f, 1.28f, 0.50f, 2.1f, 1.22f);
            case 2:
                return new VoiceProfile(0.98f, 0.91f, 0.99f, 72f, 620f, 1380f, 0.12f, 1.48f, 0.46f, 2.3f, 1.25f);
            default:
                return new VoiceProfile(1.00f, 0.96f, 1.01f, 78f, 700f, 1550f, 0.21f, 1.38f, 0.48f, 2.2f, 1.23f);
        }
    }

    private float selectedSpeed() {
        switch (speedSpinner.getSelectedItemPosition()) {
            case 0: return 0.96f;
            case 2: return 1.04f;
            case 3: return 1.08f;
            default: return 1.0f;
        }
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
        final float generationSpeed;
        final float pitch;
        final float playbackSpeed;
        final float highPassHz;
        final float nasalLowHz;
        final float nasalHighHz;
        final float nasalAmount;
        final float drive;
        final float compressionThreshold;
        final float compressionRatio;
        final float outputGain;

        VoiceProfile(
                float generationSpeed,
                float pitch,
                float playbackSpeed,
                float highPassHz,
                float nasalLowHz,
                float nasalHighHz,
                float nasalAmount,
                float drive,
                float compressionThreshold,
                float compressionRatio,
                float outputGain
        ) {
            this.generationSpeed = generationSpeed;
            this.pitch = pitch;
            this.playbackSpeed = playbackSpeed;
            this.highPassHz = highPassHz;
            this.nasalLowHz = nasalLowHz;
            this.nasalHighHz = nasalHighHz;
            this.nasalAmount = nasalAmount;
            this.drive = drive;
            this.compressionThreshold = compressionThreshold;
            this.compressionRatio = compressionRatio;
            this.outputGain = outputGain;
        }
    }
}
