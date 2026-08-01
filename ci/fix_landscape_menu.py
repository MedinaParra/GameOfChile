from pathlib import Path
import re

PROJECT = Path("project")

# Force a fixed landscape orientation and publish as a separate installable variant.
config_path = PROJECT / "project.godot"
config = config_path.read_text(encoding="utf-8")
config = config.replace('config/name="Tatis Laberinto - Modo Seguro"', 'config/name="Tatis Laberinto - Horizontal"')
config = config.replace('config/version="1.0.1"', 'config/version="1.0.2"')
if re.search(r"^handheld/orientation=", config, flags=re.MULTILINE):
    config = re.sub(r"^handheld/orientation=.*$", "handheld/orientation=0", config, flags=re.MULTILINE)
else:
    config = config.replace("[display/window]\n", "[display/window]\nhandheld/orientation=0\n", 1)
config_path.write_text(config, encoding="utf-8")

preset_path = PROJECT / "export_presets.cfg"
preset = preset_path.read_text(encoding="utf-8")
preset = preset.replace('package/unique_name="com.tatis.laberintodelfauno.safe"', 'package/unique_name="com.tatis.laberintodelfauno.landscape"')
preset = preset.replace('package/name="Tatis Laberinto - Modo Seguro"', 'package/name="Tatis Laberinto - Horizontal"')
preset = preset.replace("version/code=2", "version/code=3")
preset = preset.replace('version/name="1.0.1-safe"', 'version/name="1.0.2-landscape"')
preset_path.write_text(preset, encoding="utf-8")

# Lightweight 2D animated game vignette for the main menu. It uses only CanvasItem
# drawing primitives, avoiding textures, particles and additional 3D load on Android.
animation_path = PROJECT / "scripts/MenuAnimation.gd"
animation_path.write_text('''extends Control
class_name MenuAnimation

var elapsed := 0.0

func _ready() -> void:
    name = "MenuAnimationPreview"
    mouse_filter = Control.MOUSE_FILTER_IGNORE
    set_process(true)
    queue_redraw()

func _process(delta: float) -> void:
    elapsed = fmod(elapsed + delta, 60.0)
    queue_redraw()

func _draw() -> void:
    var panel := Rect2(34.0, 205.0, 350.0, 392.0)
    draw_rect(panel, Color(0.035, 0.075, 0.060, 0.94))
    draw_rect(panel, Color(0.44, 0.66, 0.43, 0.72), false, 3.0)

    # Moon, distant trees and softly pulsing fireflies.
    draw_circle(Vector2(323.0, 254.0), 34.0, Color(0.88, 0.90, 0.68, 0.82))
    for i in range(7):
        var tree_x := 58.0 + float(i) * 49.0
        var tree_height := 44.0 + float((i * 17) % 35)
        draw_rect(Rect2(tree_x - 4.0, 397.0 - tree_height, 8.0, tree_height), Color(0.19, 0.23, 0.16, 1.0))
        draw_circle(Vector2(tree_x, 357.0 - tree_height), 23.0, Color(0.12, 0.25, 0.15, 1.0))
    for i in range(6):
        var glow := 0.55 + sin(elapsed * 2.2 + float(i)) * 0.35
        var firefly := Vector2(66.0 + float(i) * 52.0, 290.0 + sin(float(i) * 1.7) * 34.0)
        draw_circle(firefly, 2.5 + glow * 1.6, Color(0.80, 1.0, 0.45, glow))

    # Miniature labyrinth floor and walls.
    draw_rect(Rect2(48.0, 428.0, 322.0, 151.0), Color(0.20, 0.30, 0.18, 1.0))
    var wall_color := Color(0.36, 0.43, 0.29, 1.0)
    draw_rect(Rect2(62.0, 448.0, 105.0, 14.0), wall_color)
    draw_rect(Rect2(151.0, 448.0, 14.0, 69.0), wall_color)
    draw_rect(Rect2(205.0, 490.0, 118.0, 14.0), wall_color)
    draw_rect(Rect2(250.0, 504.0, 14.0, 54.0), wall_color)
    draw_rect(Rect2(78.0, 532.0, 105.0, 14.0), wall_color)

    # Corn grains bob as collectibles from the game.
    var corn_points := [
        Vector2(92.0, 482.0), Vector2(192.0, 465.0), Vector2(224.0, 535.0),
        Vector2(304.0, 462.0), Vector2(335.0, 541.0)
    ]
    for i in range(corn_points.size()):
        var bob := sin(elapsed * 3.0 + float(i) * 1.1) * 3.0
        var point: Vector2 = corn_points[i] + Vector2(0.0, bob)
        draw_circle(point, 6.0, Color(1.0, 0.78, 0.16, 1.0))
        draw_line(point + Vector2(0.0, 5.0), point + Vector2(0.0, 11.0), Color(0.38, 0.62, 0.25, 1.0), 2.0)

    # Tatis walks back and forth through the miniature maze.
    var movement := (sin(elapsed * 0.82) + 1.0) * 0.5
    var facing := 1.0 if cos(elapsed * 0.82) >= 0.0 else -1.0
    var chicken := Vector2(90.0 + movement * 235.0, 565.0 + sin(elapsed * 5.0) * 2.5)
    _draw_tatis(chicken, facing)

    # Faun silhouette watches from the right edge of the menu.
    var faun_center := Vector2(1085.0, 452.0)
    draw_circle(faun_center, 66.0, Color(0.045, 0.075, 0.055, 0.78))
    draw_arc(faun_center + Vector2(-24.0, -48.0), 48.0, 2.9, 5.0, 24, Color(0.32, 0.43, 0.28, 0.76), 8.0)
    draw_arc(faun_center + Vector2(24.0, -48.0), 48.0, 4.4, 6.5, 24, Color(0.32, 0.43, 0.28, 0.76), 8.0)
    var eye_glow := 0.65 + sin(elapsed * 2.0) * 0.25
    draw_circle(faun_center + Vector2(-19.0, -8.0), 5.0, Color(0.72, 1.0, 0.42, eye_glow))
    draw_circle(faun_center + Vector2(19.0, -8.0), 5.0, Color(0.72, 1.0, 0.42, eye_glow))

func _draw_tatis(center: Vector2, facing: float) -> void:
    draw_ellipse_shadow(center + Vector2(0.0, 18.0))
    draw_circle(center, 22.0, Color(0.95, 0.69, 0.14, 1.0))
    draw_circle(center + Vector2(-6.0 * facing, 2.0), 11.0, Color(0.82, 0.50, 0.09, 1.0))
    var head := center + Vector2(19.0 * facing, -18.0)
    draw_circle(head, 15.0, Color(1.0, 0.79, 0.20, 1.0))
    draw_circle(head + Vector2(5.0 * facing, -3.0), 2.6, Color(0.04, 0.04, 0.03, 1.0))
    var beak := PackedVector2Array([
        head + Vector2(13.0 * facing, 1.0),
        head + Vector2(25.0 * facing, 6.0),
        head + Vector2(13.0 * facing, 10.0)
    ])
    draw_colored_polygon(beak, Color(0.94, 0.43, 0.08, 1.0))
    draw_circle(head + Vector2(-5.0 * facing, -14.0), 5.0, Color(0.82, 0.12, 0.08, 1.0))
    var step := sin(elapsed * 7.0) * 5.0
    draw_line(center + Vector2(-7.0, 18.0), center + Vector2(-7.0 + step, 31.0), Color(0.80, 0.42, 0.08, 1.0), 3.0)
    draw_line(center + Vector2(7.0, 18.0), center + Vector2(7.0 - step, 31.0), Color(0.80, 0.42, 0.08, 1.0), 3.0)

func draw_ellipse_shadow(center: Vector2) -> void:
    draw_set_transform(center, 0.0, Vector2(1.7, 0.45))
    draw_circle(Vector2.ZERO, 15.0, Color(0.02, 0.03, 0.02, 0.42))
    draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)
''', encoding="utf-8")

