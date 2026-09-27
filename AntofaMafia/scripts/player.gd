class_name AMPlayer
extends CharacterBody3D

const WALK_SPEED := 6.4
const RUN_SPEED := 10.8
const GRAVITY := 28.0
const MOUSE_SENS := 0.00245

var active := true
var yaw := 0.0
var pitch := -0.12
var camera_pivot: Node3D
var pitch_pivot: Node3D
var camera: Camera3D
var body_shape: CollisionShape3D
var current_car: AMCar = null
var visual: AMHumanoid

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

func _build_camera() -> void:
	camera_pivot = Node3D.new()
	camera_pivot.position = Vector3(0.0, 1.42, 0.0)
	add_child(camera_pivot)

	pitch_pivot = Node3D.new()
	camera_pivot.add_child(pitch_pivot)

	camera = Camera3D.new()
	camera.position = Vector3(0.92, 1.48, 4.85)
	camera.fov = 67.0
	camera.near = 0.08
	camera.current = true
	pitch_pivot.add_child(camera)

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and active and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		yaw -= event.relative.x * MOUSE_SENS
		pitch = clampf(pitch - event.relative.y * MOUSE_SENS, -0.72, 0.48)
		camera_pivot.rotation.y = yaw
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

	velocity.x = move_toward(velocity.x, target.x, 28.0 * delta)
	velocity.z = move_toward(velocity.z, target.z, 28.0 * delta)
	if not is_on_floor():
		velocity.y -= GRAVITY * delta
	else:
		velocity.y = -0.5

	move_and_slide()

	var movement_amount := clampf(Vector2(velocity.x, velocity.z).length() / RUN_SPEED, 0.0, 1.0)
	visual.animate(delta, movement_amount, sprinting)

	if world_dir.length_squared() > 0.05:
		var target_yaw := atan2(-world_dir.x, -world_dir.z)
		rotation.y = lerp_angle(rotation.y, target_yaw, minf(1.0, delta * 10.0))

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
