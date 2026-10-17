const fs = require('node:fs')
const { spawnSync } = require('node:child_process')

// Dependabot updates only Maven dependencies, so the Android SDK packages from the version catalog
// are compared with the latest stable ones from the SDK repository instead.
const catalogPath = 'gradle/libs.versions.toml'
const repositoryUrl = 'https://dl.google.com/android/repository/repository2-3.xml'
const issueTitle = 'Android SDK updates available'

function catalogVersions(catalog) {
  const version = name => {
    const match = catalog.match(new RegExp(`^${name}\\s*=\\s*"([^"]+)"`, 'm'))
    if (!match) {
      throw new Error(`${name} must exist in ${catalogPath}`)
    }
    return match[1]
  }
  return { ndk: version('ndk'), cmake: version('cmake'), compileSdk: version('compileSdk') }
}

// Platforms of minor SDK releases (android-36.1), extensions and previews are skipped,
// API 37 and newer are published as android-37.0
function stableVersions(repository) {
  const channels = new Map([...repository.matchAll(/<channel id="([^"]+)">([^<]+)<\/channel>/g)].map(m => [m[1], m[2]]))
  const versions = { ndk: [], cmake: [], compileSdk: [] }
  for (const [, path, body] of repository.matchAll(/<remotePackage path="([^"]+)">([\s\S]*?)<\/remotePackage>/g)) {
    const channel = body.match(/<channelRef ref="([^"]+)"\/>/)
    if (!channel || channels.get(channel[1]) !== 'stable' || body.includes('<obsolete')) {
      continue
    }
    const [, type, version] = path.match(/^([^;]+);(.+)$/) || []
    if (type === 'ndk' || type === 'cmake') {
      versions[type].push(version)
    } else if (type === 'platforms') {
      const api = version.match(/^android-(\d+)(?:\.0)?$/)
      if (api) {
        versions.compileSdk.push(api[1])
      }
    }
  }
  return Object.fromEntries(Object.entries(versions).map(([name, list]) => [name, list.sort(compareVersions).at(-1)]))
}

function compareVersions(a, b) {
  const left = a.split('.').map(Number)
  const right = b.split('.').map(Number)
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const difference = (left[i] || 0) - (right[i] || 0)
    if (difference !== 0) {
      return Math.sign(difference)
    }
  }
  return 0
}

function availableUpdates(current, latest) {
  return Object.keys(current)
    .filter(name => latest[name] && compareVersions(latest[name], current[name]) > 0)
    .map(name => ({ name, current: current[name], latest: latest[name] }))
}

function issueBody(updates) {
  return [
    'Newer stable Android SDK packages than in `gradle/libs.versions.toml`:',
    '',
    '| Package | Current | Latest stable |',
    '| --- | --- | --- |',
    ...updates.map(update => `| \`${update.name}\` | ${update.current} | ${update.latest} |`),
    '',
    'The NDK and CMake change the published native libraries, so compare the artifact with the previous one,',
    'run the instrumented tests on the minSdk emulator and check that the NDK still supports minSdk.',
    'targetSdk is updated separately from compileSdk, it changes the runtime behavior.',
    '',
    '_Updated by the SDK updates workflow._'
  ].join('\n')
}

function gh(...args) {
  const result = spawnSync('gh', args, { encoding: 'utf8' })
  if (result.status !== 0) {
    throw new Error(`gh ${args[0]} ${args[1]} failed: ${result.stderr}`)
  }
  return result.stdout
}

async function main() {
  const response = await fetch(repositoryUrl)
  if (!response.ok) {
    throw new Error(`${repositoryUrl} returned ${response.status}`)
  }
  const updates = availableUpdates(catalogVersions(fs.readFileSync(catalogPath, 'utf8')), stableVersions(await response.text()))
  const body = updates.length > 0 ? issueBody(updates) : null
  console.log(body || 'The Android SDK packages are up to date.')
  if (process.env.DRY_RUN) {
    return
  }
  const [existing] = JSON.parse(gh('issue', 'list', '--state', 'open', '--search', `"${issueTitle}" in:title`, '--json', 'number,title,body'))
    .filter(issue => issue.title === issueTitle)
  if (!body) {
    if (existing) {
      gh('issue', 'close', String(existing.number), '--comment', 'The Android SDK packages are up to date.')
    }
  } else if (!existing) {
    gh('issue', 'create', '--title', issueTitle, '--body', body)
  } else if (existing.body.replace(/\r\n/g, '\n') !== body) {
    gh('issue', 'edit', String(existing.number), '--body', body)
  }
}

module.exports = { catalogVersions, stableVersions, compareVersions, availableUpdates, issueBody }

if (require.main === module) {
  main().catch(error => {
    console.error(error.message)
    process.exitCode = 1
  })
}
