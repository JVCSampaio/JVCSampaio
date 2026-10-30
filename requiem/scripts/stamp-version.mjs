// Writes the release version into the workspace Cargo.toml and tauri.conf.json before CI builds
// the installers, so the app, its installers and the update check all know which one they are.
// Usage: node scripts/stamp-version.mjs 0.1.42
import { readFileSync, writeFileSync } from "node:fs";

const version = process.argv[2];
if (!/^\d+\.\d+\.\d+$/.test(version ?? "")) {
  console.error("usage: node scripts/stamp-version.mjs X.Y.Z");
  process.exit(1);
}

const cargoUrl = new URL("../Cargo.toml", import.meta.url);
const cargo = readFileSync(cargoUrl, "utf8");
const stamped = cargo.replace(/(\[workspace\.package\][^[]*?\nversion\s*=\s*)"[^"]*"/, `$1"${version}"`);
if (stamped === cargo && !cargo.includes(`version = "${version}"`)) {
  console.error("no version in [workspace.package] of Cargo.toml");
  process.exit(1);
}
writeFileSync(cargoUrl, stamped);

const confUrl = new URL("../src-tauri/tauri.conf.json", import.meta.url);
const conf = JSON.parse(readFileSync(confUrl, "utf8"));
conf.version = version;
writeFileSync(confUrl, `${JSON.stringify(conf, null, 2)}\n`);

console.log(`Requiem ${version}`);
