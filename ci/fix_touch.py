from pathlib import Path

TOUCH_SCRIPT = '''extends CanvasLayer
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

AUDIO_SCRIPT = '''extends Node
class_name AudioManager

var rng: RandomNumberGenerator = RandomNumberGenerator.new()
var music_player: AudioStreamPlayer
var music_mode: String = "menu"

func _ready() -> void:
    rng.seed = 923811
    music_player = AudioStreamPlayer.new()
    add_child(music_player)

func _tone(
    frequency: float,
    duration: float,
    volume: float = 0.25,
    wave: int = 0
) -> AudioStreamWAV:
    var rate: int = 22050
    var sample_count: int = int(rate * duration)
    var bytes: PackedByteArray = PackedByteArray()
    bytes.resize(sample_count * 2)
    for i in range(sample_count):
        var time_value: float = float(i) / float(rate)
        var envelope: float = minf(1.0, time_value * 20.0) * maxf(
            0.0,
            1.0 - time_value / duration
        )
        var sample_value: float = sin(TAU * frequency * time_value)
        if wave == 1:
            sample_value = 1.0 if sample_value >= 0.0 else -1.0
        elif wave == 2:
            sample_value = 2.0 * (
                time_value * frequency - floor(0.5 + time_value * frequency)
            )
        var packed_value: int = int(
            clampf(sample_value * envelope * volume, -1.0, 1.0) * 32767.0
        )
        bytes[i * 2] = packed_value & 255
        bytes[i * 2 + 1] = (packed_value >> 8) & 255
    var wav := AudioStreamWAV.new()
    wav.format = AudioStreamWAV.FORMAT_16_BITS
    wav.mix_rate = rate
    wav.stereo = false
    wav.data = bytes
    return wav

func play_sfx(effect_name: String) -> void:
    var player := AudioStreamPlayer.new()
    add_child(player)
    match effect_name:
        "corn":
            player.stream = _tone(880.0, 0.11, 0.22)
            player.pitch_scale = 1.0 + rng.randf_range(-0.08, 0.08)
        "jump":
            player.stream = _tone(420.0, 0.10, 0.18, 2)
        "hurt":
            player.stream = _tone(130.0, 0.25, 0.25, 1)
        "checkpoint":
            player.stream = _tone(660.0, 0.45, 0.18)
        "lever":
            player.stream = _tone(240.0, 0.18, 0.22, 2)
        "door":
            player.stream = _tone(95.0, 0.55, 0.22, 1)
        "cluck":
            player.stream = _tone(310.0, 0.18, 0.18, 2)
        "victory":
            player.stream = _tone(1046.0, 0.8, 0.25)
        "enemy":
            player.stream = _tone(160.0, 0.3, 0.18, 1)
        _:
            player.stream = _tone(500.0, 0.12, 0.15)
    player.finished.connect(player.queue_free)
    player.play()

func set_music(mode: String) -> void:
    music_mode = mode
    if music_player.playing:
        music_player.stop()
    var frequencies: Dictionary = {
        "menu": 196.0,
        "garden": 220.0,
        "forest": 174.0,
        "swamp": 146.0,
        "ruins": 164.0,
        "heart": 246.0,
        "victory": 392.0,
    }
    var frequency: float = float(frequencies.get(mode, 196.0))
    var wav: AudioStreamWAV = _tone(frequency, 4.0, 0.035)
    wav.loop_mode = AudioStreamWAV.LOOP_FORWARD
    wav.loop_begin = 0
    wav.loop_end = int(wav.mix_rate * 4.0)
    music_player.stream = wav
    music_player.play()
'''

project = Path('project')
(project / 'scripts/TouchControls.gd').write_text(TOUCH_SCRIPT, encoding='utf-8')
(project / 'scripts/AudioManager.gd').write_text(AUDIO_SCRIPT, encoding='utf-8')

config_path = project / 'project.godot'
config = config_path.read_text(encoding='utf-8')
if '[debug]' not in config:
    config += '''

[debug]
gdscript/warnings/treat_warnings_as_errors=false
gdscript/warnings/inference_on_variant=0
'''
config_path.write_text(config, encoding='utf-8')
print('Applied parser-safe touch controls, audio and warning settings')
