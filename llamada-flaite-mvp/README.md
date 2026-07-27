# Llamada Flaite IA — MVP Android

Aplicación Android de llamada simulada con un personaje chileno ficticio. Escucha un turno mediante SpeechRecognizer, analiza palabras clave y contexto local, responde en tres niveles de lenguaje y reproduce la contestación con TextToSpeech.

## Versión 0.1.0-alpha1

- Interfaz de llamada simulada.
- Reconocimiento de voz en español de Chile.
- Memoria breve de nombres y temas.
- Personalidades: buena onda, desconfiado y choro.
- Intensidad: suave, media e intensa.
- Respuesta hablada con la voz TTS instalada en el teléfono.
- Filtro para no continuar amenazas o daño real.
- No clona voces ni representa a una persona real.

## Limitaciones

Esta primera versión no incluye un modelo de lenguaje embebido. El análisis es contextual y basado en reglas para funcionar sin API privada. El reconocimiento offline depende de los paquetes de voz instalados en el dispositivo.

## Compilar

Desde esta carpeta:

```bash
gradle :app:assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.
