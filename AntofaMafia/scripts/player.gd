class_name AMPlayer
extends CharacterBody3D

const WALK_SPEED := 6.4
const RUN_SPEED := 10.8
const GRAVITY := 28.0
const MOUSE_SENS := 0.00225
const EYE_HEIGHT := 1.72

var active := true
var yaw := 0.0
var pitch := 0.0
var camera_pivot: Node3D
var pitch_pivot: Node3D
var camera: Camera3D
var body_shape: CollisionShape3D
var current_car: AMCar = null
var visual: AMHumanoid
var viewmodel: Node3D
var move_time := 0.0

func _ready() -> void:
	add_to_group("player")
	_build_body()
	_build_camera()
	Input.mouse_mode = Input.MOUSE_MODE_CAPTURED

func _build_body() -> void:
	body_shape = CollisionShape3D.new()
	var capsule_shape := CapsuleShape3D.new()
	capsule_shape.radius = 0.40
	capsule_shape.height = 1.72
	body_shape.shape = capsule_shape
	body_shape.position.y = 0.86
	add_child(body_shape)

	visual = AMHumanoid.new()
	visual.variant_seed = 2026
	add_child(visual)
	visual.call_deferred("set_first_person_mode", true)

func _build_camera() -> void:
	camera_pivot = Node3D.new()
	camera_pivot.position = Vector3(0.0, EYE_HEIGHT, 0.0)
	add_child(camera_pivot)

	pitch_pivot = Node3D.new()
	camera_pivot.add_child(pitch_pivot)

	camera = Camera3D.new()
	camera.position = Vector3.ZERO
	camera.fov = 76.0
	camera.near = 0.035
	camera.current = true
	pitch_pivot.add_child(camera)

	_build_viewmodel()

func _build_viewmodel() -> void:
	viewmodel = Node3D.new()
	viewmodel.position = Vector3(0.0, -0.29, -0.62)
	camera.add_child(viewmodel)

	var sleeve := StandardMaterial3D.new()
	sleeve.albedo_color = Color("#243243")
	sleeve.roughness = 0.86
	var skin := StandardMaterial3D.new()
	skin.albedo_color = Color("#d9a17f")
	skin.roughness = 0.92

	# Two forearms visible at the bottom of the first-person camera.
	_vm_box(Vector3(0.16, 0.16, 0.58), Vector3(-0.28, -0.02, 0.0), sleeve, -0.10)
	_vm_box(Vector3(0.16, 0.16, 0.58), Vector3(0.28, -0.02, 0.0), sleeve, 0.10)
	_vm_box(Vector3(0.18, 0.14, 0.23), Vector3(-0.28, -0.02, -0.39), skin, -0.08)
	_vm_box(Vector3(0.18, 0.14, 0.23), Vector3(0.28, -0.02, -0.39), skin, 0.08)

func _vm_box(size: Vector3, pos: Vector3, mat: Material, yaw_offset: float) -> void:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = mat
	mi.position = pos
	mi.rotation.y = yaw_offset
	viewmodel.add_child(mi)

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and active and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		yaw -= event.relative.x * MOUSE_SENS
		pitch = clampf(pitch - event.relative.y * MOUSE_SENS, -1.38, 1.30)
		rotation.y = yaw
		pitch_pivot.rotation.x = pitch

	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_E:
		if active:
			var best_car: AMCar = null
			var best_dist := 999999.0
			for node in get_tree().get_nodes_in_group("vehicle"):
				if node is AMCar:
					var d := global_position.distance_to(node.global_position)
					if d < best_dist:
						best_dist = d
						best_car = node
			if best_car != null and best_dist < 5.0:
				best_car.enter_driver(self)
		elif current_car != null:
			current_car.exit_driver()

func _physics_process(delta: float) -> void:
	if not active:
		velocity = Vector3.ZERO
		return

	var x_input := 0.0
	var z_input := 0.0
	if Input.is_key_pressed(KEY_A):
		x_input -= 1.0
	if Input.is_key_pressed(KEY_D):
		x_input += 1.0
	if Input.is_key_pressed(KEY_W):
		z_input -= 1.0
	if Input.is_key_pressed(KEY_S):
		z_input += 1.0

	var input_vec := Vector2(x_input, z_input)
	if input_vec.length() > 1.0:
		input_vec = input_vec.normalized()

	var local_dir := Vector3(input_vec.x, 0.0, input_vec.y)
	var world_dir := local_dir.rotated(Vector3.UP, yaw)
	var sprinting := Input.is_key_pressed(KEY_SHIFT) and input_vec.length() > 0.1
	var speed := RUN_SPEED if sprinting else WALK_SPEED
	var target := world_dir * speed

	velocity.x = move_toward(velocity.x, target.x, 30.0 * delta)
	velocity.z = move_toward(velocity.z, target.z, 30.0 * delta)
	if not is_on_floor():
		velocity.y -= GRAVITY * delta
	else:
		velocity.y = -0.5

	move_and_slide()

	var movement_amount := clampf(Vector2(velocity.x, velocity.z).length() / RUN_SPEED, 0.0, 1.0)
	visual.animate(delta, movement_amount, sprinting)

	# Small FPS head-bob + arm sway. Deliberately restrained to avoid nausea.
	if movement_amount > 0.03 and is_on_floor():
		move_time += delta * (11.0 if sprinting else 7.5)
		camera_pivot.position.y = EYE_HEIGHT + sin(move_time * 2.0) * 0.014 * movement_amount
		viewmodel.position.x = sin(move_time) * 0.010 * movement_amount
		viewmodel.position.y = -0.29 + absf(sin(move_time * 2.0)) * 0.012 * movement_amount
	else:
		camera_pivot.position.y = lerpf(camera_pivot.position.y, EYE_HEIGHT, minf(1.0, delta * 8.0))
		viewmodel.position = viewmodel.position.lerp(Vector3(0.0, -0.29, -0.62), minf(1.0, delta * 8.0))

func set_driving(car: AMCar) -> void:
	current_car = car
	active = false
	visible = false
	collision_layer = 0
	collision_mask = 0
	camera.current = false

func leave_car(exit_pos: Vector3) -> void:
	global_position = exit_pos
	visible = true
	active = true
	collision_layer = 1
	collision_mask = 1
	camera.current = true
	current_car = null
	rotation.y = yaw
