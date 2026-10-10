/**
 * KinderJournal dependency bootstrap.
 *
 * Downloads the exact JavaFX and SQLite JDBC artifacts the app needs into
 * .tools/lib. Run it once (build.bat does this automatically); afterwards the
 * whole project builds and runs completely offline.
 *
 * Only Node's standard library is used, so no `npm install` is required.
 *
 * Usage:  node tools/bootstrap.mjs [--force]
 */

import { createWriteStream } from 'node:fs';
import { mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import https from 'node:https';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const LIB = join(ROOT, '.tools', 'lib');
const MANIFEST = join(ROOT, '.tools', 'deps.properties');
const FORCE = process.argv.includes('--force');

const REPOS = [
  'https://repo1.maven.org/maven2',
  'https://repo.maven.apache.org/maven2',
];

const JFX_VERSION = '21.0.5';
const SQLITE_VERSION = '3.46.1.3';

/** JavaFX modules we actually use. Keeps the download small. */
const JFX_MODULES = ['base', 'graphics', 'controls'];

/**
 * Exactly what the application needs at runtime.
 *
 * sqlite-jdbc optionally uses SLF4J for its own diagnostics, but it falls back to
 * a silent no-op logger when SLF4J is absent - which is what we want, so it is
 * deliberately not downloaded.
 */
const ARTIFACTS = [
  ...JFX_MODULES.map((m) => ({
    name: `javafx-${m}`,
    path: `org/openjfx/javafx-${m}/${JFX_VERSION}/javafx-${m}-${JFX_VERSION}-win.jar`,
    file: `javafx-${m}-${JFX_VERSION}-win.jar`,
  })),
  {
    name: 'sqlite-jdbc',
    path: `org/xerial/sqlite-jdbc/${SQLITE_VERSION}/sqlite-jdbc-${SQLITE_VERSION}.jar`,
    file: `sqlite-jdbc-${SQLITE_VERSION}.jar`,
  },
];

function log(msg) {
  process.stdout.write(`${msg}\n`);
}

/** Follows redirects and resolves to the response stream. */
function get(url, redirects = 0, rangeFrom = 0) {
  return new Promise((resolvePromise, rejectPromise) => {
    if (redirects > 6) {
      rejectPromise(new Error(`Too many redirects for ${url}`));
      return;
    }
    const headers = { 'user-agent': 'KinderJournal-bootstrap' };
    if (rangeFrom > 0) headers.range = `bytes=${rangeFrom}-`;
    const req = https.get(url, { headers, timeout: 60000 }, (res) => {
      const { statusCode = 0 } = res;
      const location = res.headers.location;
      if (statusCode >= 300 && statusCode < 400 && location) {
        res.resume();
        resolvePromise(get(new URL(location, url).toString(), redirects + 1, rangeFrom));
        return;
      }
      if (statusCode !== 200 && statusCode !== 206) {
        res.resume();
        rejectPromise(new Error(`HTTP ${statusCode} for ${url}`));
        return;
      }
      resolvePromise(res);
    });
    req.on('timeout', () => req.destroy(new Error(`Timeout downloading ${url}`)));
    req.on('error', rejectPromise);
  });
}

async function existsWithSize(file, minBytes = 4096) {
  try {
    const s = await stat(file);
    return s.isFile() && s.size >= minBytes;
  } catch {
    return false;
  }
}

/** A JAR/ZIP must start with the local file header magic "PK\x03\x04". */
async function looksLikeJar(file) {
  try {
    const buf = await readFile(file);
    return buf.length > 4 && buf[0] === 0x50 && buf[1] === 0x4b;
  } catch {
    return false;
  }
}

async function download(artifact) {
  const dest = join(LIB, artifact.file);
  const tmp = `${dest}.part`;

  if (!FORCE && (await existsWithSize(dest)) && (await looksLikeJar(dest))) {
    log(`  ok      ${artifact.file} (already present)`);
    return dest;
  }

  // Resume a partially downloaded file rather than starting over.
  let have = 0;
  if (!FORCE && (await existsWithSize(tmp, 1))) {
    have = (await stat(tmp)).size;
    log(`  resume  ${artifact.file} at ${mb(have)}`);
  }

  let lastError;
  for (const repo of REPOS) {
    const url = `${repo}/${artifact.path}`;
    try {
      log(`  fetch   ${artifact.file}`);
      const res = await get(url, 0, have);
      if (have > 0 && res.statusCode !== 206) {
        // Server ignored the range request; start again from the beginning.
        have = 0;
      }
      let written = have;
      await new Promise((resolvePromise, rejectPromise) => {
        const out = createWriteStream(tmp, { flags: have > 0 ? 'a' : 'w' });
        let lastReport = 0;
        res.on('data', (chunk) => {
          written += chunk.length;
          const now = Date.now();
          if (now - lastReport > 1500) {
            lastReport = now;
            log(`  ...     ${artifact.file} ${mb(written)}`);
          }
        });
        res.pipe(out);
        out.on('finish', resolvePromise);
        out.on('error', rejectPromise);
        res.on('error', rejectPromise);
      });
      if (!(await looksLikeJar(tmp))) {
        await rm(tmp, { force: true });
        throw new Error('downloaded file is not a valid JAR');
      }
      await rename(tmp, dest);
      log(`  done    ${artifact.file} (${mb(written)})`);
      return dest;
    } catch (err) {
      lastError = err;
      log(`  warn    ${repo.split('/')[2]} failed: ${err.message}`);
      have = (await existsWithSize(tmp, 1)) ? (await stat(tmp)).size : 0;
    }
  }
  throw new Error(`Could not download ${artifact.name}: ${lastError?.message}`);
}

function mb(bytes) {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function sha256(buf) {
  return createHash('sha256').update(buf).digest('hex');
}

async function main() {
  log('KinderJournal dependency bootstrap');
  log(`  target  ${LIB}`);
  await mkdir(LIB, { recursive: true });

  const resolved = [];
  let failures = 0;
  for (const artifact of ARTIFACTS) {
    try {
      const file = await download(artifact);
      const digest = sha256(await readFile(file));
      resolved.push({ ...artifact, digest });
    } catch (err) {
      failures += 1;
      log(`  ERROR   ${artifact.name}: ${err.message}`);
    }
  }

  if (resolved.length !== ARTIFACTS.length) {
    log('');
    log(`FAILED: ${failures} artifact(s) missing.`);
    log('This machine needs internet access once, to fetch the dependencies.');
    log('If you are offline, copy the .tools/lib folder from a machine that has them.');
    process.exitCode = 1;
    return;
  }

  const jars = resolved.map((r) => r.file);
  const jfx = resolved.filter((r) => r.name.startsWith('javafx-')).map((r) => r.file);

  const manifest = [
    '# Generated by tools/bootstrap.mjs - do not edit by hand.',
    `jfx.version=${JFX_VERSION}`,
    `sqlite.version=${SQLITE_VERSION}`,
    `classpath=${jars.join(';')}`,
    `modulepath=${jfx.join(';')}`,
    '',
    ...resolved.map((r) => `sha256.${r.file}=${r.digest}`),
    '',
  ].join('\n');

  await writeFile(MANIFEST, manifest, 'utf8');

  log('');
  log(`Done: ${resolved.length} artifacts in .tools/lib`);
  for (const r of resolved) log(`  ${r.file}  sha256=${r.digest.slice(0, 16)}...`);
  log(`Manifest written to ${MANIFEST}`);
}

main().catch((err) => {
  log(`Bootstrap failed: ${err.message}`);
  process.exitCode = 1;
});
