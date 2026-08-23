Pod::Spec.new do |s|
  s.name = 'MuxyTerminalInput'
  s.version = '1.0.0'
  s.summary = 'Terminal input support for Muxy'
  s.description = 'iOS terminal input with external keyboard support for Muxy'
  s.license = { type: 'MIT' }
  s.author = { 'Muxy' => 'support@muxy.app' }
  s.homepage = 'https://github.com/muxy-app/mobile'
  s.platforms = { ios: '17.0' }
  s.source = { git: 'https://github.com/muxy-app/mobile.git' }
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.source_files = '**/*.{h,m,mm,swift}'
  s.swift_version = '5.9'
end
