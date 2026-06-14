// Pure helpers for the live website preview (kept out of the component so they
// are unit-testable with `node --test`, no DOM/Vite needed).

// Detect a full HTML document inside an assistant reply (a fenced ```html block
// containing <!doctype html> or an <html> tag). Returns the HTML or null.
export function extractWebsite(text) {
  const m = /```(?:html|htm)\s*([\s\S]*?)```/i.exec(text || "");
  const code = (m ? m[1] : "").trim();
  return /<!doctype html|<html[\s>]/i.test(code) ? code : null;
}
