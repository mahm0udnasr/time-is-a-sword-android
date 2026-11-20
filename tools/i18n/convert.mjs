// One-off converter (plan step 5.2): the extension's shared/i18n.js → Android
// string resources. Keys become x_<key with . → _>; {param} placeholders are
// kept as-is and filled by the app's named-argument formatter (ui/format/Text.kt).
//
//   node tools/i18n/convert.mjs ../social-lock-ext/shared/i18n.js app/src/main/res
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
const [src, resDir] = process.argv.slice(2);
const code = readFileSync(src, 'utf8');
const body = code.slice(code.indexOf('const STRINGS = '), code.indexOf('\n};', code.indexOf('const STRINGS = ')) + 3);
const STRINGS = new Function(body.replace('const STRINGS = ', 'return ') )();
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/@/g, '\\@').replace(/^\?/, '\\?');
const name = (k) => 'x_' + k.replace(/[^A-Za-z0-9]/g, '_');
for (const [lang, dir] of [['en', 'values'], ['ar', 'values-ar']]) {
  const keys = Object.keys(STRINGS.en);
  const lines = keys.map((k) => `    <string name="${name(k)}" formatted="false">${esc(STRINGS[lang][k] ?? STRINGS.en[k])}</string>`);
  mkdirSync(`${resDir}/${dir}`, { recursive: true });
  writeFileSync(`${resDir}/${dir}/strings_ext.xml`,
    `<?xml version="1.0" encoding="utf-8"?>\n<!-- GENERATED from the extension's shared/i18n.js by tools/i18n/convert.mjs. Do not edit by hand. -->\n<resources>\n${lines.join('\n')}\n</resources>\n`);
  console.log(dir, keys.length, 'strings');
}
const missing = Object.keys(STRINGS.en).filter((k) => !(k in STRINGS.ar));
if (missing.length) console.log('keys missing in ar (English used):', missing.join(', '));
