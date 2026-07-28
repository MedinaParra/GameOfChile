package cl.medina.flaitevoice;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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
    private Button speakButton;
    private Button randomButton;
    private Button stopButton;

    private final String[] samplePhrases = {
            "Oe, hermano, ¿qué weá querí? Habla al toque po, que ando terrible ocupao.",
            "Ya po, weón, no me vengái con cuentos. Decime la firme al tiro nomás.",
            "Wena, compare. ¿Dónde andái metío? Hace caleta que no dai señales.",
            "Oe, loco, te estoy preguntando en buena. ¿Vai a venir o vai a puro dar jugo?",
            "Mira, hermano, la cuestión es cortita: habla claro y no le pongái tanto color.",
            "Aló... ¿quién habla? Oe, si vai a decir una weá, decila al tiro po."
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

        TextView badge = label("100% LOCAL · SIN PC · SIN API", 12, Color.rgb(108, 229, 165));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(16), 0, dp(16), 0);
        badge.setBackground(rounded(Color.rgb(31, 49, 43), 30));
        root.addView(badge, params(-2, dp(36), 0, 0, 0, 18));

        TextView title = label("Laboratorio de voz flaite", 27, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params(-1, -2, 0, 0, 0, 6));

        TextView subtitle = label("Prueba diez voces neuronales dentro del teléfono", 14, Color.rgb(174, 180, 190));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, params(-1, -2, 0, 0, 0, 18));

        statusView = card("Cargando modelo neuronal local… La primera carga puede ser lenta.", Color.rgb(108, 229, 165));
        root.addView(statusView, params(-1, -2, 0, 0, 0, 16));

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
        setSpinner(intensitySpinner, new String[]{"Flaite suave", "Flaite medio", "Flaite brígido"});
        speakerSpinner.setSelection(6);
        intensitySpinner.setSelection(2);
        optionsTop.addView(speakerSpinner, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(54), 1);
        second.leftMargin = dp(8);
        optionsTop.addView(intensitySpinner, second);

        speedSpinner = new Spinner(this);
        setSpinner(speedSpinner, new String[]{"Calmado 0,92×", "Natural 1,00×", "Acelerado 1,10×", "Terrible rápido 1,18×"});
        speedSpinner.setSelection(2);
        root.addView(speedSpinner, params(-1, dp(54), 0, 0, 0, 15));

        root.addView(sectionTitle("ASÍ LO VA A PRONUNCIAR"), params(-1, -2, 0, 0, 0, 7));
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

        TextView note = label("Esta alpha prueba solo la voz. El modelo viene dentro de la APK y después de instalar funciona sin internet.", 12, Color.rgb(133, 141, 154));
        note.setGravity(Gravity.CENTER);
        root.addView(note, params(-1, -2, 0, 4, 0, 0));

        speakButton.setOnClickListener(v -> synthesize());
        randomButton.setOnClickListener(v -> {
            inputView.setText(samplePhrases[random.nextInt(samplePhrases.length)]);
            spokenView.setText("Pulsa Hablar para aplicar la actuación flaite.");
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
                    statusView.setText("Modelo listo. Prueba las voces 0–9; algunas son más graves que otras.");
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
        float speed = selectedSpeed();
        String performedText = makeFlaite(source, intensity);
        spokenView.setText(performedText);
        generating = true;
        stopRequested = false;
        speakButton.setEnabled(false);
        statusView.setText("Generando voz local…");

        executor.execute(() -> {
            try {
                GenerationConfig generation = new GenerationConfig(
                        0.10f,
                        speed,
                        speaker,
                        null,
                        0,
                        null,
                        8,
                        Collections.singletonMap("lang", "es")
                );
                GeneratedAudio audio = tts.generateWithConfig(performedText, generation);
                if (audio.getSamples().length == 0) {
                    throw new IllegalStateException("el modelo no produjo audio");
                }
                runOnUiThread(() -> statusView.setText("Hablando con Voz " + speaker + "…"));
                playFloatAudio(audio.getSamples(), audio.getSampleRate());
                runOnUiThread(() -> statusView.setText(stopRequested ? "Reproducción detenida." : "Listo. Cambia la voz y compara."));
            } catch (Throwable error) {
                runOnUiThread(() -> statusView.setText("Falló la generación local: " + safeMessage(error)));
            } finally {
                generating = false;
                runOnUiThread(() -> speakButton.setEnabled(tts != null));
            }
        });
    }

    private void playFloatAudio(float[] samples, int sampleRate) {
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
                .replaceAll("(?i)\\bpara\\b", "pa")
                .replaceAll("(?i)\\bquieres\\b", "querí")
                .replaceAll("(?i)\\bquieras\\b", "querái")
                .replaceAll("(?i)\\bestás\\b", "estái")
                .replaceAll("(?i)\\bpuedes\\b", "podí")
                .replaceAll("(?i)\\bvas\\b", "vai")
                .replaceAll("(?i)\\bdices\\b", "decí")
                .replaceAll("(?i)\\bdime\\b", "decime")
                .replaceAll("(?i)\\bocupado\\b", "ocupao")
                .replaceAll("(?i)\\bmetido\\b", "metío")
                .replaceAll("(?i)\\bnada\\b", "ná")
                .replaceAll("(?i)\\bentonces\\b", "entonce")
                .replaceAll("(?i)\\bamigo\\b", "compare")
                .replaceAll("(?i)\\bpersona\\b", "loco");

        if (intensity == 0) {
            return ensurePrefix(text, "Oe, compare… ") + " Po.";
        }
        if (intensity == 1) {
            return ensurePrefix(text, "Wena, hermano… ") + " Ya po.";
        }

        String[] starts = {
                "Oe, weón… mira… ",
                "Ya po, hermano… ",
                "Wena, compare… oe… ",
                "Mira, loco… la weá es así… "
        };
        String result = ensurePrefix(text, starts[random.nextInt(starts.length)]);
        result = result.replace(".", "… ").replace("?", "… ¿cachái?");
        if (!result.toLowerCase(Locale.ROOT).contains("po")) result += " po";
        return result.trim();
    }

    private String ensurePrefix(String text, String prefix) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("oe") || lower.startsWith("wena") || lower.startsWith("ya po") || lower.startsWith("mira")) {
            return text;
        }
        return prefix + Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private float selectedSpeed() {
        switch (speedSpinner.getSelectedItemPosition()) {
            case 0: return 0.92f;
            case 2: return 1.10f;
            case 3: return 1.18f;
            default: return 1.0f;
        }
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
}
