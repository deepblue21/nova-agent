// Yedek üretim derleyicisi (esbuild).
//
// Normal yol `npm run build` (Vite). Bu betik yalnız Vite'ın çalışamadığı
// ortamlar için var — ör. node_modules Windows'ta kurulduğu için rolldown'ın
// native binary'si Linux'ta yok. Çıktı tek dosya: dist/assets/main.js.
//
// Kullanım:  node scripts/build-esbuild.mjs [--esbuild <esbuild modül yolu>]
//
// NOT: dist bir Docker bind-mount olabilir. Dizini SİLME — yalnızca içeriğini
// temizle; silinirse container ölü inode'u tutar ve eski UI servis edilir.
import { existsSync, mkdirSync, readdirSync, rmSync, cpSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");
const dist = resolve(root, "dist");
const pub = resolve(root, "public");

const argv = process.argv.slice(2);
const esbuildPath = argv.includes("--esbuild")
  ? argv[argv.indexOf("--esbuild") + 1]
  : "esbuild";
const { build } = await import(esbuildPath);

// dist içeriğini temizle (dizinin kendisini değil)
mkdirSync(dist, { recursive: true });
for (const f of readdirSync(dist)) rmSync(resolve(dist, f), { recursive: true, force: true });

// public/* → dist/
if (existsSync(pub)) cpSync(pub, dist, { recursive: true });

const result = await build({
  entryPoints: [resolve(root, "src/main.jsx")],
  bundle: true,
  format: "esm",
  splitting: false,          // tek dosya: yavaş bind-mount'ta çok daha hızlı
  outfile: resolve(dist, "assets/main.js"),
  jsx: "automatic",
  loader: { ".js": "jsx", ".jsx": "jsx", ".svg": "dataurl", ".png": "dataurl" },
  define: { "process.env.NODE_ENV": '"production"' },
  target: ["es2020"],
  metafile: true,
  logLevel: "info",
});

// index.html: kaynak şablondan al, script etiketini üretilen dosyaya bağla
const tpl = readFileSync(resolve(root, "index.html"), "utf8");
const html = tpl.replace(
  /<script[^>]*src="[^"]*main\.jsx?"[^>]*><\/script>/,
  '<script type="module" crossorigin src="/assets/main.js"></script>',
);
writeFileSync(resolve(dist, "index.html"), html);

const bytes = Object.values(result.metafile.outputs).reduce((n, o) => n + o.bytes, 0);
console.log("dist hazır ·", (bytes / 1e6).toFixed(2), "MB");
