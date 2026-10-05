import { Worker } from 'node:worker_threads';

let active = 0;
const error = (message, status) => Object.assign(new Error(message), {status});

export async function runComputeJob(kind, payload, timeoutMs = 10000) {
  if (!['document', 'code'].includes(kind)) throw error('unknown compute job', 400);
  if (active >= 2) throw error('İşleme kapasitesi dolu; tekrar deneyin.', 503);
  active++;
  let worker, timer;
  try {
    worker = new Worker(new URL('./compute_worker.mjs', import.meta.url), {
      workerData: {kind, payload},
      resourceLimits: {maxOldGenerationSizeMb: 96, maxYoungGenerationSizeMb: 16, stackSizeMb: 4},
    });
    return await new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(error('İşleme süresi aşıldı.', 504)), timeoutMs);
      worker.once('message', result => result.ok ? resolve(result.value) : reject(error(result.error, result.status || 422)));
      worker.once('error', () => reject(error('İşleme sınırı aşıldı veya işlem başarısız oldu.', 422)));
      worker.once('exit', code => reject(error('İşleme tamamlanamadı (' + code + ').', 422)));
    });
  } finally {
    clearTimeout(timer);
    await worker?.terminate();
    active--;
  }
}
