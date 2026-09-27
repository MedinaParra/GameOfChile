class_name AMCar
extends CharacterBody3D

const MAX_SPEED := 38.0
const REVERSE_SPEED := 13.0
const ACCEL := 24.0
const BRAKE := 36.0
const DRAG := 10.0
const GRAVITY := 28.0

var speed := 0.0
var active := false
var driver: AMPlayer = null
var camera: Camera3D

func _ready() -> void:
	add_to_group("vehicle")
	_build_car()
	_build_camera()

func _mat(c: Color, rough := 0.6, metallic := 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	m.metallic = metallic
	return m

func _build_car() -> void:
	var shape := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(2.0, 1.15, 4.4)
	shape.shape = bs
	shape.position.y = 0.72
	add_child(shape)

	var red := _mat(Color("#922e2e"), 0.34, 0.35)
	var dark := _mat(Color("#121417"), 0.76)
	var glass := _mat(Color("#1c3443"), 0.18, 0.15)
	_part(Vector3(2.0,0.62,4.25),Vector3(0,0.72,0),red)
	_part(Vector3(1.66,0.72,1.85),Vector3(0,1.27,-0.1),glass)
	_part(Vector3(1.72,0.11,1.5),Vector3(0,1.67,-0.1),red)
	_part(Vector3(1.85,0.18,1.1),Vector3(0,0.98,1.55),red)
	for sx in [-1.0,1.0]:
		for sz in [-1.0,1.0]:
			var wheel := MeshInstance3D.new()
			var cyl := CylinderMesh.new()
			cyl.top_radius = 0.38
			cyl.bottom_radius = 0.38
			cyl.height = 0.30
			wheel.mesh = cyl
			wheel.material_override = dark
			wheel.rotation.z = PI/2.0
			wheel.position = Vector3(sx*1.02,0.40,sz*1.43)
			add_child(wheel)

func _part(size: Vector3, pos: Vector3, mat: Material) -> void:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = mat
	mi.position = pos
	add_child(mi)

func _build_camera() -> void:
	var pivot := Node3D.new()
	pivot.position = Vector3(0,1.2,0)
	add_child(pivot)
	camera = Camera3D.new()
	camera.position = Vector3(0.7,2.4,7.0)
	camera.fov = 72.0
	camera.current = false
	pivot.add_child(camera)

func enter_driver(p: AMPlayer) -> void:
	if active:
		return
	driver = p
	active = true
	p.set_driving(self)
	camera.current = true

func exit_driver() -> void:
	if driver == null:
		return
	camera.current = false
	var p := driver
	driver = null
	active = false
	p.leave_car(global_position + transform.basis.x*2.5 + Vector3.UP*0.8)

func _physics_process(delta: float) -> void:
	if not active:
		if not is_on_floor():
			velocity.y -= GRAVITY*delta
			move_and_slide()
		return

	var throttle := 0.0
	if Input.is_key_pressed(KEY_W):
		throttle += 1.0
	if Input.is_key_pressed(KEY_S):
		throttle -= 1.0
	if throttle > 0.0:
		speed = move_toward(speed,MAX_SPEED,ACCEL*delta)
	elif throttle < 0.0:
		speed = move_toward(speed,-REVERSE_SPEED,BRAKE*delta)
	else:
		speed = move_toward(speed,0.0,DRAG*delta)
	if Input.is_key_pressed(KEY_SPACE):
		speed = move_toward(speed,0.0,BRAKE*1.8*delta)

	var steer := 0.0
	if Input.is_key_pressed(KEY_A):
		steer += 1.0
	if Input.is_key_pressed(KEY_D):
		steer -= 1.0
	if absf(speed) > 0.5:
		rotation.y += steer*1.6*delta*(1.0 if speed >= 0 else -1.0)

	var forward := -transform.basis.z
	velocity.x = forward.x*speed
	velocity.z = forward.z*speed
	velocity.y = -0.5 if is_on_floor() else velocity.y-GRAVITY*delta
	move_and_slide()
	if is_on_wall():
		speed *= 0.35
