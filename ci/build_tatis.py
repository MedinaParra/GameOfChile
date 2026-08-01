from __future__ import annotations

from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "project"


def reconstruct() -> None:
    raw = "".join(
        p.read_text(encoding="utf-8")
        for p in sorted((ROOT / "bootstrap_parts").glob("part_*"))
    )
    raw = raw.replace("FILES = json.loads('", "FILES = json.loads('''", 1)
    raw = raw.replace("\"}')\nroot=Path", "\"}''')\nroot=Path", 1)
    bootstrap = ROOT / "bootstrap.py"
    bootstrap.write_text(raw, encoding="utf-8")
    subprocess.run(["python3", str(bootstrap)], cwd=ROOT, check=True)
    shutil.rmtree(PROJECT, ignore_errors=True)
    (ROOT / "tatis").rename(PROJECT)


def patch(rel: str, replacements: list[tuple[str, str]] = [], append: str = "") -> None:
    path = PROJECT / rel
    text = path.read_text(encoding="utf-8")
    for old, new in replacements:
        if old not in text:
            print(f"NOTICE token not found in {rel}: {old[:70]!r}")
        text = text.replace(old, new)
    if append and append not in text:
        text += append
    path.write_text(text, encoding="utf-8")


def apply_patches() -> None:
    config = PROJECT / "project.godot"
    text = config.read_text(encoding="utf-8").replace(
        "[display/window]]", "[display/window]"
    )
    if "\n[input]\n" in text:
        text = text.split("\n[input]\n", 1)[0].rstrip() + "\n"
    config.write_text(text, encoding="utf-8")

    patch(
        "scripts/GameManager.gd",
        [
            (
                'func _ready() -> void:\n    add_to_group("game")',
                'func _ready() -> void:\n    _setup_input_actions()\n    add_to_group("game")',
            ),
            (
                "    final_ready=true; if door:door.open(); if exit_light:exit_light.light_energy=4.0",
                "    final_ready = true\n    if door:\n        door.open()\n    if exit_light:\n        exit_light.light_energy = 4.0",
            ),
            (
                '("❤ " .repeat(v)+"♡ " .repeat(3-v)).strip_edges()',
                '("❤ ".repeat(v) + "♡ ".repeat(3-v)).strip_edges()',
            ),
            (
                'return "%02d:%02d"%[int(t)/60,int(t)%60]',
                'return "%02d:%02d" % [int(t) / 60, int(t) % 60]',
            ),
        ],
        append='''

func _setup_input_actions() -> void:
    var bindings := {
        "move_left": KEY_A,
        "move_right": KEY_D,
        "move_forward": KEY_W,
        "move_back": KEY_S,
        "jump": KEY_SPACE,
        "run": KEY_SHIFT,
        "interact": KEY_E,
        "cluck": KEY_Q,
        "pause_game": KEY_ESCAPE,
    }
    for action in bindings:
        if not InputMap.has_action(action):
            InputMap.add_action(action, 0.2)
        if InputMap.action_get_events(action).is_empty():
            var key := InputEventKey.new()
            key.physical_keycode = bindings[action]
            InputMap.action_add_event(action, key)
''',
    )

    patch(
        "scripts/Checkpoint.gd",
        [
            (
                "    body_entered.connect(func(b): if b is PlayerController and not active: active=true; activated.emit(checkpoint_id,global_position+Vector3.UP))",
                "    body_entered.connect(_on_body_entered)",
            )
        ],
        append='''

func _on_body_entered(body: Node) -> void:
    if body is PlayerController and not active:
        active = true
        activated.emit(checkpoint_id, global_position + Vector3.UP)
''',
    )

    patch(
        "scripts/MagicRoots.gd",
        [
            (
                "    body_entered.connect(func(b): if active and b is PlayerController: b.take_damage())",
                "    body_entered.connect(_on_body_entered)",
            )
        ],
        append='''

func _on_body_entered(body: Node) -> void:
    if active and body is PlayerController:
        body.take_damage()
''',
    )

    patch(
        "scripts/CrowEnemy.gd",
        [
            (
                '    for c in get_children(): if c.name=="Wing": c.rotation.z=sin(phase*8.0)*.55',
                '    for c in get_children():\n        if c.name == "Wing":\n            c.rotation.z = sin(phase * 8.0) * 0.55',
            )
        ],
    )

    patch(
        "scripts/PuzzleManager.gd",
        [
            (
                '    timed_open=true; if timed_door: timed_door.open(); message.emit("¡Cruza antes de que termine el brillo!")',
                '    timed_open = true\n    if timed_door:\n        timed_door.open()\n    message.emit("¡Cruza antes de que termine el brillo!")',
            )
        ],
    )

    patch(
        "scripts/TouchControls.gd",
        [
            (
                "    joystick.changed.connect(func(v): if is_instance_valid(player): player.mobile_move = v)",
                "    joystick.changed.connect(_on_joystick_changed)",
            ),
            (
                "    cam.dragged.connect(func(d): if is_instance_valid(player): player.add_look_delta(d)",
                "    cam.dragged.connect(_on_camera_dragged)",
            ),
        ],
        append='''

func _on_joystick_changed(value: Vector2) -> void:
    if is_instance_valid(player):
        player.mobile_move = value

func _on_camera_dragged(delta: Vector2) -> void:
    if is_instance_valid(player):
        player.add_look_delta(delta)
''',
    )

    patch(
        "scripts/PlayerController.gd",
        [
            (
                '    var tw=create_tween(); tw.tween_property(visual,"modulate",Color(1,0.35,0.35,1),0.08); tw.tween_property(visual,"modulate",Color.WHITE,0.12); tw.set_loops(5)',
                '    var tw = create_tween()\n    tw.tween_property(visual, "scale", Vector3(0.9, 1.1, 0.9), 0.08)\n    tw.tween_property(visual, "scale", Vector3.ONE, 0.12)\n    tw.set_loops(5)',
            )
        ],
    )


def configure_signing() -> None:
    presets = PROJECT / "export_presets.cfg"
    text = presets.read_text(encoding="utf-8")
    home = Path.home()
    text += f'''\nkeystore/debug="{home / ".android/debug.keystore"}"
keystore/debug_user="androiddebugkey"
keystore/debug_password="android"
keystore/release="{ROOT / "tatis-release.keystore"}"
keystore/release_user="tatis"
keystore/release_password="TatisBuild2026"
'''
    presets.write_text(text, encoding="utf-8")


def main() -> None:
    reconstruct()
    apply_patches()
    if "--signing" in __import__("sys").argv:
        configure_signing()
    print("Tatis project reconstructed and patched:", PROJECT)


if __name__ == "__main__":
    main()
