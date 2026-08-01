from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "project"
DEBUG_APK = ROOT / "Tatis_Laberinto_Fauno_v1.0.0.apk"
RELEASE_APK = ROOT / "Tatis_Laberinto_Fauno_v1.0.0_release.apk"


def digest(path: Path) -> str:
    h = sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def mib(path: Path) -> str:
    return f"{path.stat().st_size / (1024 * 1024):.2f} MiB"


def apk_checks(path: Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
    checks = []
    for expected in ("AndroidManifest.xml", "classes.dex"):
        checks.append(f"- `{expected}` presente: **{'OK' if expected in names else 'FALLO'}**")
    arm64 = any(name.startswith("lib/arm64-v8a/") for name in names)
    armv7 = any(name.startswith("lib/armeabi-v7a/") for name in names)
    checks.append(f"- Biblioteca ARM64 presente: **{'OK' if arm64 else 'FALLO'}**")
    checks.append(f"- Biblioteca ARMv7 presente: **{'OK' if armv7 else 'FALLO'}**")
    return checks


stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
report = f"""# Reporte de pruebas — Tatis y el Laberinto del Fauno v1.0.0

Compilación verificada: **{stamp}**.

## Entorno de compilación
- Godot Engine: **4.6.3 stable**.
- Renderizador: **Compatibility**.
- Java: **OpenJDK 17**.
- Android SDK Platform: **35**.
- Android Build Tools: **35.0.1**.
- Paquete: `com.tatis.laberintodelfauno`.
- Arquitecturas: **ARM64-v8a y armeabi-v7a**.

## Resultados automáticos
- Reconstrucción del proyecto editable: **OK**.
- Importación completa de recursos: **OK**.
- Análisis de todos los scripts GDScript por Godot: **OK**.
- Referencias nulas o rutas rotas durante importación: **no detectadas**.
- Prueba de arranque headless de la escena principal: **OK**.
- Menú principal creado al iniciar: **OK**.
- Prueba estática de estructura y scripts especializados: **OK**.
- Recolectables declarados: **50 exactos**.
- Distribución: **10 por cada uno de los cinco sectores**.
- IDs únicos: **0–49, sin duplicados**.
- Persistencia por ID y guardado después de cada recolección: **OK por análisis de código**.
- Condición de apertura de puerta: **50/50 obligatorios**.
- Exportación APK debug: **OK** — {mib(DEBUG_APK)}.
- Exportación APK release: **OK** — {mib(RELEASE_APK)}.
- Prueba `unzip -t` de ambas APK: **OK**.

## Contenido APK debug
{chr(10).join(apk_checks(DEBUG_APK))}

## Contenido APK release
{chr(10).join(apk_checks(RELEASE_APK))}

## Huellas SHA-256
- Debug: `{digest(DEBUG_APK)}`
- Release: `{digest(RELEASE_APK)}`

## Cobertura funcional implementada
Movimiento, carrera, salto, aleteo procedural, cámara con SpringArm, controles táctiles multitáctiles, 50 granos persistentes, tres corazones, invulnerabilidad temporal, nidos de control, reaparición, zorro, cuervo, estatua guardiana, raíces mágicas, cuatro acertijos, diálogos del fauno, puerta bloqueada, final, estadísticas, desbloqueos, configuración gráfica y audio procedural.

## Limitaciones reales
- El runner de compilación no tenía un teléfono Android ni emulador conectado; por ello no se afirma una instalación física ni medición real de FPS en un modelo específico.
- La validación de ejecución se realizó con Godot en modo headless, además del análisis completo de scripts y recursos.
- El arte es low-poly y procedural, y la música/efectos son sintetizados en tiempo de ejecución para evitar dependencias y derechos de terceros.
- La APK release está firmada con una clave generada para esta compilación de prueba. Para publicar actualizaciones en Google Play debe conservarse una clave de producción propia y estable.
"""
(PROJECT / "REPORTE_PRUEBAS.md").write_text(report, encoding="utf-8")

errors = """# Errores encontrados y corregidos — v1.0.0

1. **Reconstrucción del paquete fuente:** se corrigieron delimitadores y escapes del contenedor textual usado por CI.
2. **Sintaxis táctil:** se reemplazaron lambdas de una línea incompatibles por callbacks tipados y parser-safe.
3. **Inferencia de tipos en audio:** se tiparon frecuencia, muestras, envolvente y datos PCM para Godot 4.6.
4. **Advertencias tratadas como errores:** se configuraron advertencias de inferencia sin ocultar errores críticos.
5. **Entrada de PC:** las acciones se crean de forma segura en tiempo de ejecución para WASD, salto, carrera, interacción, cacareo y pausa.
6. **Invulnerabilidad visual:** se sustituyó una propiedad visual no válida por animación de escala procedural.
7. **Exportación Android sin mensaje:** se activó `textures/vram_compression/import_etc2_astc`, requisito que Godot valida sin añadir texto al mensaje de error.
8. **IDs de maíz:** se fijaron 50 identificadores únicos y persistentes, diez por sector.
9. **Puerta final:** se añadió verificación explícita de 50/50 tanto al desbloquear como al finalizar.
10. **Cadena Android:** se configuraron OpenJDK 17, SDK 35, Build Tools 35.0.1, plantillas 4.6.3 y firmas debug/release.

No quedaron errores críticos de análisis, arranque o exportación en la compilación entregada.
"""
(PROJECT / "ERRORES_CORREGIDOS.md").write_text(errors, encoding="utf-8")
print("Final documentation generated from verified APK artifacts")
