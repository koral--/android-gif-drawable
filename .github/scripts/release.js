const assert = require('node:assert/strict')
const fs = require('node:fs')
const { spawnSync } = require('node:child_process')

const changelogPath = 'CHANGELOG.md'
const gradlePropertiesPath = 'gradle.properties'
const readmePath = 'README.md'
const repositoryUrl = 'https://github.com/koral--/android-gif-drawable'
const placeholder = '- TODO: describe the changes'

function getVersionFromProperties(gradleProperties) {
  const match = gradleProperties.match(/^VERSION_NAME\s*=\s*(.+)$/m)
  assert.ok(match, 'VERSION_NAME must exist in gradle.properties')
  return match[1].trim()
}

function centralUrl(version) {
  return `https://repo1.maven.org/maven2/pl/droidsonroids/gif/android-gif-drawable/${version}/android-gif-drawable-${version}.pom`
}

function compareUrl(previousTag, version) {
  return `${repositoryUrl}/compare/${previousTag}...v${version}`
}

function validateVersion(mode, version, gradleProperties, ref, defaultBranch) {
  assert.ok(defaultBranch && ref === `refs/heads/${defaultBranch}`, `Run this on ${defaultBranch}`)
  assert.match(version, /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/, 'Use a version like 1.2.33, without a v prefix')
  const currentVersion = getVersionFromProperties(gradleProperties)
  if (mode === 'prepare') {
    const current = currentVersion.split('.').map(BigInt)
    const requested = version.split('.').map(BigInt)
    const changed = requested.findIndex((part, index) => part !== current[index])
    assert.ok(changed >= 0 && requested[changed] > current[changed], `${version} must be greater than ${currentVersion}`)
  } else {
    assert.equal(version, currentVersion, 'Merge the version bump before releasing')
  }
}

// Turns GitHub generated notes into changelog bullets. Headings, the contributor list
// and the "Full Changelog" line are dropped, the compare link in the entry replaces the latter.
function bulletsFromGeneratedNotes(notes) {
  const bullets = []
  let skipSection = false
  for (const line of notes.replace(/\r\n/g, '\n').split('\n')) {
    if (line.startsWith('#')) {
      skipSection = /new contributors/i.test(line)
    } else if (!skipSection && /^[*-] /.test(line)) {
      bullets.push(`- ${line.slice(2).trim()}`)
    }
  }
  return bullets
}

function findEntry(changelog, version) {
  const lines = changelog.split('\n')
  const start = lines.findIndex(line => /^#+ /.test(line) && line.replace(/^#+ /, '').trim() === version)
  if (start < 0) {
    return null
  }
  const remaining = lines.slice(start + 1)
  const end = remaining.findIndex(line => /^#+ /.test(line))
  return remaining.slice(0, end < 0 ? remaining.length : end)
}

function prepareRelease(version, previousTag, today, gradleProperties, changelog, readme, notes) {
  assert.ok(previousTag, 'Previous release tag is unknown')
  assert.equal(findEntry(changelog, version), null, 'Changelog already contains this version')

  const currentVersion = getVersionFromProperties(gradleProperties)
  const updatedGradleProperties = gradleProperties.replace(/^VERSION_NAME\s*=.*$/m, `VERSION_NAME=${version}`)
  const updatedReadme = readme.replaceAll(
    `pl.droidsonroids.gif:android-gif-drawable:${currentVersion}'`,
    `pl.droidsonroids.gif:android-gif-drawable:${version}'`
  )

  const bullets = bulletsFromGeneratedNotes(notes || '')
  const entry = [
    `#### ${version}`,
    `- ${today} - [commits](${compareUrl(previousTag, version)})`,
    ...(bullets.length ? bullets : [placeholder])
  ].join('\n')

  return {
    gradleProperties: updatedGradleProperties,
    readme: updatedReadme,
    changelog: `${entry}\n\n${changelog.trimStart()}`
  }
}

// The GitHub release body: the entry bullets without the date line, followed by the compare link.
function releaseNotes(version, changelog) {
  const entry = findEntry(changelog, version)
  assert.ok(entry, `Changelog has no entry for ${version}`)
  let commits = null
  const bullets = []
  for (const line of entry) {
    const dateLine = line.match(/^- \d{4}-\d{2}-\d{2} - \[commits\]\((.+)\)\s*$/)
    if (dateLine) {
      commits = dateLine[1]
    } else if (line.trim()) {
      bullets.push(line)
    }
  }
  assert.ok(bullets.length, `Changelog entry for ${version} is empty`)
  assert.ok(!bullets.includes(placeholder), `Changelog entry for ${version} still contains the TODO placeholder`)
  return commits ? `${bullets.join('\n')}\n\n**Full Changelog**: ${commits}` : bullets.join('\n')
}

async function isPublished(url) {
  try {
    const res = await fetch(url, { redirect: 'follow', signal: AbortSignal.timeout(15000) })
    return res.status === 200
  } catch {
    return false
  }
}

async function checkUnpublished(version) {
  const tag = spawnSync('git', ['show-ref', '--verify', '--quiet', `refs/tags/v${version}`])
  assert.equal(tag.status, 1, `Tag v${version} already exists or tags could not be checked`)

  const onCentral = await isPublished(centralUrl(version))
  assert.ok(!onCentral, `Maven Central: version ${version} is already published`)
}

async function main() {
  const [command, notesPath] = process.argv.slice(2)
  const version = process.env.VERSION
  const changelog = fs.readFileSync(changelogPath, 'utf8')
  const gradleProperties = fs.readFileSync(gradlePropertiesPath, 'utf8')
  const readme = fs.readFileSync(readmePath, 'utf8')

  if (command === 'notes') {
    console.log(releaseNotes(version, changelog))
    return
  }
  if (command === 'is-published-central') {
    process.exitCode = await isPublished(centralUrl(version)) ? 0 : 1
    return
  }

  assert.ok(['check-prepare', 'check-release', 'prepare'].includes(command), 'Unknown release command')

  validateVersion(
    command === 'check-release' ? 'release' : 'prepare',
    version,
    gradleProperties,
    process.env.GITHUB_REF,
    process.env.DEFAULT_BRANCH
  )

  if (command === 'prepare') {
    const notes = fs.readFileSync(notesPath, 'utf8')
    const today = new Date().toISOString().slice(0, 10)
    const next = prepareRelease(version, process.env.PREVIOUS_TAG, today, gradleProperties, changelog, readme, notes)
    fs.writeFileSync(gradlePropertiesPath, next.gradleProperties)
    fs.writeFileSync(readmePath, next.readme)
    fs.writeFileSync(changelogPath, next.changelog)
    return
  }

  if (command === 'check-release') {
    releaseNotes(version, changelog)
  }

  await checkUnpublished(version)
}

module.exports = {
  getVersionFromProperties,
  validateVersion,
  bulletsFromGeneratedNotes,
  prepareRelease,
  releaseNotes,
  centralUrl
}

if (require.main === module) {
  main().catch(error => {
    console.error(error.message)
    process.exitCode = 1
  })
}
