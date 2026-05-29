const fs = require('fs');
const path = require('path');

const pomPath = path.resolve(__dirname, '../../pom.xml');
const pkgPath = path.resolve(__dirname, '../package.json');

const pomContent = fs.readFileSync(pomPath, 'utf-8');
const match = pomContent.match(/<revision>([^<]+)<\/revision>/);
if (!match) {
  console.error('sync-version: <revision> not found in root pom.xml');
  process.exit(1);
}

const version = match[1];
const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
if (pkg.version === version) {
  console.log(`sync-version: package.json already at ${version}`);
  process.exit(0);
}

pkg.version = version;
fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + '\n');
console.log(`sync-version: package.json ${pkg.version} -> ${version}`);
