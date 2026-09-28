class_name AMHud
extends CanvasLayer

var mission_label: Label
var speed_label: Label
var mini_marker: ColorRect
var mission_marker: ColorRect
var mini_map: TextureRect
var full_map: TextureRect
var overlay: ColorRect
var map_visible := false

const MINI := Vector2(210,210)
const X0 := -760.0
const X1 := 560.0
const Z0 := -600.0
const Z1 := 600.0

func _ready() -> void:
	var title := Label.new()
	title.text = "ANTOFAMAFIA v0.4 • FPS"
	title.position = Vector2(24,20)
	title.add_theme_font_size_override("font_size",28)
	add_child(title)

	mission_label = Label.new()
	mission_label.text = "MISIÓN: llega al Puerto de Antofagasta"
	mission_label.position = Vector2(24,62)
	mission_label.add_theme_font_size_override("font_size",18)
	add_child(mission_label)

	speed_label = Label.new()
	speed_label.position = Vector2(24,92)
	speed_label.add_theme_font_size_override("font_size",18)
	add_child(speed_label)

	var mode := Label.new()
	mode.text = "PRIMERA PERSONA"
	mode.position = Vector2(24,120)
	mode.add_theme_font_size_override("font_size",14)
	add_child(mode)

	# Minimal crosshair at screen centre.
	var crosshair := Label.new()
	crosshair.text = "+"
	crosshair.anchor_left = 0.5
	crosshair.anchor_top = 0.5
	crosshair.anchor_right = 0.5
	crosshair.anchor_bottom = 0.5
	crosshair.offset_left = -10
	crosshair.offset_top = -16
	crosshair.offset_right = 10
	crosshair.offset_bottom = 16
	crosshair.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	crosshair.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	crosshair.add_theme_font_size_override("font_size",22)
	add_child(crosshair)

	var frame := ColorRect.new()
	frame.color = Color(0.03,0.04,0.05,0.82)
	frame.anchor_left = 1.0
	frame.anchor_right = 1.0
	frame.offset_left = -235
	frame.offset_right = -15
	frame.offset_top = 18
	frame.offset_bottom = 238
	add_child(frame)

	mini_map = TextureRect.new()
	mini_map.texture = load("res://assets/antofamafia_map.svg")
	mini_map.position = Vector2(5,5)
	mini_map.size = MINI
	mini_map.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	frame.add_child(mini_map)

	mini_marker = ColorRect.new()
	mini_marker.color = Color.WHITE
	mini_marker.size = Vector2(8,8)
	mini_map.add_child(mini_marker)
	mission_marker = ColorRect.new()
	mission_marker.color = Color("#ffd33d")
	mission_marker.size = Vector2(9,9)
	mini_map.add_child(mission_marker)

	var help := Label.new()
	help.text = "Mouse mirar • WASD mover/manejar • Shift correr • E auto • Espacio freno • M mapa"
	help.anchor_top = 1.0
	help.anchor_bottom = 1.0
	help.anchor_right = 1.0
	help.offset_top = -38
	help.offset_bottom = -12
	help.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	add_child(help)

	overlay = ColorRect.new()
	overlay.color = Color(0,0,0,0.92)
	overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	overlay.visible = false
	add_child(overlay)
	full_map = TextureRect.new()
	full_map.texture = load("res://assets/antofamafia_map.svg")
	full_map.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT, Control.PRESET_MODE_MINSIZE, 40)
	full_map.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	overlay.add_child(full_map)

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_M:
		map_visible = not map_visible
		overlay.visible = map_visible

func update_runtime(pos: Vector3, mission: Vector3, driving: bool, speed: float) -> void:
	mini_marker.position = _map(pos)-mini_marker.size*0.5
	mission_marker.position = _map(mission)-mission_marker.size*0.5
	speed_label.text = ("%03d km/h" % int(absf(speed)*3.6)) if driving else "A PIE"

func _map(p: Vector3) -> Vector2:
	return Vector2(
		clampf(inverse_lerp(X0,X1,p.x),0,1)*MINI.x,
		clampf(inverse_lerp(Z0,Z1,p.z),0,1)*MINI.y
	)

func set_mission_complete() -> void:
	mission_label.text = "MISIÓN COMPLETADA — Puerto de Antofagasta"
