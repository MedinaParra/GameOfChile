from pathlib import Path

SCRIPT = '''extends CanvasLayer
class_name TouchControls

signal pause_requested

var player: PlayerController
var joystick: VirtualJoystick

func setup(target: PlayerController) -> void:
    player = target
    var root := Control.new()
    root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(root)

    joystick = VirtualJoystick.new()
    joystick.position = Vector2(28, 480)
    joystick.size = Vector2(180, 180)
    root.add_child(joystick)
    joystick.changed.connect(_on_joystick_changed)

    var camera_area := CameraTouchArea.new()
    camera_area.position = Vector2(520, 90)
    camera_area.size = Vector2(760, 510)
    root.add_child(camera_area)
    camera_area.dragged.connect(_on_camera_dragged)

    _make_button(root, "SALTAR", Vector2(1065, 510), Vector2(145, 72), _jump_down)
    _make_button(root, "CORRER", Vector2(895, 585), Vector2(145, 62), _run_down, _run_up)
    _make_button(root, "CACAREO", Vector2(895, 505), Vector2(145, 62), _cluck_down)
    _make_button(root, "II", Vector2(1188, 22), Vector2(64, 54), _pause_down)

func _make_button(
    parent: Control,
    text_value: String,
    pos: Vector2,
    dimensions: Vector2,
    down_callback: Callable,
    up_callback: Callable = Callable()
) -> Button:
    var button := Button.new()
    button.text = text_value
    button.position = pos
    button.size = dimensions
    button.modulate = Color(1, 1, 1, 0.72)
    button.focus_mode = Control.FOCUS_NONE
    parent.add_child(button)
    button.button_down.connect(down_callback)
    if up_callback.is_valid():
        button.button_up.connect(up_callback)
    return button

func _on_joystick_changed(value: Vector2) -> void:
    if is_instance_valid(player):
        player.mobile_move = value

func _on_camera_dragged(delta: Vector2) -> void:
    if is_instance_valid(player):
        player.add_look_delta(delta)

func _jump_down() -> void:
    if is_instance_valid(player):
        player.request_jump()

func _run_down() -> void:
    if is_instance_valid(player):
        player.mobile_run = true

func _run_up() -> void:
    if is_instance_valid(player):
        player.mobile_run = false

func _cluck_down() -> void:
    if is_instance_valid(player):
        player.request_cluck()

func _pause_down() -> void:
    pause_requested.emit()
'''

path = Path('project/scripts/TouchControls.gd')
path.write_text(SCRIPT, encoding='utf-8')
print('Replaced TouchControls.gd with parser-safe implementation')
