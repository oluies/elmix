// Bootar DuckDB-WASM och exponerar window.ObalansDB för Scala.js-bygget, som är
// en klassisk (icke-modul) script och därför inte kan importera själv.
//
// Motorn är 34 MB okomprimerad och hämtas från jsDelivr FÖRST när läsaren ber om
// den, inte vid sidladdning. De statiska diagrammen ovanför fungerar utan den.
// Workern måste gå via en blob: en Worker får inte laddas cross-origin direkt.
const DIST = 'https://cdn.jsdelivr.net/npm/@duckdb/duckdb-wasm@1.29.0/dist/'
// dist/duckdb-browser.mjs importerar "apache-arrow" som bar specifier, vilket en
// webbläsare inte kan slå upp utan bundlare eller import map. jsDelivrs +esm-bygge
// har beroendena upplösta, så det är det vi importerar. Wasm och worker pekas
// fortfarande ut explicit mot dist/.
const ESM = 'https://cdn.jsdelivr.net/npm/@duckdb/duckdb-wasm@1.29.0/+esm'
const PARQUET = '../data/imbalance.parquet'

let conn = null
let booting = null

async function boot(onStatus) {
  const say = (s) => { try { onStatus && onStatus(s) } catch (e) {} }
  say('hämtar DuckDB-WASM')
  const duckdb = await import(/* @vite-ignore */ ESM)
  const bundle = await duckdb.selectBundle({
    mvp: { mainModule: DIST + 'duckdb-mvp.wasm', mainWorker: DIST + 'duckdb-browser-mvp.worker.js' },
    eh: { mainModule: DIST + 'duckdb-eh.wasm', mainWorker: DIST + 'duckdb-browser-eh.worker.js' }
  })
  const shim = URL.createObjectURL(
    new Blob([`importScripts("${bundle.mainWorker}");`], { type: 'text/javascript' }))
  const worker = new Worker(shim)
  const db = new duckdb.AsyncDuckDB(new duckdb.VoidLogger(), worker)
  say('startar databasen')
  await db.instantiate(bundle.mainModule, bundle.pthreadWorker)
  URL.revokeObjectURL(shim)
  say('hämtar 2,5 MB parquet')
  const res = await fetch(PARQUET)
  if (!res.ok) throw new Error('parquet ' + res.status)
  await db.registerFileBuffer('imbalance.parquet', new Uint8Array(await res.arrayBuffer()))
  conn = await db.connect()
  await conn.query(
    "CREATE TABLE imb AS SELECT zone, ts, diff FROM read_parquet('imbalance.parquet')")
  say('klar')
}

// Arrow ger BigInt för heltal; Scala.js vill ha Double. Konverteras här.
function plain(table) {
  return table.toArray().map((row) => {
    const o = row.toJSON(), out = {}
    for (const k in o) {
      const v = o[k]
      out[k] = typeof v === 'bigint' ? Number(v)
        : (v && typeof v === 'object' && typeof v.valueOf === 'function') ? Number(v.valueOf())
          : v
    }
    return out
  })
}

window.ObalansDB = {
  ready(onStatus) {
    if (!booting) booting = boot(onStatus).catch((e) => { booting = null; throw e })
    return booting
  },
  query(sql) {
    if (!conn) return Promise.reject(new Error('DuckDB är inte startad'))
    return conn.query(sql).then(plain)
  }
}
