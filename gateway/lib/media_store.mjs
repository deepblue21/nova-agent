import {q,withTx} from './db.mjs';

export function createMediaStore({query=q,transaction=withTx}={}) {
  return {
    async reserve(userId,key,bytes,{quotaBytes=Number(process.env.MEDIA_QUOTA_BYTES || 104857600),retentionDays=Number(process.env.MEDIA_RETENTION_DAYS || 7)}={}) {
      if (!Number.isSafeInteger(quotaBytes) || quotaBytes<1 || !Number.isSafeInteger(retentionDays) || retentionDays<1 || retentionDays>365)
        throw Object.assign(new Error('media limits misconfigured'),{status:503});
      await transaction(async c=>{
        await c.query('SELECT pg_advisory_xact_lock(hashtextextended($1,68210413))',[userId]);
        const {rows}=await c.query('SELECT COALESCE(sum(bytes),0) AS total FROM media_objects WHERE user_id=$1',[userId]);
        if (Number(rows[0].total)+bytes>quotaBytes) throw Object.assign(new Error('media storage quota exceeded'),{status:413});
        await c.query("INSERT INTO media_objects(object_key,user_id,bytes,expires_at) VALUES($1,$2,$3,now()+$4*interval '1 day')",[key,userId,bytes,retentionDays]);
      });
    },
    async get(userId,key) {return (await query('SELECT object_key FROM media_objects WHERE user_id=$1 AND object_key=$2',[userId,key])).rows[0];},
    async expired() {return (await query('SELECT object_key FROM media_objects WHERE expires_at<=now() ORDER BY expires_at LIMIT 100')).rows;},
    async remove(key) {await query('DELETE FROM media_objects WHERE object_key=$1',[key]);},
  };
}
export const mediaStore=createMediaStore();
