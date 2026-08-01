from pathlib import Path

project = Path('project')

preset_path = project / 'export_presets.cfg'
preset = preset_path.read_text(encoding='utf-8')
preset = preset.replace('architectures/x86_64=true', 'architectures/x86_64=false')
preset_path.write_text(preset, encoding='utf-8')

config_path = project / 'project.godot'
config = config_path.read_text(encoding='utf-8')
config = config.replace('rendering_device/driver.android="vulkan"\n', '')
config_path.write_text(config, encoding='utf-8')

print('Final ARM export configured: ARM64/ARMv7, Mobile renderer with automatic fallback')
