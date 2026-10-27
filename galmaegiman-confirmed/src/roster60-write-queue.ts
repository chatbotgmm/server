import type {PrismaClient} from '@prisma/client';

/** Share one JS write lane across roster services using the same Prisma client.
 * This is process-local ordering, not the database's cross-client write lock.
 */
export const roster60WriteTails=new WeakMap<PrismaClient,Promise<void>>();

/** Wrap the whole transaction/retry boundary, before opening a transaction.
 * The callback must not recursively enter this queue with the same client.
 * Failures release the lane so subsequent requests are not stranded.
 */
export async function serializeRoster60Write<T>(client:PrismaClient,work:()=>Promise<T>):Promise<T>{
 const previous=roster60WriteTails.get(client)??Promise.resolve();
 let release!:()=>void;const tail=new Promise<void>(resolve=>{release=resolve;});
 roster60WriteTails.set(client,tail);await previous;
 try{return await work();}
 finally{release();if(roster60WriteTails.get(client)===tail)roster60WriteTails.delete(client);}
}
