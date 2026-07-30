Pod::Spec.new do |s|
  s.name = 'MuxySsh'
  s.version = '1.0.0'
  s.summary = 'Secure Shell sessions for Muxy'
  s.description = 'Apple-only SSH transport for the Muxy React Native app'
  s.license = { type: 'MIT' }
  s.author = { 'Muxy' => 'support@muxy.app' }
  s.homepage = 'https://github.com/muxy-app/muxy-mobile'
  s.platforms = { ios: '17.0' }
  s.source = { git: 'https://github.com/muxy-app/muxy-mobile.git' }
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.source_files = '**/*.{h,m,mm,swift}'
  s.swift_version = '5.9'

  spm_dependency(
    s,
    url: 'https://github.com/orlandos-nl/Citadel.git',
    requirement: { kind: 'exactVersion', version: '0.12.1' },
    products: ['Citadel']
  )
end
