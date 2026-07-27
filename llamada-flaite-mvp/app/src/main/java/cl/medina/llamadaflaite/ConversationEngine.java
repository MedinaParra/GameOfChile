package cl.medina.llamadaflaite;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConversationEngine {
    private final Random random = new Random();
    private final Set<String> rememberedNames = new HashSet<>();
    private String lastTopic = "";
    private int turns;

    void reset() {
        rememberedNames.clear();
        lastTopic = "";
        turns = 0;
    }

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
            base = name.isEmpty()
                    ? pick("Depende de cuál loco hablai. Dame otra pista.", "Puede ser que lo cache, ¿de dónde es el compadre?", "Dime cómo se llama o por dónde se mueve.")
                    : pick("Al " + name + " lo cacho de nombre, pero dime qué pasó.", "Sí po, al " + name + ". ¿Qué onda con ese loco?", "Puede ser que lo ubique. ¿El " + name + " de dónde?");
        } else if (containsAny(text, "moto", "auto", "camioneta", "bicicleta")) {
            base = pick("Ah, ya sé más o menos cuál decí. ¿Y qué hizo ahora?", "Con ese dato lo ubico mejor. ¿Lo andai buscando?", "Ya po, el del vehículo ese. Sigue contando.");
        } else if (containsAny(text, "plata", "deuda", "pagar", "cobrar", "lucas")) {
            base = personality == 1
                    ? "¿Y por qué me preguntai a mí por esa plata? Explica bien primero."
                    : pick("Ya, pero hablemos claro: ¿cuántas lucas y desde cuándo?", "Chuta, tema de plata entonces. Cuéntame bien.", "¿Es una deuda real o estai practicando la conversación nomás?");
        } else if (containsAny(text, "enojado", "molesto", "rabia", "pelea", "discutir")) {
            base = personality == 2
                    ? "Ya, pero bájale un cambio. Se puede hablar firme sin dejar la cagá."
                    : "Tranqui, hermano. Cuéntame qué pasó y vemos cómo responder sin calentarse de más.";
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
        if (random.nextBoolean() && !base.startsWith("Ya") && !base.startsWith("Oe")) {
            result = prefixes[random.nextInt(prefixes.length)] + lowerFirst(base);
        }
        return result.replace("compadre", "loco").replace("persona", "weón");
    }

    private void rememberNames(String raw) {
        Matcher matcher = Pattern.compile("\\b(?:el|la|al)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,})\\b").matcher(raw);
        while (matcher.find()) {
            String candidate = matcher.group(1);
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

    private String pick(String... values) {
        return values[random.nextInt(values.length)];
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9ñáéíóúü ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String lowerFirst(String text) {
        if (text.isEmpty()) return text;
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
