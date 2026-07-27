package cl.medina.llamadaflaite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private boolean callActive = false;
    private boolean muted = false;
    private boolean speakerOn = true;
    private boolean ttsReady = false;
    private boolean waitingForSpeech = false;

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
        replyView = card("Cuando comience la llamada, el personaje reaccionará a lo que digas.", Color.WHITE);
        root.addView(replyView, params(-1, -2, 0, 0, 0, 22));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        root.addView(controls, params(-1, -2, 0, 0, 0, 12));

        speakerButton = controlButton("🔊\nAltavoz");
        micButton = controlButton("🎙\nMicrófono");
        callButton = controlButton("☎\nLlamar");
        callButton.setBackground(rounded(Color.rgb(42, 190, 105), 60));
        controls.addView(speakerButton, weightedButton());
        controls.addView(micButton, weightedButton());
        controls.addView(callButton, weightedButton());

        TextView hint = label("Escucha un turno, responde con voz y vuelve a escuchar automáticamente.", 12, Color.rgb(133, 141, 154));
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, params(-1, -2, 0, 8, 0, 0));

        callButton.setOnClickListener(v -> toggleCall());
        micButton.setOnClickListener(v -> toggleMute());
        speakerButton.setOnClickListener(v -> toggleSpeaker());
        setContentView(scroll);
    }

    private void configureSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.setText("No hay reconocimiento de voz disponible");
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
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
        if (recognizer == null) {
            toast("No hay reconocimiento de voz disponible");
            return;
        }
        callActive = true;
        muted = false;
        engine.reset();
        callButton.setText("✕\nColgar");
        callButton.setBackground(rounded(Color.rgb(224, 67, 67), 60));
        micButton.setText("🎙\nMicrófono");
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.setVisibility(View.VISIBLE);
        chronometer.start();
        statusView.setText("Conectando…");
        String greeting = engine.greeting(personalitySpinner.getSelectedItemPosition(), intensitySpinner.getSelectedItemPosition());
        replyView.setText(greeting);
        speak(greeting);
    }

    private void endCall() {
        callActive = false;
        waitingForSpeech = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.cancel();
        if (tts != null) tts.stop();
        chronometer.stop();
        statusView.setText("Llamada finalizada");
        callButton.setText("☎\nLlamar");
        callButton.setBackground(rounded(Color.rgb(42, 190, 105), 60));
    }

    private void beginListening() {
        if (!callActive || muted || recognizer == null || waitingForSpeech) return;
        waitingForSpeech = true;
        statusView.setText("Te está escuchando…");
        try {
            recognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            waitingForSpeech = false;
            statusView.setText("No se pudo iniciar el micrófono");
        }
    }

    private void speak(String text) {
        if (!callActive) return;
        statusView.setText("El Brayan está hablando…");
        if (!ttsReady) {
            handler.postDelayed(() -> { if (callActive) speak(text); }, 650);
            return;
        }
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
    }

    private void handleRecognizedText(String text) {
        if (!callActive || text == null || text.trim().isEmpty()) return;
        heardView.setText(text);
        String response = engine.reply(text, personalitySpinner.getSelectedItemPosition(), intensitySpinner.getSelectedItemPosition());
        replyView.setText(response);
        speak(response);
    }

    private void toggleMute() {
        muted = !muted;
        if (muted) {
            if (recognizer != null) recognizer.cancel();
            waitingForSpeech = false;
            micButton.setText("🔇\nSilenciado");
            statusView.setText("Micrófono silenciado");
        } else {
            micButton.setText("🎙\nMicrófono");
            if (callActive) beginListening();
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
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            Locale chile = new Locale("es", "CL");
            int result = tts.setLanguage(chile);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(new Locale("es", "ES"));
            }
            tts.setSpeechRate(1.03f);
            tts.setPitch(0.92f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onError(String utteranceId) {
                    handler.post(() -> {
                        statusView.setText("Falló la voz TTS; la respuesta sigue visible");
                        waitingForSpeech = false;
                    });
                }
                @Override public void onDone(String utteranceId) {
                    handler.postDelayed(() -> {
                        waitingForSpeech = false;
                        beginListening();
                    }, 400);
                }
            });
        } else {
            statusView.setText("No se pudo iniciar la voz");
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { statusView.setText("Habla ahora…"); }
    @Override public void onBeginningOfSpeech() { statusView.setText("Analizando lo que dices…"); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { statusView.setText("Pensando la respuesta…"); }

    @Override
    public void onError(int error) {
        waitingForSpeech = false;
        if (!callActive || muted) return;
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            statusView.setText("No escuché bien; intenta otra vez");
            handler.postDelayed(this::beginListening, 700);
        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            handler.postDelayed(this::beginListening, 900);
        } else {
            statusView.setText("Error de voz " + error + "; toca Micrófono para reintentar");
        }
    }

    @Override
    public void onResults(Bundle results) {
        waitingForSpeech = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) handleRecognizedText(matches.get(0));
        else handler.postDelayed(this::beginListening, 600);
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
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (audioManager != null) audioManager.setMode(AudioManager.MODE_NORMAL);
    }

    private TextView sectionTitle(String text) {
        TextView v = label(text, 11, Color.rgb(132, 141, 157));
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private TextView card(String text, int color) {
        TextView v = label(text, 16, color);
        v.setPadding(dp(16), dp(14), dp(16), dp(14));
        v.setMinHeight(dp(68));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setBackground(rounded(Color.rgb(28, 33, 42), 18));
        return v;
    }

    private Button controlButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(Color.rgb(43, 49, 61), 60));
        return b;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(78), 1);
        p.setMargins(dp(5), 0, dp(5), 0);
        return p;
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
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout.LayoutParams params(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private static class ConversationEngine {
        private final Random random = new Random();
        private final Set<String> rememberedNames = new HashSet<>();
        private String lastTopic = "";
        private int turns = 0;

        void reset() { rememberedNames.clear(); lastTopic = ""; turns = 0; }

        String greeting(int personality, int intensity) {
            if (personality == 1) return decorate("Aló… ¿quién habla? ¿Qué necesitái?", intensity);
            if (personality == 2) return decorate("Aló, hermano. Habla al tiro, ¿qué pasó?", intensity);
            return decorate("Aló, hermanito, ¿cómo estai? Cuéntame qué onda.", intensity);
        }

        String reply(String raw, int personality, int intensity) {
            turns++;
            String text = normalize(raw);
            rememberNames(raw);
            updateTopic(text);
            String base;

            if (containsAny(text, "hola", "alo", "buenas", "como estai", "como estas")) {
                base = pick("Wena, hermanito. Aquí estamos, ¿qué pasó?", "Todo bien por acá. Habla nomás.", "Wena po, te escucho. ¿Qué necesitái?");
            } else if (containsAny(text, "quien eres", "quien soi", "como te llamai", "tu nombre")) {
                base = "Soy el Brayan po, pero esta llamada es simulada. ¿Qué querí conversar?";
            } else if (containsAny(text, "conoces", "cachai", "ubicas", "conocis")) {
                String name = latestName();
                base = name.isEmpty() ? pick("Depende de cuál loco hablai. Dame otra pista.", "Puede ser que lo cache, ¿de dónde es el compadre?", "Dime cómo se llama o por dónde se mueve.") : pick("Al " + name + " lo cacho de nombre, pero dime qué pasó.", "Sí po, al " + name + ". ¿Qué onda con ese loco?", "Puede ser que lo ubique. ¿El " + name + " de dónde?");
            } else if (containsAny(text, "moto", "auto", "camioneta", "bicicleta")) {
                base = pick("Ah, ya sé más o menos cuál decí. ¿Y qué hizo ahora?", "Con ese dato lo ubico mejor. ¿Lo andai buscando?", "Ya po, el del vehículo ese. Sigue contando.");
            } else if (containsAny(text, "plata", "deuda", "pagar", "cobrar", "lucas")) {
                base = personality == 1 ? "¿Y por qué me preguntai a mí por esa plata? Explica bien primero." : pick("Ya, pero hablemos claro: ¿cuántas lucas y desde cuándo?", "Chuta, tema de plata entonces. Cuéntame bien.", "¿Es una deuda real o estai practicando la conversación nomás?");
            } else if (containsAny(text, "enojado", "molesto", "rabia", "pelea", "discutir")) {
                base = personality == 2 ? "Ya, pero bájale un cambio. Se puede hablar firme sin dejar la cagá." : "Tranqui, hermano. Cuéntame qué pasó y vemos cómo responder sin calentarse de más.";
            } else if (containsAny(text, "donde", "direccion", "vive", "queda")) {
                base = "No tengo ubicaciones reales de personas. Para la simulación inventemos un lugar y seguimos la llamada.";
            } else if (containsAny(text, "amenaza", "pegar", "matar", "hacerle algo", "arma")) {
                base = "No me meto en amenazas ni daño a nadie. Practiquemos una respuesta firme, pero sin violencia.";
            } else if (containsAny(text, "gracias", "vale", "buena")) {
                base = pick("De nada po, pa eso estamos.", "Buena, hermanito. Cualquier cosa hablai.", "Ya po, quedó clarito entonces.");
            } else if (containsAny(text, "chao", "adios", "nos vemos", "corta")) {
                base = "Ya, nos vimos entonces. Cuídate y no dejí la cagá.";
            } else if (!lastTopic.isEmpty() && turns > 1) {
                base = contextualFollowUp(personality);
            } else {
                base = personalityResponse(personality);
            }
            return decorate(base, intensity);
        }

        private String contextualFollowUp(int personality) {
            if (personality == 1) return pick("Ya, pero eso de " + lastTopic + " no me cuadra mucho. Explícate mejor.", "¿Y cómo sé que lo de " + lastTopic + " es tal como decí?", "Puede ser, pero me faltan datos de " + lastTopic + ".");
            if (personality == 2) return pick("Entonces el tema es " + lastTopic + ". Dilo directo po, ¿qué querí hacer?", "Ya, con lo de " + lastTopic + " estamos claros. ¿Qué viene ahora?", "Mira, por " + lastTopic + " no conviene calentarse; hablemos bien.");
            return pick("Ya, entiendo lo de " + lastTopic + ". ¿Y después qué pasó?", "Mish, entonces todo viene por " + lastTopic + ". Sigue contando.", "Ya po, te sigo. ¿Qué querí preguntarme sobre " + lastTopic + "?");
        }

        private String personalityResponse(int personality) {
            if (personality == 1) return pick("Ya… pero contame la historia completa, no a medias.", "No sé, hermano, suena medio raro. Dame más contexto.", "¿Y por qué querí saber eso? Pregunto nomás.");
            if (personality == 2) return pick("Ya po, habla sin tanta vuelta. ¿Qué pasó exactamente?", "Entiendo, pero dime al tiro qué necesitái.", "Está bien, pero no nos calentemos por las puras.");
            return pick("Mish, ya po. ¿Y qué pasó después?", "Te cacho. Dame un poco más de contexto.", "Ya, hermanito, sigue hablando que te estoy escuchando.");
        }

        private String decorate(String base, int intensity) {
            if (intensity <= 0) return base.replace("hermanito", "compadre").replace("querí", "quieres").replace("necesitái", "necesitas");
            if (intensity == 1) return base;
            String[] prefixes = {"Ya po, ", "Oe, ", "Mira, hermano, ", "Wena, pero "};
            String result = base;
            if (random.nextBoolean() && !base.startsWith("Ya") && !base.startsWith("Oe")) result = prefixes[random.nextInt(prefixes.length)] + lowerFirst(base);
            return result.replace("compadre", "loco").replace("persona", "weón");
        }

        private void rememberNames(String raw) {
            Matcher m = Pattern.compile("\\b(?:el|la|al)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,})\\b").matcher(raw);
            while (m.find()) {
                String candidate = m.group(1);
                if (!candidate.equalsIgnoreCase("Brayan")) rememberedNames.add(candidate);
            }
        }

        private String latestName() {
            String latest = "";
            for (String name : rememberedNames) latest = name;
            return latest;
        }

        private void updateTopic(String text) {
            if (containsAny(text, "moto", "auto", "camioneta")) lastTopic = "el vehículo";
            else if (containsAny(text, "plata", "deuda", "lucas")) lastTopic = "la plata";
            else if (containsAny(text, "trabajo", "pega", "jefe")) lastTopic = "la pega";
            else if (containsAny(text, "vecino", "barrio", "cancha")) lastTopic = "el barrio";
            else if (containsAny(text, "pareja", "polola", "pololo")) lastTopic = "la relación";
            else if (text.length() > 12) {
                String[] words = text.split(" ");
                if (words.length >= 2) lastTopic = words[words.length - 2] + " " + words[words.length - 1];
            }
        }

        private boolean containsAny(String text, String... terms) {
            for (String term : terms) if (text.contains(term)) return true;
            return false;
        }

        private String pick(String... values) { return values[random.nextInt(values.length)]; }

        private String normalize(String value) {
            String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
            return normalized.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9ñáéíóúü ]", " ").replaceAll("\\s+", " ").trim();
        }

        private String lowerFirst(String text) {
            if (text.isEmpty()) return text;
            return Character.toLowerCase(text.charAt(0)) + text.substring(1);
        }
    }
}
