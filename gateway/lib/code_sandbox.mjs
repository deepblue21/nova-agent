import { runComputeJob } from './compute_jobs.mjs';

export function codeToolEnabled() {
  return /^(1|true|yes|on)$/i.test(process.env.CODE_TOOL_ENABLED || '');
}

export async function runJavaScriptSandbox(args = {}) {
  if (JSON.stringify(args).length > 100000) throw new Error('sandbox input too large');
  return runComputeJob('code', args, 7500);
}
