import type {CatalogTier} from './augment-catalog-v2.js';
// TFT의 시퀀스 방식에서 착안한 자체 6회 프로필이며 Riot의 원본 확률표가 아니다.
export interface AugmentSequenceProfile {id:string;prismaticCountWeights:number[];goldBias:number}
export const balancedSixProfile:AugmentSequenceProfile=Object.freeze({id:'balanced-six-20261021',prismaticCountWeights:Object.freeze([3500,4500,1700,250,40,9,1]) as unknown as number[],goldBias:0.6});
export function parseSequenceProfile(input:AugmentSequenceProfile):AugmentSequenceProfile{
 if(!input||Object.keys(input).some(k=>!['id','prismaticCountWeights','goldBias'].includes(k))||typeof input.id!=='string'||!input.id||!Array.isArray(input.prismaticCountWeights)||input.prismaticCountWeights.length!==7||input.prismaticCountWeights.some(n=>!Number.isFinite(n)||n<0)||!Number.isFinite(input.prismaticCountWeights.reduce((a,b)=>a+b,0))||input.prismaticCountWeights.reduce((a,b)=>a+b,0)<=0||!Number.isFinite(input.goldBias)||input.goldBias<0||input.goldBias>1)throw new Error('증강 순서 프로필 오류');
 return {id:input.id,prismaticCountWeights:[...input.prismaticCountWeights],goldBias:input.goldBias};
}
export function createAugmentSequence(profile:AugmentSequenceProfile,rng:()=>number):CatalogTier[]{
 const p=parseSequenceProfile(profile),draw=()=>{const n=rng();if(!Number.isFinite(n)||n<0||n>=1)throw new Error('난수 오류');return n;};
 let cursor=draw()*p.prismaticCountWeights.reduce((a,b)=>a+b,0),prisms=6;
 for(let i=0;i<7;i++){cursor-=p.prismaticCountWeights[i];if(cursor<0){prisms=i;break;}}
 const remainder=6-prisms;
 // P0: G3/S3 또는 G4/S2; 나머지는 홀수 칸 하나만 G 쪽에 약간 기울인다.
 const gold=prisms===0?3+(draw()<p.goldBias?1:0):Math.floor(remainder/2)+(remainder%2&&draw()<p.goldBias?1:0);
 const sequence:CatalogTier[]=[...Array(prisms).fill('prismatic'),...Array(gold).fill('gold'),...Array(remainder-gold).fill('silver')];
 for(let i=sequence.length-1;i>0;i--){const j=Math.floor(draw()*(i+1));[sequence[i],sequence[j]]=[sequence[j],sequence[i]];}
 return sequence;
}
