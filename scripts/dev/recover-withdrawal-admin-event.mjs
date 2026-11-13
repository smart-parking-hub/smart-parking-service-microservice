// Local maintenance only. Dry-run by default; never creates/approves a withdrawal or changes a wallet.
import {execFileSync} from 'node:child_process';
const args=process.argv.slice(2);
const option=(key,fallback)=>{const i=args.indexOf(key);return i<0?fallback:args[i+1]};
const id=Number(option('--id','28'));
const schema=option('--schema','smart_parking_payment_p0_20261106');
const container=option('--mysql','smart-parking-mysql');
if(!Number.isSafeInteger(id)||id<1||!/^[a-zA-Z0-9_]+$/.test(schema)||!/^[a-zA-Z0-9_-]+$/.test(container))throw new Error('Invalid maintenance arguments');
const sql=query=>execFileSync('docker',['exec','-i',container,'sh','-c',
 `MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names --raw --database=${schema}`],{input:query,encoding:'utf8'}).trim();
const raw=sql(`SELECT JSON_OBJECT('id',id,'merchantId',merchant_id,'amount',amount,'status',status,'requestedAt',DATE_FORMAT(requested_at,'%Y-%m-%dT%H:%i:%s')) FROM withdrawal_requests WHERE id=${id};`);
if(!raw)throw new Error('Withdrawal not found');
const request=JSON.parse(raw);
if(request.status!=='PENDING')throw new Error('Withdrawal is no longer pending; no waiting-for-review notification will be created');
const eventId=`legacy-withdrawal-requested:${id}`;
const existing=Number(sql(`SELECT COUNT(*) FROM admin_event_outbox WHERE topic_name='admin-operations.events' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventType'))='WITHDRAWAL_REQUESTED' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.targetId'))='${id}';`));
if(existing){console.log(JSON.stringify({id,action:'no-op',existingEvents:existing}));process.exit(0)}
console.log(JSON.stringify({id,merchantId:request.merchantId,amount:request.amount,status:request.status,apply:args.includes('--apply')}));
if(!args.includes('--apply'))process.exit(0);
// Local application dates are Vietnam wall time. Preserve the original instant explicitly.
const timestamp=new Date(request.requestedAt+'+07:00').toISOString();
const payload=JSON.stringify({eventId,eventType:'WITHDRAWAL_REQUESTED',timestamp,schemaVersion:1,
 aggregateId:String(id),targetId:id,actorId:request.merchantId,amount:request.amount,currency:'VND'});
const encoded=Buffer.from(payload).toString('hex');
const result=sql(`START TRANSACTION;
 SELECT id FROM withdrawal_requests WHERE id=${id} AND status='PENDING' FOR UPDATE;
 INSERT INTO admin_event_outbox(event_id,topic_name,partition_key,payload,status,attempts,created_at,next_attempt_at)
 SELECT '${eventId}','admin-operations.events','withdrawal:${id}',CONVERT(0x${encoded} USING utf8mb4),'PENDING',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
 FROM withdrawal_requests w WHERE w.id=${id} AND w.status='PENDING' AND w.merchant_id=${Number(request.merchantId)} AND w.amount=${Number(request.amount)}
 AND NOT EXISTS(SELECT 1 FROM admin_event_outbox o WHERE o.event_id='${eventId}' OR
 (o.topic_name='admin-operations.events' AND JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.eventType'))='WITHDRAWAL_REQUESTED'
 AND JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.targetId'))='${id}'));
 SELECT ROW_COUNT(); COMMIT;`);
console.log(JSON.stringify({id,eventId,inserted:Number(result.split('\n').at(-1))}));
