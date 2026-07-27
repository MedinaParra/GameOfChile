package cl.medina.llamadaflaite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO = 41;
    private static final String UTTERANCE_ID = "flaite_reply";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ConversationEngine engine = new ConversationEngine();

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextToSpeech tts;
    private AudioManager audioManager;

    private TextView statusView;
    private TextView heardView;
    private TextView replyView;
    private Chronometer chronometer;
    private Spinner intensitySpinner;
    private Spinner personalitySpinner;
    private Button callButton;
    private Button micButton;
    private Button speakerButton;

    private boolean callActive;
    private boolean muted;
    private boolean speakerOn = true;
    private boolean ttsReady;
    private boolean listening;
    private boolean ttsSpeaking;
    private int consecutiveRecognizerErrors;

    private final Runnable ttsSafetyFallback = () -> {
        if (!callActive || !ttsSpeaking) return;
        ttsSpeaking = false;
        statusView.setText("La voz no confirmó el término; te escucho igual…");
        startListeningSoon(250);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 19, 24));
        window.setNavigationBarColor(Color.rgb(16, 19, 24));
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createUi();
        configureSpeech();
        tts = new TextToSpeech(this, this);
    }

    private void createUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 19, 24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView badge = label("SIMULACIÓN · PERSONAJE FICTICIO", 12, Color.rgb(108, 229, 165));
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

        TextView subtitle = label("personaje chileno ficticio", 14, Color.rgb(174, 180, 190));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, params(-1, -2, 0, 0, 0, 8));

        chronometer = new Chronometer(this);
        chronometer.setTextColor(Color.rgb(218, 222, 230));
        chronometer.setTextSize(17);
        chronometer.setGravity(Gravity.CENTER);
        chronometer.setFormat("%s");
        chronometer.setVisibility(View.INVISIBLE);
        root.addView(chronometer, params(-1, dp(34), 0, 0, 0, 8));

        statusView = label("Listo para iniciar la llamada", 15, Color.rgb(108, 229, 165));
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

        root.addView(sectionTitle("LO QUE ESCUCHÓ"), params(-1, -2, 0, 0, 0, 7));
        heardView = card("Todavía no has hablado.", Color.rgb(199, 205, 216));
        root.addView(heardView, params(-1, -2, 0, 0, 0, 15));

        root.addView(sectionTitle("RESPUESTA DEL PERSONAJE"), params(-1, -2, 0, 0, 0, 7));
        replyView = card("Inicia la llamada y espera a que diga: Escuchando…", Color.WHITE);
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

        TextView hint = label("Toca Hablar para forzar un nuevo turno. Mantén presionado para silenciar.", 12, Color.rgb(133, 141, 154));
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
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
        createRecognizer();
    }

    private void createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = null;
            if (statusView != null) statusView.setText("Android no tiene un servicio de reconocimiento de voz activo");
            return;
        }
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
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
        if (recognizer == null) createRecognizer();
        if (recognizer == null) {
            toast("Activa el reconocimiento de voz de Google o del fabricante");
            return;
        }
        callActive = true;
        muted = false;
        listening = false;
        ttsSpeaking = false;
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
        speakOrListen(greeting);
    }

    private void endCall() {
        callActive = false;
        listening = false;
        ttsSpeaking = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
        }
        if (tts != null) tts.stop();
        chronometer.stop();
        statusView.setText("Llamada finalizada");
        callButton.setText("☎\nLlamar");
        callButton.setBackground(rounded(Color.rgb(42, 190, 105), 60));
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
        if (ttsSpeaking && tts != null) {
            tts.stop();
            ttsSpeaking = false;
        }
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            listening = false;
        }
        startListeningSoon(250);
    }

    private void startListeningSoon(long delayMs) {
        handler.postDelayed(this::beginListening, delayMs);
    }

    private void beginListening() {
        if (!callActive || muted || listening || ttsSpeaking) return;
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
            }, 500);
        }
    }

    private void speakOrListen(String text) {
        if (!callActive) return;
        if (!ttsReady || tts == null) {
            ttsSpeaking = false;
            statusView.setText("Voz TTS no disponible; te escucho igual…");
            startListeningSoon(350);
            return;
        }
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            listening = false;
        }
        ttsSpeaking = true;
        statusView.setText("El Brayan está hablando…");
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID);
        if (result == TextToSpeech.ERROR) {
            ttsSpeaking = false;
            statusView.setText("Falló la voz; te escucho igual…");
            startListeningSoon(350);
            return;
        }
        handler.removeCallbacks(ttsSafetyFallback);
        long estimatedMs = Math.max(2500L, Math.min(8000L, text.length() * 70L));
        handler.postDelayed(ttsSafetyFallback, estimatedMs);
    }

    private void handleRecognizedText(String text) {
        if (!callActive || text == null || text.trim().isEmpty()) {
            startListeningSoon(500);
            return;
        }
        consecutiveRecognizerErrors = 0;
        heardView.setText(text);
        statusView.setText("Analizando lo que dijiste…");
        String response = engine.reply(text, personalitySpinner.getSelectedItemPosition(), intensitySpinner.getSelectedItemPosition());
        replyView.setText(response);
        speakOrListen(response);
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
            if (callActive) startListeningSoon(200);
        }
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(speakerOn);
        speakerButton.setText(speakerOn ? "🔊\nAltavoz" : "🔈\nAuricular");
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            ttsReady = false;
            if (callActive) startListeningSoon(250);
            return;
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        Locale chile = new Locale("es", "CL");
        int languageResult = tts.setLanguage(chile);
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            languageResult = tts.setLanguage(new Locale("es", "ES"));
        }
        ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
        tts.setSpeechRate(1.03f);
        tts.setPitch(0.92f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override public void onError(String utteranceId) {
                handler.post(() -> {
                    handler.removeCallbacks(ttsSafetyFallback);
                    ttsSpeaking = false;
                    if (callActive) {
                        statusView.setText("La voz falló; te escucho igual…");
                        startListeningSoon(300);
                    }
                });
            }

            @Override public void onDone(String utteranceId) {
                handler.post(() -> {
                    handler.removeCallbacks(ttsSafetyFallback);
                    ttsSpeaking = false;
                    if (callActive) startListeningSoon(300);
                });
            }
        });
        if (!ttsReady && callActive) startListeningSoon(250);
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
        if (!callActive || muted) return;
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
        long retry = Math.min(2200L, 500L + consecutiveRecognizerErrors * 250L);
        startListeningSoon(retry);
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        micButton.setText("🎙\nHablar");
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) handleRecognizedText(matches.get(0));
        else startListeningSoon(500);
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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