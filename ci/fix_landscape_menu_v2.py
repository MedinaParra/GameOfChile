from pathlib import Path
import re
import shutil

PROJECT = Path("project")
ROOT = Path(__file__).resolve().parents[1]

# Fixed horizontal orientation and separate package, avoiding signature conflicts
# with the diagnostic v1.0.1 installation.
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

# Copy the low-cost CanvasItem animation into the reconstructed project.
shutil.copyfile(ROOT / "ci" / "MenuAnimation.gd", PROJECT / "scripts" / "MenuAnimation.gd")

game_path = PROJECT / "scripts" / "GameManager.gd"
game = game_path.read_text(encoding="utf-8")

touch_token = 'const TouchScript=preload("res://scripts/TouchControls.gd")\n'
if 'const MenuAnimationScript=' not in game:
    if touch_token not in game:
        raise RuntimeError("TouchScript preload token missing")
    game = game.replace(
        touch_token,
        touch_token + 'const MenuAnimationScript=preload("res://scripts/MenuAnimation.gd")\n',
        1,
    )

ready_token = 'func _ready() -> void:\n    _setup_input_actions()\n'
if 'DisplayServer.screen_set_orientation' not in game:
    if ready_token not in game:
        raise RuntimeError("GameManager _ready token missing")
    game = game.replace(
        ready_token,
        'func _ready() -> void:\n'
        '    if OS.get_name() == "Android":\n'
        '        DisplayServer.screen_set_orientation(DisplayServer.SCREEN_LANDSCAPE)\n'
        '    _setup_input_actions()\n',
        1,
    )

background_token = '    var bg:=ColorRect.new(); bg.color=Color("14251d"); bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); menu.add_child(bg)\n'
if 'var preview := MenuAnimationScript.new()' not in game:
    if background_token not in game:
        raise RuntimeError("Main menu background token missing")
    game = game.replace(
        background_token,
        background_token
        + '    var preview := MenuAnimationScript.new()\n'
        + '    preview.name = "MenuAnimationPreview"\n'
        + '    preview.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)\n'
        + '    menu.add_child(preview)\n',
        1,
    )

game_path.write_text(game, encoding="utf-8")

# Verify both requested changes during the existing full-world smoke test.
test_path = PROJECT / "tests" / "RuntimeWorldSmoke.gd"
test = test_path.read_text(encoding="utf-8")
test_token = '    await process_frame\n    game.start_game(false)\n'
if 'animated main menu preview missing' not in test:
    if test_token not in test:
        raise RuntimeError("Runtime world smoke insertion token missing")
    test = test.replace(
        test_token,
        '    await process_frame\n'
        '    if int(ProjectSettings.get_setting("display/window/handheld/orientation", -1)) != DisplayServer.SCREEN_LANDSCAPE:\n'
        '        _fail("landscape orientation is not locked")\n'
        '        return\n'
        '    if game.menu == null or game.menu.get_node_or_null("MenuAnimationPreview") == null:\n'
        '        _fail("animated main menu preview missing")\n'
        '        return\n'
        '    game.start_game(false)\n',
        1,
    )
test = test.replace(
    'WORLD SMOKE: OK - 5 sectors, 50 corn, damage, checkpoint, save, final door',
    'WORLD SMOKE: OK - landscape locked, animated menu, 5 sectors, 50 corn, damage, checkpoint, save, final door',
)
test_path.write_text(test, encoding="utf-8")

print("Applied fixed landscape orientation and animated main menu v1.0.2")
