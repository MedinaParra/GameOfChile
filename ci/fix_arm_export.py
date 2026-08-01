from pathlib import Path

project = Path('project')

preset_path = project / 'export_presets.cfg'
preset = preset_path.read_text(encoding='utf-8')
preset = preset.replace('architectures/x86_64=true', 'architectures/x86_64=false')
preset_path.write_text(preset, encoding='utf-8')

config_path = project / 'project.godot'
config = config_path.read_text(encoding='utf-8')
config = config.replace('rendering_device/driver.android="vulkan"\n', '')

# Godot's Android exporter silently rejects a project when ETC2/ASTC mobile
# texture import is not enabled. Remove malformed/duplicate declarations and
# write the exact setting inside the [rendering] section as the final patch.
lines = config.splitlines()
cleaned: list[str] = []
in_wrong_textures_section = False
for line in lines:
    if line == '[textures]':
        in_wrong_textures_section = True
        continue
    if in_wrong_textures_section and line.startswith('['):
        in_wrong_textures_section = False
    if in_wrong_textures_section:
        if line.strip() == 'vram_compression/import_etc2_astc=true' or not line.strip():
            continue
    if line.startswith('textures/vram_compression/import_etc2_astc='):
        continue
    cleaned.append(line)

try:
    rendering_index = cleaned.index('[rendering]')
except ValueError as exc:
    raise RuntimeError('Missing [rendering] section in project.godot') from exc

cleaned.insert(rendering_index + 1, 'textures/vram_compression/import_etc2_astc=true')
config_path.write_text('\n'.join(cleaned).rstrip() + '\n', encoding='utf-8')

print('Final ARM export configured: ARM64/ARMv7, Mobile renderer, ETC2/ASTC enabled')
