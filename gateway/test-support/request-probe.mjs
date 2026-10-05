// Isolated HTTP fixture: never sends test prompts to a real provider.
import { Server } from 'node:net';
const listen = Server.prototype.listen;
Server.prototype.listen = function (...args) {
  this.once('listening', () => process.send?.(this.address()));
  return listen.apply(this, args);
};
globalThis.fetch = async (url,opts={}) => {
  if (String(url).endsWith('/api/chat') && process.env.TEST_SLOW_OLLAMA === '1') {
    return new Promise((_resolve,reject)=>{
      if(opts.signal?.aborted) reject(new DOMException('Aborted','AbortError'));
      else opts.signal?.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')),{once:true});
    });
  }
  if (String(url) === 'https://api.openai.com/v1/chat/completions') {
    return new Response('data: ' + JSON.stringify({ choices: [{ delta: { content: 'hello' } }],
      usage: { prompt_tokens: 1000, completion_tokens: 1000 } }) + '\n\ndata: [DONE]\n\n',
      { headers: { 'Content-Type': 'text/event-stream' } });
  }
  throw new Error('Test blocked outbound request');
};
await import('../gateway.mjs');
