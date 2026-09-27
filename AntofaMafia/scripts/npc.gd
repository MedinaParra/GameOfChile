class_name AMNpc
extends CharacterBody3D

var rng := RandomNumberGenerator.new()
var direction := Vector3.ZERO
var timer := 0.0
var walk_speed := 2.2
var visual: AMHumanoid

func _ready() -> void:
	rng.seed = int(global_position.x * 31.0 + global_position.z * 17.0 + 50000.0)
	_build_body()
	_choose_direction()

func _build_body() -> void:
	var shape := CollisionShape3D.new()
	var cap_shape := CapsuleShape3D.new()
	cap_shape.radius = 0.34
	cap_shape.height = 1.68
	shape.shape = cap_shape
	shape.position.y = 0.84
	add_child(shape)

	visual = AMHumanoid.new()
	visual.variant_seed = int(absf(global_position.x * 13.0 + global_position.z * 7.0)) + 3
	visual.scale = Vector3.ONE * rng.randf_range(0.94, 1.05)
	add_child(visual)

func _choose_direction() -> void:
	timer = rng.randf_range(2.0, 6.0)
	var angle := rng.randf_range(-PI, PI)
	direction = Vector3(cos(angle), 0.0, sin(angle)).normalized()

func _physics_process(delta: float) -> void:
	timer -= delta
	if timer <= 0.0:
		_choose_direction()

	velocity.x = direction.x * walk_speed
	velocity.z = direction.z * walk_speed
	if not is_on_floor():
		velocity.y -= 25.0 * delta
	else:
		velocity.y = -0.5

	move_and_slide()
	if is_on_wall():
		direction = -direction
		timer = 1.2

	visual.animate(delta, 0.55, false)

	if direction.length_squared() > 0.01:
		rotation.y = lerp_angle(rotation.y, atan2(-direction.x, -direction.z), minf(1.0, delta * 5.0))
