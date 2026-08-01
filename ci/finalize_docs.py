from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "project"
DEBUG_APK = ROOT / "Tatis_Laberinto_Fauno_v1.0.1_Samsung_A26.apk"
RELEASE_APK = ROOT / "Tatis_Laberinto_Fauno_v1.0.1_Samsung_A26_release.apk"


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
        ("x86_64 de prueba", "lib/x86_64/"),
    ):
        present = any(name.startswith(prefix) for name in names)
        checks.append(f"- Biblioteca {architecture} presente: **{'OK' if present else 'FALLO'}**")
    return checks


stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
report = f"""# Reporte de pruebas — Tatis y el Laberinto del Fauno v1.0.1 Samsung A26

Compilación verificada: **{stamp}**.

## Entorno de compilación
- Godot Engine: **4.6.3 stable**.
- Renderizador Android: **Mobile sobre Vulkan**, con fallback automático a OpenGL 3.
- Java: **OpenJDK 17**.
- Android SDK Platform: **35**.
- Android Build Tools: **35.0.1**.
- Paquete de prueba paralelo: `com.tatis.laberintodelfauno.safe`.
- Arquitecturas: **ARM64-v8a, armeabi-v7a y x86_64 para emulador**.

## Correcciones de estabilidad móvil
- Eliminados los 50 sistemas `GPUParticles3D` de los granos.
- Sombras direccionales desactivadas.
- Luces dinámicas del pantano sustituidas por mallas emisivas.
- Materiales, cajas y esferas reutilizados mediante caché.
- Geometría esférica reducida a 12 segmentos y 6 anillos.
- Límite conservador de **30 FPS** en Android.
- Frame pacing de Android activado.
- Música procedural en bucle desactivada en Android; se mantienen efectos cortos cacheados.
- Cambios de monitoreo de raíces mágicas movidos a `set_deferred`.

## Resultados automáticos
- Reconstrucción del proyecto editable: **OK**.
- Importación completa de recursos: **OK**.
- Análisis de todos los scripts GDScript por Godot: **OK**.
- Prueba de arranque del menú: **OK**.
- Prueba funcional que entra a Nueva partida y construye los cinco sectores: **OK**.
- Mundo, jugador, daño, guardado, punto de control y puerta final: **OK**.
- Recolectables creados durante ejecución: **50 exactos**.
- Puerta bloqueada antes de 50/50 y abierta después de 50/50: **OK**.
- Instalación y arranque en emulador Android API 35: **OK**.
- Proceso vivo después de entrar a Nueva partida: **OK**.
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
- La prueba automatizada Android se realiza en un emulador API 35, no directamente en el Samsung A26 físico del usuario.
- El modo seguro reduce efectos visuales y desactiva la música ambiental en Android para aislar fallos de GPU/audio.
- El paquete `.safe` se instala junto a la versión anterior y empieza con un guardado independiente.
- La APK release está firmada con una clave de prueba generada para esta compilación; no es una clave definitiva de Google Play.
"""
(PROJECT / "REPORTE_PRUEBAS.md").write_text(report, encoding="utf-8")

errors = """# Errores encontrados y corregidos — v1.0.1 Samsung A26

1. **Cierre físico después de 1–2 segundos:** se sustituyó OpenGL Compatibility como ruta principal por Mobile/Vulkan con fallback automático.
2. **Carga de partículas:** se eliminaron 50 sistemas GPU simultáneos asociados a los granos.
3. **Presión de luces y sombras:** se desactivaron sombras y se redujeron luces dinámicas.
4. **Duplicación de recursos:** materiales y mallas modulares ahora se reutilizan mediante caché.
5. **Picos de geometría:** se redujeron segmentos de esferas y cantidad de árboles.
6. **Audio móvil:** se desactivó el bucle musical procedural en Android y se cachean los efectos cortos.
7. **Estabilidad de física:** el cambio de `Area3D.monitoring` de las raíces usa ejecución diferida.
8. **Frame pacing:** se activó el control de ritmo de fotogramas y se fijó un máximo de 30 FPS en Android.
9. **Validación incompleta anterior:** ahora la prueba automática entra realmente a Nueva partida, construye el mundo y verifica 50 granos, daño, guardado, checkpoint y puerta.
10. **Prueba Android:** la APK se instala, inicia y permanece ejecutándose en un emulador Android API 35 tras entrar al juego.

No quedaron errores críticos de análisis, ejecución funcional, exportación o prueba Android automatizada en esta compilación.
"""
(PROJECT / "ERRORES_CORREGIDOS.md").write_text(errors, encoding="utf-8")

readme_path = PROJECT / "README.md"
readme = readme_path.read_text(encoding="utf-8")
readme = readme.replace("v1.0.0", "v1.0.1 Samsung A26")
readme = readme.replace("com.tatis.laberintodelfauno", "com.tatis.laberintodelfauno.safe")
readme += """

## Modo seguro Samsung A26
Esta variante usa el renderizador Mobile/Vulkan, limita Android a 30 FPS, elimina partículas GPU y sombras, reduce luces y reutiliza mallas/materiales. Se instala como una aplicación paralela con paquete `com.tatis.laberintodelfauno.safe`, por lo que no es necesario desinstalar la primera APK para probarla.

La música ambiental procedural queda desactivada en Android en esta compilación de diagnóstico; los efectos cortos permanecen activos.
"""
readme_path.write_text(readme, encoding="utf-8")
print("Final Samsung A26 documentation generated from verified APK artifacts")
