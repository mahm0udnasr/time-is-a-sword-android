// Parity fixtures (plan step 1.11): run the Chrome extension's own pure logic
// over many seeded random scenarios and store inputs + outputs, so the Kotlin
// port can be checked against the real JavaScript.
//
//   TZ=Africa/Cairo node tools/parity/gen-parity.mjs /path/to/social-lock-ext \
//     > core/src/test/resources/parity.json
//
// Sites use id = domain, so the extension's domain-keyed usage and pauses are
// the same maps as the Android target-id-keyed ones.
const ext = (process.argv[2] || '../social-lock-ext').replace(/\/$/, '');
globalThis.chrome = { runtime: { getURL: (p) => 'chrome-extension://x/' + p } };
const R = await import(ext + '/background/rules.js');
const L = await import(ext + '/background/loosen.js');
const P = await import(ext + '/background/prayer.js');
const i18n = await import(ext + '/shared/i18n.js');
i18n.setLanguage('en');

if (Intl.DateTimeFormat().resolvedOptions().timeZone !== 'Africa/Cairo') {
  console.error('Run with TZ=Africa/Cairo');
  process.exit(1);
}

// mulberry32: small deterministic PRNG
let seed = 20261119;
const rnd = () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[Math.floor(rnd() * xs.length)];
const pad = (n) => String(n).padStart(2, '0');
const hm = () => `${pad(int(0, 23))}:${pick(['00', '15', '30', '45'])}`;
const MODES = ['block', 'limit', 'schedule', 'limit+schedule'];

function randDays() {
  const all = [0, 1, 2, 3, 4, 5, 6];
  const r = rnd();
  if (r < 0.5) return all;
  return all.filter(() => rnd() < 0.5);
}
function randRule(days = randDays()) {
  const mode = pick(MODES);
  const windows = mode.includes('schedule') ? Array.from({ length: int(0, 2) }, () => ({ start: hm(), end: hm() })) : [];
  return { days, mode, limitMinutes: pick([0, 1, 5, 10, 20, 30, 45, 60]), windows };
}
function randRules() {
  const r = rnd();
  if (r < 0.15) return [];
  if (r < 0.65) return [randRule()];
  const a = [0, 1, 2, 3, 4, 5, 6].filter(() => rnd() < 0.5);
  const b = [0, 1, 2, 3, 4, 5, 6].filter((d) => !a.includes(d));
  return [randRule(a), randRule(b)];
}
const DOMAINS = ['a.com', 'b.com', 'c.org', 'd.net'];
function randWorld() {
  const groups = rnd() < 0.5 ? [{ id: 'g1', name: 'G', enabled: rnd() < 0.85, rules: randRules() }] : [];
  const sites = DOMAINS.map((d) => ({
    id: d, domain: d, enabled: rnd() < 0.85, groupId: groups.length && rnd() < 0.5 ? 'g1' : null, rules: randRules()
  }));
  return { sites, groups };
}
function randNow() {
  // Any minute of 2026-11-15 (Sun) … 2026-11-21 (Sat), plus some around midnight.
  const d = int(15, 21);
  const t = rnd() < 0.2 ? pick([[23, 59], [0, 0], [0, 1], [3, 59], [4, 0]]) : [int(0, 23), int(0, 59)];
  return new Date(2026, 10, d, t[0], t[1], int(0, 59)).getTime();
}
function randUsage(now, resetHour) {
  const key = R.dayKey(now, resetHour);
  const seconds = {};
  for (const d of DOMAINS) if (rnd() < 0.7) seconds[d] = pick([0, 30, 299, 300, 599.5, 1199, 1200, 1800, 2700, 3600]);
  return { [key]: { seconds } };
}

