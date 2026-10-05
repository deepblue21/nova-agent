import { rateLimit } from './cache.mjs';

export const API_KEY_SCOPES = ['read', 'write', 'chat', 'voice', 'mobile', 'tools', 'admin'];
export function scopeAllows(principal, method, path) {
  if (principal?.via !== 'api_key') return true;
  const scopes = principal.scopes;
  if (!Array.isArray(scopes) || scopes.some(s => !API_KEY_SCOPES.includes(s))) return false;
  if (!scopes.length) return true; // existing full-access keys
  let required;
  if (path.startsWith('/v1/admin/')) required = 'admin';
  else if (path === '/v1/chat/completions' || path === '/v1/eval') required = 'chat';
  else if (path === '/stt' || path === '/tts' || path.startsWith('/v1/voice/')) required = 'voice';
  else if (path.startsWith('/v1/mobile/')) required = 'mobile';
  else if (path.startsWith('/v1/mcp/')) required = 'tools';
  else if (/^\/v1\/(models|conversations|knowledge|memory|media|scheduled|workspaces|workspace-invitations|usage|agent\/runs)(\/|$)/.test(path))
    required = ['GET','HEAD'].includes(method) ? 'read' : 'write';
  return !!required && (scopes.includes(required) || (path === '/v1/models' && scopes.includes('chat')));
}

export function userAdmission({ max, windowMs, limit = rateLimit }) {
  return async (req,res,next) => {
    if (!req.principal) return next();
    if (!scopeAllows(req.principal,req.method,req.path)) return res.status(403).json({error:'API key scope denied'});
    try {
      const r = await limit(req.principal.userId,max,windowMs);
      if (!r.allowed) {
        res.setHeader('Retry-After',String(Math.max(1,Math.ceil(r.retryAfterMs/1000))));
        return res.status(429).json({error:'rate limit exceeded'});
      }
      next();
    } catch (e) {
      req.log?.error?.({err:e.message},'rate limit unavailable');
      res.status(503).json({error:'rate-limit unavailable'});
    }
  };
}
