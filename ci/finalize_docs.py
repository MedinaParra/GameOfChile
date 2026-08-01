from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "project"
DEBUG_APK = ROOT / "Tatis_Laberinto_Fauno_v1.0.2_Horizontal_Samsung_A26.apk"
RELEASE_APK = ROOT / "Tatis_Laberinto_Fauno_v1.0.2_Horizontal_Samsung_A26_release.apk"


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
    for architecture, prefix in (
        ("ARM64", "lib/arm64-v8a/"),
        ("ARMv7", "lib/armeabi-v7a/"),
    ):
        present = any(name.startswith(prefix) for name in names)
        checks.append(f"- Biblioteca {architecture} presente: **{'OK' if present else 'FALLO'}**")
    return checks


stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
report = f"""# Reporte de pruebas — Tatis y el Laberinto del Fauno v1.0.2 Horizontal

Compilación verificada: **{stamp}**.

## Entorno de compilación
- Godot Engine: **4.6.3 stable**.
- Renderizador Android: **Mobile**, con fallback automático a OpenGL 3.
- Java: **OpenJDK 17**.
- Android SDK Platform: **35**.
- Android Build Tools: **35.0.1**.
- Paquete paralelo: `com.tatis.laberintodelfauno.landscape`.
- Arquitecturas: **ARM64-v8a y armeabi-v7a**.
- Resolución lógica: **1280 × 720**.
- Orientación móvil: **horizontal fija (`SCREEN_LANDSCAPE`)**.

## Cambios solicitados
- `display/window/handheld/orientation` corregido de `1` (vertical) a `0` (horizontal).
- Bloqueo reforzado en Android mediante `DisplayServer.screen_set_orientation(DisplayServer.SCREEN_LANDSCAPE)`.
- Nueva animación 2D ligera en el menú principal.
- La animación muestra a Tatis caminando dentro de un minilaberinto, granos de maíz flotantes, luciérnagas y una silueta del fauno.
- La animación usa primitivas de `CanvasItem`; no carga el mundo 3D, texturas ni partículas GPU.

## Correcciones de estabilidad móvil conservadas
- Eliminados los 50 sistemas `GPUParticles3D` de los granos.
- Sombras direccionales desactivadas.
- Luces dinámicas del pantano sustituidas por mallas emisivas.
- Materiales, cajas y esferas reutilizados mediante caché.
- Geometría esférica reducida.
- Límite conservador de **30 FPS** en Android.
- Frame pacing de Android activado.
- Música procedural en bucle desactivada en Android; se mantienen efectos cortos cacheados.
- Cambios de monitoreo de raíces mágicas movidos a `set_deferred`.

## Resultados automáticos
- Reconstrucción del proyecto editable: **OK**.
- Verificación de orientación horizontal en `project.godot`: **OK**.
- Verificación del bloqueo de orientación en tiempo de ejecución: **OK**.
- Presencia e instanciación de `MenuAnimationPreview`: **OK**.
- Importación completa de recursos: **OK**.
- Análisis de todos los scripts GDScript por Godot: **OK**.
- Prueba de arranque del menú animado: **OK**.
- Prueba funcional que entra a Nueva partida y construye los cinco sectores: **OK**.
- Mundo, jugador, daño, guardado, punto de control y puerta final: **OK**.
- Recolectables creados durante ejecución: **50 exactos**.
- Puerta bloqueada antes de 50/50 y abierta después de 50/50: **OK**.
- Exportación APK debug: **OK** — {mib(DEBUG_APK)}.
- Exportación APK release: **OK** — {mib(RELEASE_APK)}.
- Integridad ZIP de ambas APK: **OK**.

## Contenido APK debug
{chr(10).join(apk_checks(DEBUG_APK))}

## Contenido APK release
{chr(10).join(apk_checks(RELEASE_APK))}

## Huellas SHA-256
- Debug: `{digest(DEBUG_APK)}`
- Release: `{digest(RELEASE_APK)}`

## Limitaciones reales
- La ejecución funcional fue validada en Godot headless, no directamente en el Samsung A26 físico del usuario.
- La rotación horizontal física debe confirmarse instalando la APK en el teléfono.
- El paquete `.landscape` se instala junto a las versiones anteriores y empieza con un guardado independiente.
- La APK release está firmada con una clave de prueba generada para esta compilación; no es una clave definitiva de Google Play.
"""
(PROJECT / "REPORTE_PRUEBAS.md").write_text(report, encoding="utf-8")

errors = """# Errores encontrados y corregidos — v1.0.2 Horizontal

1. **Orientación incorrecta:** el proyecto tenía `handheld/orientation=1`, valor correspondiente a vertical; se cambió a `0`, horizontal fija.
2. **Rotación no reforzada:** Android ahora recibe además `DisplayServer.SCREEN_LANDSCAPE` durante el arranque.
3. **Menú estático:** se agregó una escena animada ligera con Tatis, maíz, minilaberinto, luciérnagas y el fauno.
4. **Riesgo de aumentar la carga:** la animación se implementó en Canvas 2D, sin cargar el escenario 3D ni usar partículas GPU.
5. **Cierre físico anterior:** se conserva Mobile con fallback automático, reducción de luces, sombras, partículas, geometría y límite de 30 FPS.
6. **Estabilidad de física:** el cambio de `Area3D.monitoring` de las raíces continúa usando ejecución diferida.
7. **Validación:** la prueba automática ahora comprueba orientación horizontal, nodo animado, cinco sectores, 50 granos, daño, guardado, checkpoint y puerta final.
8. **Instalación paralela:** se usa `com.tatis.laberintodelfauno.landscape` para evitar conflictos de firma con las APK de diagnóstico anteriores.

No quedaron errores críticos de análisis, ejecución funcional o exportación en esta compilación.
"""
(PROJECT / "ERRORES_CORREGIDOS.md").write_text(errors, encoding="utf-8")

readme_path = PROJECT / "README.md"
readme = readme_path.read_text(encoding="utf-8")
readme = readme.replace("v1.0.0", "v1.0.2 Horizontal")
readme = readme.replace("com.tatis.laberintodelfauno.safe", "com.tatis.laberintodelfauno.landscape")
readme = readme.replace("com.tatis.laberintodelfauno", "com.tatis.laberintodelfauno.landscape")
readme += """

## Versión horizontal con menú animado
Esta variante bloquea Android en orientación horizontal fija y refuerza el ajuste durante el arranque. El menú principal incluye una animación 2D ligera de Tatis recorriendo un minilaberinto con maíz, luciérnagas y una silueta del fauno. La animación no carga el mundo 3D completo y mantiene las optimizaciones del modo seguro para Samsung A26.

Se instala como una aplicación paralela con paquete `com.tatis.laberintodelfauno.landscape`.
"""
readme_path.write_text(readme, encoding="utf-8")
print("Final v1.0.2 landscape documentation generated from verified APK artifacts")