# Load and mount the animated vignette behind all menu controls.
game_path = PROJECT / "scripts/GameManager.gd"
game = game_path.read_text(encoding="utf-8")n
touch_token = 'const TouchScript=preload("res://scripts/TouchControls.gd")\n'
if 'const MenuAnimationScript=' not in game:
    if touch_token not in game:
        raise RuntimeError("TouchScript preload token missing")
    game = game.replace(touch_token, touch_token + 'const MenuAnimationScript=preload("res://scripts/MenuAnimation.gd")\n', 1)

ready_token = 'func _ready() -> void:\n    _setup_input_actions()\n'
ready_replacement = '''func _ready() -> void:
    if OS.get_name() == "Android":
        DisplayServer.screen_set_orientation(DisplayServer.SCREEN_LANDSCAPE)
    _setup_input_actions()
'''
if 'DisplayServer.screen_set_orientation' not in game:
    if ready_token not in game:
        raise RuntimeError("GameManager _ready token missing")
    game = game.replace(ready_token, ready_replacement, 1)

background_token = '    var bg:=ColorRect.new(); bg.color=Color("14251d"); bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); menu.add_child(bg)\n'
preview_code = '''    var preview := MenuAnimationScript.new()
    preview.name = "MenuAnimationPreview"
    preview.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    menu.add_child(preview)
'''
if 'var preview := MenuAnimationScript.new()' not in game:
    if background_token not in game:
        raise RuntimeError("Main menu background token missing")
    game = game.replace(background_token, background_token + preview_code, 1)

game_path.write_text(game, encoding="utf-8")

# Extend the gameplay smoke test to verify orientation and the animated menu node.
test_path = PROJECT / "tests/RuntimeWorldSmoke.gd"
test = test_path.read_text(encoding="utf-8")
test_token = '''    await process_frame
    game.start_game(false)
'''
test_replacement = '''    await process_frame
    if int(ProjectSettings.get_setting("display/window/handheld/orientation", -1)) != DisplayServer.SCREEN_LANDSCAPE:
        _fail("landscape orientation is not locked")
        return
    if game.menu == null or game.menu.get_node_or_null("MenuAnimationPreview") == null:
        _fail("animated main menu preview missing")
        return
    game.start_game(false)
'''
if 'animated main menu preview missing' not in test:
    if test_token not in test:
        raise RuntimeError("Runtime world smoke insertion token missing")
    test = test.replace(test_token, test_replacement, 1)
test = test.replace(
    'WORLD SMOKE: OK - 5 sectors, 50 corn, damage, checkpoint, save, final door',
    'WORLD SMOKE: OK - landscape locked, animated menu, 5 sectors, 50 corn, damage, checkpoint, save, final door'
)
test_path.write_text(test, encoding="utf-8")

print("Applied fixed landscape orientation and lightweight animated main menu")
