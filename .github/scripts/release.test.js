const assert = require('node:assert/strict')
const { test } = require('node:test')
const {
  getVersionFromProperties,
  validateVersion,
  bulletsFromGeneratedNotes,
  prepareRelease,
  releaseNotes,
  centralUrl
} = require('./release')

const sampleGradleProperties = 'GROUP=pl.droidsonroids.gif\nVERSION_NAME=1.2.32\nPOM_DEVELOPER_NAME=Karol Wr\\u00f3tniak\n'
const sampleReadme = `
\`\`\`groovy
dependencies {
    implementation 'pl.droidsonroids.gif:android-gif-drawable:1.2.32'
}
\`\`\`

\`\`\`groovy
dependencies {
    implementation 'pl.droidsonroids.gif:android-gif-drawable:1.2.+'
}
\`\`\`
`
const sampleChangelog = `#### 1.2.32
- 2026-06-18 - [commits](https://github.com/koral--/android-gif-drawable/compare/v1.2.31...v1.2.32)
- Fix use-after-free, fixes [(#870)](https://github.com/koral--/android-gif-drawable/issues/870)

#### 1.2.31
- 2026-02-12 - [commits](https://github.com/koral--/android-gif-drawable/compare/v1.2.30...v1.2.31)
- Remove Kotlin dependency from library module
`
const generatedNotes = `## What's Changed\r
* Fix JNI memory-safety bugs by @koral-- in https://github.com/koral--/android-gif-drawable/pull/873\r
* Update NDK by @someone in https://github.com/koral--/android-gif-drawable/pull/874\r
\r
## New Contributors\r
* @someone made their first contribution in https://github.com/koral--/android-gif-drawable/pull/874\r
\r
**Full Changelog**: https://github.com/koral--/android-gif-drawable/compare/v1.2.32...v1.2.33\r
`

test('extracts current version from gradle.properties', () => {
  assert.equal(getVersionFromProperties(sampleGradleProperties), '1.2.32')
  assert.throws(() => getVersionFromProperties('FOO=BAR\n'))
})

test('prepare requires an increasing stable version and default branch', () => {
  for (const version of ['1.2.33', '1.3.0', '2.0.0']) {
    validateVersion('prepare', version, sampleGradleProperties, 'refs/heads/dev', 'dev')
  }
  for (const version of ['1.2.32', '1.2.31', '1.1.40', 'v1.2.33', '1.2.33-rc1', '1.2', '1.2.33\n']) {
    assert.throws(() => validateVersion('prepare', version, sampleGradleProperties, 'refs/heads/dev', 'dev'))
  }
  for (const ref of ['refs/heads/release/v1.2.33', 'refs/heads/master', 'refs/tags/dev']) {
    assert.throws(() => validateVersion('prepare', '1.2.33', sampleGradleProperties, ref, 'dev'))
  }
})

test('release rejects an unmerged bump and wrong branch', () => {
  validateVersion('release', '1.2.32', sampleGradleProperties, 'refs/heads/dev', 'dev')
  assert.throws(() => validateVersion('release', '1.2.33', sampleGradleProperties, 'refs/heads/dev', 'dev'))
  assert.throws(() => validateVersion('release', '1.2.32', sampleGradleProperties, 'refs/heads/release/v1.2.32', 'dev'))
})

test('generated notes become changelog bullets without headings and contributors', () => {
  assert.deepEqual(bulletsFromGeneratedNotes(generatedNotes), [
    '- Fix JNI memory-safety bugs by @koral-- in https://github.com/koral--/android-gif-drawable/pull/873',
    '- Update NDK by @someone in https://github.com/koral--/android-gif-drawable/pull/874'
  ])
  assert.deepEqual(bulletsFromGeneratedNotes('**Full Changelog**: https://example.com\n'), [])
})

test('prepare updates gradle.properties, readme and changelog in the existing format', () => {
  const result = prepareRelease('1.2.33', 'v1.2.32', '2026-10-16', sampleGradleProperties, sampleChangelog, sampleReadme, generatedNotes)

  assert.equal(getVersionFromProperties(result.gradleProperties), '1.2.33')
  assert.ok(result.gradleProperties.includes('POM_DEVELOPER_NAME=Karol Wr\\u00f3tniak'))
  assert.ok(result.readme.includes("android-gif-drawable:1.2.33'"))
  assert.ok(result.readme.includes("android-gif-drawable:1.2.+'"))
  assert.ok(!result.readme.includes("android-gif-drawable:1.2.32'"))

  assert.ok(result.changelog.startsWith(`#### 1.2.33
- 2026-10-16 - [commits](https://github.com/koral--/android-gif-drawable/compare/v1.2.32...v1.2.33)
- Fix JNI memory-safety bugs by @koral-- in https://github.com/koral--/android-gif-drawable/pull/873
- Update NDK by @someone in https://github.com/koral--/android-gif-drawable/pull/874

#### 1.2.32
`))
  assert.ok(result.changelog.endsWith(sampleChangelog.trimStart()))
})

test('prepare inserts a placeholder that blocks the release until replaced', () => {
  const result = prepareRelease('1.2.33', 'v1.2.32', '2026-10-16', sampleGradleProperties, sampleChangelog, sampleReadme, '**Full Changelog**: x\n')
  assert.ok(result.changelog.includes('- TODO: describe the changes'))
  assert.throws(() => releaseNotes('1.2.33', result.changelog), /placeholder/)

  const edited = result.changelog.replace('- TODO: describe the changes', '- Fix a crash, thanks @someone for reporting')
  assert.equal(releaseNotes('1.2.33', edited), `- Fix a crash, thanks @someone for reporting

**Full Changelog**: https://github.com/koral--/android-gif-drawable/compare/v1.2.32...v1.2.33`)
})

test('prepare rejects an existing version and an unknown previous tag', () => {
  assert.throws(() => prepareRelease('1.2.32', 'v1.2.31', '2026-10-16', sampleGradleProperties, sampleChangelog, sampleReadme, generatedNotes))
  assert.throws(() => prepareRelease('1.2.33', '', '2026-10-16', sampleGradleProperties, sampleChangelog, sampleReadme, generatedNotes))
})

test('releaseNotes reads only the requested entry', () => {
  assert.equal(releaseNotes('1.2.31', sampleChangelog), `- Remove Kotlin dependency from library module

**Full Changelog**: https://github.com/koral--/android-gif-drawable/compare/v1.2.30...v1.2.31`)
  assert.throws(() => releaseNotes('1.2.33', sampleChangelog), /no entry/)
  assert.throws(() => releaseNotes('1.2.33', '#### 1.2.33\n- 2026-10-16 - [commits](https://x)\n\n#### 1.2.32\n- Old\n'), /empty/)
})

test('central URL matches the published coordinates', () => {
  assert.equal(
    centralUrl('1.2.32'),
    'https://repo1.maven.org/maven2/pl/droidsonroids/gif/android-gif-drawable/1.2.32/android-gif-drawable-1.2.32.pom'
  )
})