// ---- evaluate
const evaluate = [];
for (let i = 0; i < 400; i++) {
  const { sites, groups } = randWorld();
  const now = randNow();
  const resetHour = rnd() < 0.8 ? 0 : 4;
  const usage = randUsage(now, resetHour);
  const pauses = rnd() < 0.1 ? { [pick(DOMAINS)]: { until: now + pick([-1000, 60000, 300000]) } } : {};
  const site = pick(sites);
  const v = R.evaluate(site, { now, settings: { resetHour }, usage, sites, groups, pauses });
  evaluate.push({
    input: { now, resetHour, sites, groups, usage, pauses, siteId: site.id },
    out: v.blocked
      ? { blocked: true, reason: v.reason, until: v.until ?? null, group: v.group?.name ?? null }
      : { blocked: false, remainingSec: v.remainingSec ?? null, blockAt: v.blockAt == null ? null : Math.round(v.blockAt), blockKind: v.blockKind ?? null, paused: v.paused }
  });
}

// ---- diffState: mutate a world and record each op's kind/op/id/number of reasons
function mutate(w) {
  const n = structuredClone(w);
  const k = int(1, 3);
  for (let i = 0; i < k; i++) {
    const r = rnd();
    const s = pick(n.sites);
    if (r < 0.15) s.enabled = !s.enabled;
    else if (r < 0.25) s.groupId = s.groupId ? null : (n.groups[0]?.id ?? null);
    else if (r < 0.6) s.rules = rnd() < 0.5 ? randRules() : s.rules.map((x) => ({ ...x, limitMinutes: pick([5, 10, 20, 30, 60]) }));
    else if (r < 0.7) n.sites = n.sites.filter((x) => x !== s);
    else if (r < 0.8 && n.groups.length) n.groups[0] = { ...n.groups[0], enabled: !n.groups[0].enabled };
    else if (r < 0.9 && n.groups.length) n.groups[0] = { ...n.groups[0], rules: randRules() };
    else n.sites.push({ id: 'new.com', domain: 'new.com', enabled: true, groupId: null, rules: randRules() });
  }
  return n;
}
const diff = [];
for (let i = 0; i < 200; i++) {
  const cur = randWorld();
  const next = mutate(cur);
  const ops = L.diffState(cur, next);
  diff.push({ cur, next, ops: ops.map((o) => ({ kind: o.kind, op: o.op, id: o.id, reasons: o.reasons.length })) });
}

// ---- rulesLoosening: day groups per reason (English text → compared as "days: why" pairs)
const loosen = [];
for (let i = 0; i < 200; i++) {
  const prev = randRules();
  const next = rnd() < 0.5 ? randRules() : prev.map((x) => ({ ...x, limitMinutes: pick([5, 20, 60]), windows: x.windows.map((w) => ({ start: w.start, end: hm() })) }));
  loosen.push({ prev, next, reasons: L.rulesLoosening(prev, next) });
}

// ---- lock windows from fixed prayer times
const lock = [];
const timings = { fajr: '04:53', dhuhr: '11:40', asr: '14:38', maghrib: '16:57', isha: '18:18' };
for (let i = 0; i < 50; i++) {
  const raw = {
    enabled: rnd() < 0.9, address: 'Cairo', method: 5, before: int(0, 60), after: int(0, 120),
    prayers: Object.fromEntries(P.PRAYERS.map((p) => [p, { on: rnd() < 0.85, adjust: int(-60, 60) }]))
  };
  const p = P.normalizePrayer(raw);
  const key = P.cfgKey(p);
  const cache = { days: {
    '2026-11-19': { y: 2026, m: 11, d: 19, cfg: key, timings, timezone: 'Africa/Cairo' },
    '2026-11-20': { y: 2026, m: 11, d: 20, cfg: key, timings, timezone: 'Africa/Cairo' }
  } };
  lock.push({ prayer: raw, windows: P.lockWindows(p, cache).map((w) => [w.prayer, w.start, w.end]) });
}

process.stdout.write(JSON.stringify({ evaluate, diff, loosen, lock, timings }));
