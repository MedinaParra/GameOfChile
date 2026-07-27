import http from "node:http";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.OPENAI_API_KEY || "";
const appToken = process.env.APP_TOKEN || "";
const voiceModel = process.env.VOICE_MODEL || "gpt-4o-mini-tts";
const voiceName = process.env.VOICE_NAME || "cedar";

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, X-App-Token",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
  });
  res.end(body);
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.setEncoding("utf8");
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 16_000) reject(new Error("request_too_large"));
    });
    req.on("end", () => {
      try {
        resolve(JSON.parse(raw || "{}"));
      } catch {
        reject(new Error("invalid_json"));
      }
    });
    req.on("error", reject);
  });
}

function voiceInstructions(personality, intensity) {
  const personalityLine = {
    "Buena onda": "Suena cercano, relajado y ligeramente divertido.",
    "Desconfiado": "Suena cauteloso, con pausas cortas y algo de sospecha.",
    "Choro": "Suena seguro, directo y callejero, sin gritar ni amenazar."
  }[personality] || "Suena cercano y espontáneo.";

  const intensityLine = {
    "Flaite suave": "Usa modismos chilenos suaves y pronunciación natural.",
    "Flaite medio": "Usa más contracciones chilenas y ritmo de conversación callejera.",
    "Flaite intenso": "Usa habla chilena muy coloquial, rápida e imperfecta, pero siempre comprensible."
  }[intensity] || "Usa español chileno coloquial.";

  return [
    "Interpreta a un hombre chileno joven ficticio en una llamada telefónica simulada.",
    "Habla como una persona real, no como locutor, narrador, asistente ni GPS.",
    "Usa ritmo irregular, micro pausas, respiración leve, finales de frase relajados y emoción contenida.",
    "No sobreactúes el acento ni pronuncies cada sílaba perfectamente.",
    "Las respuestas deben sonar improvisadas, breves y conversacionales.",
    personalityLine,
    intensityLine
  ].join(" ");
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Content-Type, X-App-Token",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
    });
    res.end();
    return;
  }

  if (req.method === "GET" && req.url === "/health") {
    sendJson(res, 200, { ok: true, model: voiceModel, voice: voiceName });
    return;
  }

  if (req.method !== "POST" || req.url !== "/v1/speech") {
    sendJson(res, 404, { error: "not_found" });
    return;
  }

  if (!apiKey) {
    sendJson(res, 503, { error: "OPENAI_API_KEY_not_configured" });
    return;
  }

  if (appToken && req.headers["x-app-token"] !== appToken) {
    sendJson(res, 401, { error: "invalid_app_token" });
    return;
  }

  try {
    const payload = await readJson(req);
    const text = String(payload.text || "").trim();
    if (!text || text.length > 900) {
      sendJson(res, 400, { error: "text_required_or_too_long" });
      return;
    }

    const upstream = await fetch("https://api.openai.com/v1/audio/speech", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: voiceModel,
        voice: voiceName,
        input: text,
        instructions: voiceInstructions(payload.personality, payload.intensity),
        response_format: "mp3",
        speed: 1.04
      })
    });

    if (!upstream.ok) {
      const detail = await upstream.text();
      sendJson(res, upstream.status, {
        error: "voice_provider_error",
        detail: detail.slice(0, 800)
      });
      return;
    }

    const audio = Buffer.from(await upstream.arrayBuffer());
    res.writeHead(200, {
      "Content-Type": "audio/mpeg",
      "Content-Length": audio.length,
      "Cache-Control": "no-store",
      "Access-Control-Allow-Origin": "*"
    });
    res.end(audio);
  } catch (error) {
    sendJson(res, 500, { error: error.message || "server_error" });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Neural voice backend listening on :${port}`);
});
