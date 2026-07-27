package cl.medina.llamadaflaite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends Activity implements RecognitionListener {
    private static final int REQ_AUDIO = 41;
    private static final String PREFS = "voice_engine";
    private static final String PREF_BACKEND = "backend_url";
    private static final String PREF_TOKEN = "app_token";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ConversationEngine engine = new ConversationEngine();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private Call activeSpeechCall;
    private SharedPreferences preferences;

    private TextView statusView;
    private TextView heardView;
    private TextView replyView;
    private Chronometer chronometer;
    private Spinner intensitySpinner;
    private Spinner personalitySpinner;
    private EditText backendInput;
    private EditText tokenInput;
    private Button callButton;
    private Button micButton;
    private Button speakerButton;

    private boolean callActive;
    private boolean muted;
    private boolean speakerOn = true;
    private boolean listening;
    private boolean audioSpeaking;
    private int consecutiveRecognizerErrors;
    private int speechSequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 19, 24));
        window.setNavigationBarColor(Color.rgb(16, 19, 24));
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        createUi();
        configureSpeech();
    }

    private void createUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 19, 24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView badge = label("SIMULACIÓN · VOZ GENERADA POR IA", 12, Color.rgb(108, 229, 165));
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(rounded(Color.rgb(31, 49, 43), 30));
        root.addView(badge, params(-2, dp(34), 0, 0, 0, 18));

        TextView avatar = label("B", 50, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setBackgroundResource(R.drawable.circle_avatar);
        root.addView(avatar, params(dp(118), dp(118), 0, 0, 0, 16));

        TextView name = label("El Brayan", 30, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        root.addView(name, params(-1, -2, 0, 0, 0, 4));

        TextView subtitle = label("personaje chileno ficticio · motor neural alpha3", 14, Color.rgb(174, 180, 190));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, params(-1, -2, 0, 0, 0, 8));

        chronometer = new Chronometer(this);
        chronometer.setTextColor(Color.rgb(218, 222, 230));
        chronometer.setTextSize(17);
        chronometer.setGravity(Gravity.CENTER);
        chronometer.setFormat("%s");
        chronometer.setVisibility(View.INVISIBLE);
        root.addView(chronometer, params(-1, dp(34), 0, 0, 0, 8));

        statusView = label("Configura el servidor de voz neuronal", 15, Color.rgb(108, 229, 165));
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView, params(-1, -2, 0, 0, 0, 18));

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        options.setGravity(Gravity.CENTER);
        root.addView(options, params(-1, -2, 0, 0, 0, 14));

        intensitySpinner = new Spinner(this);
        personalitySpinner = new Spinner(this);
        setSpinner(intensitySpinner, new String[]{"Flaite suave", "Flaite medio", "Flaite intenso"});
        setSpinner(personalitySpinner, new String[]{"Buena onda", "Desconfiado", "Choro"});
        options.addView(intensitySpinner, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(52), 1);
        right.leftMargin = dp(8);
        options.addView(personalitySpinner, right);

        root.addView(sectionTitle("MOTOR DE VOZ NEURONAL"), params(-1, -2, 0, 0, 0, 7));
        backendInput = inputField("https://tu-servidor.example.com");
        backendInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        backendInput.setText(preferences.getString(PREF_BACKEND, ""));
        root.addView(backendInput, params(-1, dp(54), 0, 0, 0, 8));

        tokenInput = inputField("Token privado del servidor (opcional)");
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setText(preferences.getString(PREF_TOKEN, ""));
        root.addView(tokenInput, params(-1, dp(54), 0, 0, 0, 8));

        Button saveEngineButton = new Button(this);
        saveEngineButton.setText("Guardar motor de voz");
        saveEngineButton.setAllCaps(false);
        saveEngineButton.setTextColor(Color.WHITE);
        saveEngineButton.setBackground(rounded(Color.rgb(62, 89, 158), 18));
        root.addView(saveEngineButton, params(-1, dp(50), 0, 0, 0, 8));
        saveEngineButton.setOnClickListener(v -> saveVoiceConfiguration());

        TextView engineHint = label("La app ya no usa la voz robótica de Android. El servidor genera MP3 neuronal y mantiene la clave de IA fuera del teléfono.", 12, Color.rgb(133, 141, 154));
        engineHint.setGravity(Gravity.CENTER);
        root.addView(engineHint, params(-1, -2, 4, 0, 4, 16));

        root.addView(sectionTitle("LO QUE ESCUCHÓ"), params(-1, -2, 0, 0, 0, 7));
        heardView = card("Todavía no has hablado.", Color.rgb(199, 205, 216));
        root.addView(heardView, params(-1, -2, 0, 0, 0, 15));

        root.addView(sectionTitle("RESPUESTA DEL PERSONAJE"), params(-1, -2, 0, 0, 0, 7));
        replyView = card("La respuesta aparecerá aquí y se reproducirá mediante el motor neuronal.", Color.WHITE);
        root.addView(replyView, params(-1, -2, 0, 0, 0, 22));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        root.addView(controls, params(-1, -2, 0, 0, 0, 12));

        speakerButton = controlButton("🔊\nAltavoz");
        micButton = controlButton("🎙\nHablar");
        callButton = controlButton("☎\nLlamar");
        callButton.setBackground(rounded(Color.rgb(42, 190, 105), 60));
        controls.addView(speakerButton, weightedButton());
        controls.addView(micButton, weightedButton());
        controls.addView(callButton, weightedButton());

        TextView hint = label("Toca Hablar para interrumpir la voz y abrir un nuevo turno. Mantén presionado para silenciar.", 12, Color.rgb(133, 141, 154));
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, params(-1, -2, 0, 8, 0, 0));

        callButton.setOnClickListener(v -> toggleCall());
        micButton.setOnClickListener(v -> manualListen());
        micButton.setOnLongClickListener(v -> {
            toggleMute();
            return true;
        });
        speakerButton.setOnClickListener(v -> toggleSpeaker());
        setContentView(scroll);
    }

    private void configureSpeech() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1050L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 450L);
        createRecognizer();
    }

    private void createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = null;
            if (statusView != null) statusView.setText("Android no tiene reconocimiento de voz activo");
            return;
        }
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
    }

    private void saveVoiceConfiguration() {
        String backend = normalizeBackendUrl(backendInput.getText().toString());
        if (backend.isEmpty() || (!backend.startsWith("https://") && !backend.startsWith("http://"))) {
            statusView.setText("Escribe una URL válida del servidor de voz");
            toast("La URL debe comenzar con https:// o http://");
            return;
        }
        preferences.edit()
                .putString(PREF_BACKEND, backend)
                .putString(PREF_TOKEN, tokenInput.getText().toString().trim())
                .apply();
        backendInput.setText(backend);
        statusView.setText("Motor neuronal guardado. Ya puedes llamar");
    }

    private void toggleCall() {
        if (callActive) {
            endCall();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startCall();
    }

    private void startCall() {
        String backend = currentBackendUrl();
        if (backend.isEmpty()) {
            statusView.setText("Configura y guarda el servidor de voz primero");
            toast("Falta la URL del motor neuronal");
            return;
        }
        if (recognizer == null) createRecognizer();
        if (recognizer == null) {
            toast("Activa el reconocimiento de voz de Google o del fabricante");
            return;
        }
        callActive = true;
        muted = false;
        listening = false;
        audioSpeaking = false;
        consecutiveRecognizerErrors = 0;
        engine.reset();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(speakerOn);
        callButton.setText("✕\nColgar");
        callButton.setBackground(rounded(Color.rgb(224, 67, 67), 60));
        micButton.setText("🎙\nHablar");
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.setVisibility(View.VISIBLE);
        chronometer.start();
        heardView.setText("Esperando tu voz…");
        String greeting = engine.greeting(personalitySpinner.getSelectedItemPosition(), intensitySpinner.getSelectedItemPosition());
        replyView.setText(greeting);
        speakWithNeuralEngine(greeting);
    }

    private void endCall() {
        callActive = false;
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
        }
        stopVoiceOutput();
        chronometer.stop();
        statusView.setText("Llamada finalizada");
        callButton.setText("☎\nLlamar");
        callButton.setBackground(rounded(Color.rgb(42, 190, 105), 60));
        micButton.setText("🎙\nHablar");
        audioManager.setMode(AudioManager.MODE_NORMAL);
    }

    private void manualListen() {
        if (!callActive) {
            toast("Primero inicia la llamada");
            return;
        }
        if (muted) {
            muted = false;
            micButton.setText("🎙\nHablar");
        }
        stopVoiceOutput();
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            listening = false;
        }
        startListeningSoon(180);
    }

    private void startListeningSoon(long delayMs) {
        handler.postDelayed(this::beginListening, delayMs);
    }

    private void beginListening() {
        if (!callActive || muted || listening || audioSpeaking) return;
        if (recognizer == null) createRecognizer();
        if (recognizer == null) {
            statusView.setText("Sin reconocimiento de voz. Revisa el servicio de Google");
            return;
        }
        listening = true;
        statusView.setText("Escuchando… habla ahora");
        micButton.setText("🎙\nEscuchando");
        try {
            recognizer.startListening(recognizerIntent);
        } catch (Exception firstError) {
            listening = false;
            createRecognizer();
            handler.postDelayed(() -> {
                if (!callActive || recognizer == null) return;
                try {
                    listening = true;
                    recognizer.startListening(recognizerIntent);
                } catch (Exception secondError) {
                    listening = false;
                    statusView.setText("No pude abrir el micrófono. Toca Hablar para reintentar");
                }
            }, 450);
        }
    }

    private void speakWithNeuralEngine(String text) {
        if (!callActive) return;
        String backend = currentBackendUrl();
        if (backend.isEmpty()) {
            statusView.setText("Motor neural sin configurar; respuesta solo escrita");
            startListeningSoon(300);
            return;
        }
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            listening = false;
        }
        stopVoiceOutput();
        audioSpeaking = true;
        micButton.setText("⏳\nGenerando");
        statusView.setText("Generando voz neuronal…");
        int requestSequence = ++speechSequence;

        try {
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            payload.put("personality", personalitySpinner.getSelectedItem().toString());
            payload.put("intensity", intensitySpinner.getSelectedItem().toString());

            RequestBody requestBody = RequestBody.create(payload.toString(), JSON);
            Request.Builder builder = new Request.Builder()
                    .url(backend + "/v1/speech")
                    .post(requestBody)
                    .header("Accept", "audio/mpeg");
            String token = tokenInput.getText().toString().trim();
            if (!token.isEmpty()) builder.header("X-App-Token", token);

            activeSpeechCall = httpClient.newCall(builder.build());
            activeSpeechCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException error) {
                    handler.post(() -> neuralSpeechFailed(requestSequence, readableError(error)));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response safeResponse = response) {
                        ResponseBody body = safeResponse.body();
                        if (!safeResponse.isSuccessful() || body == null) {
                            String message = "HTTP " + safeResponse.code();
                            if (body != null && body.contentType() != null && body.contentType().toString().contains("json")) {
                                String detail = body.string();
                                if (!detail.isBlank()) message += ": " + detail;
                            }
                            String finalMessage = message;
                            handler.post(() -> neuralSpeechFailed(requestSequence, finalMessage));
                            return;
                        }
                        byte[] audio = body.bytes();
                        File file = File.createTempFile("brayan-neural-", ".mp3", getCacheDir());
                        try (FileOutputStream output = new FileOutputStream(file)) {
                            output.write(audio);
                        }
                        handler.post(() -> playNeuralAudio(file, requestSequence));
                    } catch (Exception error) {
                        handler.post(() -> neuralSpeechFailed(requestSequence, readableError(error)));
                    }
                }
            });
        } catch (Exception error) {
            neuralSpeechFailed(requestSequence, readableError(error));
        }
    }

    private void playNeuralAudio(File audioFile, int requestSequence) {
        if (!callActive || requestSequence != speechSequence) {
            audioFile.delete();
            return;
        }
        releaseMediaPlayer();
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(audioFile.getAbsolutePath());
            player.setOnPreparedListener(readyPlayer -> {
                if (!callActive || requestSequence != speechSequence) {
                    releaseMediaPlayer();
                    audioFile.delete();
                    return;
                }
                audioSpeaking = true;
                statusView.setText("El Brayan está hablando con voz neural…");
                micButton.setText("✋\nInterrumpir");
                readyPlayer.start();
            });
            player.setOnCompletionListener(completedPlayer -> {
                audioFile.delete();
                releaseMediaPlayer();
                audioSpeaking = false;
                micButton.setText("🎙\nHablar");
                if (callActive) startListeningSoon(220);
            });
            player.setOnErrorListener((failedPlayer, what, extra) -> {
                audioFile.delete();
                releaseMediaPlayer();
                neuralSpeechFailed(requestSequence, "reproducción de audio " + what);
                return true;
            });
            mediaPlayer = player;
            player.prepareAsync();
        } catch (Exception error) {
            audioFile.delete();
            neuralSpeechFailed(requestSequence, readableError(error));
        }
    }

    private void neuralSpeechFailed(int requestSequence, String reason) {
        if (requestSequence != speechSequence) return;
        audioSpeaking = false;
        activeSpeechCall = null;
        releaseMediaPlayer();
        micButton.setText("🎙\nHablar");
        if (!callActive) return;
        String compactReason = reason == null ? "error desconocido" : reason;
        if (compactReason.length() > 120) compactReason = compactReason.substring(0, 120);
        statusView.setText("Motor neural no respondió: " + compactReason);
        startListeningSoon(450);
    }

    private void stopVoiceOutput() {
        speechSequence++;
        audioSpeaking = false;
        if (activeSpeechCall != null) {
            activeSpeechCall.cancel();
            activeSpeechCall = null;
        }
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer == null) return;
        try {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
        } catch (Exception ignored) { }
        try { mediaPlayer.reset(); } catch (Exception ignored) { }
        try { mediaPlayer.release(); } catch (Exception ignored) { }
        mediaPlayer = null;
    }

    private void handleRecognizedText(String text) {
        if (!callActive || text == null || text.trim().isEmpty()) {
            startListeningSoon(450);
            return;
        }
        consecutiveRecognizerErrors = 0;
        heardView.setText(text);
        statusView.setText("Analizando lo que dijiste…");
        String response = engine.reply(text, personalitySpinner.getSelectedItemPosition(), intensitySpinner.getSelectedItemPosition());
        replyView.setText(response);
        speakWithNeuralEngine(response);
    }

    private void toggleMute() {
        muted = !muted;
        if (muted) {
            if (recognizer != null) {
                try { recognizer.cancel(); } catch (Exception ignored) { }
            }
            listening = false;
            micButton.setText("🔇\nSilenciado");
            statusView.setText("Micrófono silenciado");
        } else {
            micButton.setText("🎙\nHablar");
            if (callActive && !audioSpeaking) startListeningSoon(180);
        }
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(speakerOn);
        speakerButton.setText(speakerOn ? "🔊\nAltavoz" : "🔈\nAuricular");
    }

    private String currentBackendUrl() {
        String typed = backendInput == null ? "" : backendInput.getText().toString();
        String normalized = normalizeBackendUrl(typed);
        if (!normalized.isEmpty()) return normalized;
        return normalizeBackendUrl(preferences.getString(PREF_BACKEND, ""));
    }

    private String normalizeBackendUrl(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private String readableError(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @Override public void onReadyForSpeech(Bundle params) {
        statusView.setText("Escuchando… habla ahora");
        micButton.setText("🎙\nEscuchando");
    }

    @Override public void onBeginningOfSpeech() {
        statusView.setText("Te escuché; sigue hablando…");
    }

    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { statusView.setText("Pensando la respuesta…"); }

    @Override
    public void onError(int error) {
        listening = false;
        micButton.setText("🎙\nHablar");
        if (!callActive || muted || audioSpeaking) return;
        consecutiveRecognizerErrors++;

        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            statusView.setText("Falta permiso de micrófono");
            return;
        }
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
            createRecognizer();
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            statusView.setText("No entendí; vuelve a hablar…");
        } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
            statusView.setText("Problema de red del reconocedor; reintentando…");
        } else {
            statusView.setText("Reiniciando reconocimiento de voz…");
        }
        long retry = Math.min(2200L, 450L + consecutiveRecognizerErrors * 250L);
        startListeningSoon(retry);
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        micButton.setText("🎙\nHablar");
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) handleRecognizedText(matches.get(0));
        else startListeningSoon(450);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) heardView.setText(matches.get(0) + "…");
    }

    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCall();
        } else if (requestCode == REQ_AUDIO) {
            toast("Necesito permiso de micrófono para simular la llamada");
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.destroy();
        stopVoiceOutput();
        httpClient.dispatcher().cancelAll();
        httpClient.connectionPool().evictAll();
        if (audioManager != null) audioManager.setMode(AudioManager.MODE_NORMAL);
        super.onDestroy();
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text, 11, Color.rgb(132, 141, 157));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView card(String text, int color) {
        TextView view = label(text, 16, color);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setMinHeight(dp(68));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackground(rounded(Color.rgb(28, 33, 42), 18));
        return view;
    }

    private EditText inputField(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(120, 128, 142));
        input.setTextColor(Color.WHITE);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.rgb(28, 33, 42), 16));
        return input;
    }

    private Button controlButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.rgb(43, 49, 61), 60));
        return button;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(78), 1);
        params.setMargins(dp(5), 0, dp(5), 0);
        return params;
    }

    private void setSpinner(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
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

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
