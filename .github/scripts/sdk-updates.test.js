const assert = require('node:assert/strict')
const fs = require('node:fs')
const { test } = require('node:test')
const {
  catalogVersions,
  stableVersions,
  compareVersions,
  availableUpdates,
  issueBody
} = require('./sdk-updates')

const repository = `
<sdk:sdk-repository>
  <channel id="channel-0">stable</channel>
  <channel id="channel-1">beta</channel>
  <remotePackage path="ndk;29.0.14206865"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="ndk;30.0.16248370"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="ndk;31.0.1"><channelRef ref="channel-1"/></remotePackage>
  <remotePackage path="ndk;9.0.1"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="cmake;3.22.1"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="cmake;4.1.2"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="cmake;4.2.0"><obsolete/><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="platforms;android-36"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="platforms;android-36.1"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="platforms;android-36-ext19"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="platforms;android-37.0"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="platforms;android-37.2-beta3"><channelRef ref="channel-0"/></remotePackage>
  <remotePackage path="platforms;android-CANARY"><channelRef ref="channel-0"/></remotePackage>
</sdk:sdk-repository>
`

test('reads the SDK package versions from the version catalog', () => {
  const versions = catalogVersions(fs.readFileSync('gradle/libs.versions.toml', 'utf8'))
  assert.match(versions.ndk, /^\d+\.\d+\.\d+$/)
  assert.match(versions.cmake, /^\d+\.\d+\.\d+$/)
  assert.match(versions.compileSdk, /^\d+$/)
  assert.deepEqual(catalogVersions('compileSdk = "36"\nminSdk = "21"\nndk = "29.0.14206865"\ncmake = "4.1.2"'), {
    ndk: '29.0.14206865',
    cmake: '4.1.2',
    compileSdk: '36'
  })
  assert.throws(() => catalogVersions('ndk = "29.0.14206865"'), /cmake must exist/)
})

test('picks the newest stable versions, skipping previews, obsolete packages and minor SDKs', () => {
  assert.deepEqual(stableVersions(repository), { ndk: '30.0.16248370', cmake: '4.1.2', compileSdk: '37' })
})

test('compares versions numerically', () => {
  assert.equal(compareVersions('30.0.1', '29.0.14206865'), 1)
  assert.equal(compareVersions('9.0', '10.0'), -1)
  assert.equal(compareVersions('4.1', '4.1.0'), 0)
})

test('reports only newer versions', () => {
  const updates = availableUpdates(
    { ndk: '29.0.14206865', cmake: '4.1.2', compileSdk: '37' },
    { ndk: '30.0.16248370', cmake: '4.1.2', compileSdk: '36' }
  )
  assert.deepEqual(updates, [{ name: 'ndk', current: '29.0.14206865', latest: '30.0.16248370' }])
  assert.match(issueBody(updates), /\| `ndk` \| 29\.0\.14206865 \| 30\.0\.16248370 \|/)
})
