class_name AMHumanoid
extends Node3D

## Lightweight articulated human made entirely from Godot primitives.
## No capsule placeholder: torso, pelvis, head, hair, arms, forearms,
## hands, thighs, shins and shoes are independent articulated parts.

var variant_seed: int = 1
var anim_time := 0.0

var left_arm: Node3D
var right_arm: Node3D
var left_leg: Node3D
var right_leg: Node3D
var torso: Node3D
var head_pivot: Node3D

var skin: StandardMaterial3D
var shirt: StandardMaterial3D
var pants: StandardMaterial3D
var shoes: StandardMaterial3D
var hair: StandardMaterial3D

func _ready() -> void:
	_make_palette()
	_build_human()

func _make_palette() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = variant_seed * 7907 + 17

	var skin_colors := [
		Color("#f0c3a4"), Color("#d9a17f"), Color("#bd7f5d"),
		Color("#9a6349"), Color("#764b38")
	]
	var shirt_colors := [
		Color("#243243"), Color("#6a3f36"), Color("#425b49"),
		Color("#665a78"), Color("#3f4d5f"), Color("#887047")
	]
	var pants_colors := [
		Color("#222830"), Color("#343943"), Color("#40382f"),
		Color("#273449"), Color("#4a4b48")
	]
	var hair_colors := [
		Color("#15120f"), Color("#332319"), Color("#5a4031"),
		Color("#201c1b")
	]

	skin = _mat(skin_colors[rng.randi_range(0, skin_colors.size()-1)], 0.92)
	shirt = _mat(shirt_colors[rng.randi_range(0, shirt_colors.size()-1)], 0.86)
	pants = _mat(pants_colors[rng.randi_range(0, pants_colors.size()-1)], 0.90)
	shoes = _mat(Color("#15171a"), 0.75)
	hair = _mat(hair_colors[rng.randi_range(0, hair_colors.size()-1)], 0.96)

func _mat(c: Color, rough := 0.85) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	return m

func _build_human() -> void:
	_part_box(self, Vector3(0.64, 0.34, 0.35), Vector3(0, 1.02, 0), pants)

	torso = Node3D.new()
	torso.position = Vector3(0, 1.30, 0)
	add_child(torso)
	_part_box(torso, Vector3(0.82, 0.86, 0.40), Vector3(0, 0.18, 0), shirt)
	_part_cylinder(torso, 0.12, 0.15, Vector3(0, 0.69, 0), skin)

	head_pivot = Node3D.new()
	head_pivot.position = Vector3(0, 2.08, 0)
	add_child(head_pivot)

	var head := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.27
	sm.height = 0.54
	head.mesh = sm
	head.material_override = skin
	head_pivot.add_child(head)

	_part_box(head_pivot, Vector3(0.09, 0.11, 0.11), Vector3(0, -0.01, -0.255), skin)
	_part_box(head_pivot, Vector3(0.48, 0.13, 0.42), Vector3(0, 0.22, 0.015), hair)

	var eye_mat := _mat(Color("#16191c"), 0.95)
	_part_box(head_pivot, Vector3(0.045, 0.038, 0.025), Vector3(-0.09, 0.055, -0.255), eye_mat)
	_part_box(head_pivot, Vector3(0.045, 0.038, 0.025), Vector3(0.09, 0.055, -0.255), eye_mat)

	left_arm = _make_arm(-1.0)
	right_arm = _make_arm(1.0)
	left_leg = _make_leg(-1.0)
	right_leg = _make_leg(1.0)

func _make_arm(side: float) -> Node3D:
	var shoulder := Node3D.new()
	shoulder.position = Vector3(side * 0.51, 1.74, 0)
	add_child(shoulder)

	_part_box(shoulder, Vector3(0.24, 0.62, 0.25), Vector3(0, -0.29, 0), shirt)

	var elbow := Node3D.new()
	elbow.position = Vector3(0, -0.60, 0)
	shoulder.add_child(elbow)
	_part_box(elbow, Vector3(0.21, 0.55, 0.22), Vector3(0, -0.25, 0), skin)
	_part_box(elbow, Vector3(0.23, 0.20, 0.25), Vector3(0, -0.57, -0.01), skin)
	return shoulder

func _make_leg(side: float) -> Node3D:
	var hip := Node3D.new()
	hip.position = Vector3(side * 0.20, 1.01, 0)
	add_child(hip)

	_part_box(hip, Vector3(0.32, 0.67, 0.34), Vector3(0, -0.31, 0), pants)

	var knee := Node3D.new()
	knee.position = Vector3(0, -0.65, 0)
	hip.add_child(knee)
	_part_box(knee, Vector3(0.29, 0.62, 0.31), Vector3(0, -0.28, 0), pants)
	_part_box(knee, Vector3(0.33, 0.18, 0.52), Vector3(0, -0.62, -0.09), shoes)
	return hip

func _part_box(parent: Node, size: Vector3, pos: Vector3, mat: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = mat
	mi.position = pos
	parent.add_child(mi)
	return mi

func _part_cylinder(parent: Node, radius: float, height: float, pos: Vector3, mat: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = radius
	cm.bottom_radius = radius
	cm.height = height
	mi.mesh = cm
	mi.material_override = mat
	mi.position = pos
	parent.add_child(mi)
	return mi

func animate(delta: float, movement: float, sprint := false) -> void:
	anim_time += delta * (8.0 if sprint else 5.5)

	var amount := clampf(movement, 0.0, 1.0)
	var swing := sin(anim_time) * 0.72 * amount
	var arm_swing := swing * 0.85

	left_leg.rotation.x = swing
	right_leg.rotation.x = -swing
	left_arm.rotation.x = -arm_swing
	right_arm.rotation.x = arm_swing

	torso.rotation.x = lerpf(torso.rotation.x, -0.10 if sprint and amount > 0.2 else 0.0, minf(1.0, delta * 7.0))
	torso.position.y = 1.30 + sin(anim_time * 0.5) * 0.012 + absf(sin(anim_time)) * 0.025 * amount
	head_pivot.rotation.y = sin(anim_time * 0.33) * 0.035 * (1.0 - amount)

func set_variant(seed_value: int) -> void:
	variant_seed = seed_value
