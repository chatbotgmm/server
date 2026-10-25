#!/usr/bin/env node
// Regenerates editable reference mockups only; never writes Android app source.
const fs=require('fs'),path=require('path'),{spawnSync}=require('child_process');
const root=path.resolve(__dirname,'..'),base=path.join(root,'editable/workspace/outputs/ui-mockups-20261025');
function run(file,args=[]){const p=spawnSync(process.execPath,[path.join(base,file),...args],{stdio:'inherit'});if(p.status!==0)process.exit(p.status||1)}
(async()=>{
 run('party-v3/render-party-v3.cjs');run('recruit-v2/render.cjs');
 const B=require(path.join(base,'battle-v3/battle-base.cjs'));await B.init();fs.mkdirSync(path.join(base,'battle-v2'),{recursive:true});fs.writeFileSync(path.join(base,'battle-v2/S06_전투_B_항구전장_v2.png'),B.scene('outdoor','B').toBuffer('image/png'));
 run('battle-v3/build-battle-v3.cjs');run('v3/render-canvas.cjs');
 run('v3/render-canvas.cjs',['S15_insufficient','S15_received','S20_progress','S20_ready','S20_received','S22_surrender','S22_return','S22_wipe']);
 const page=JSON.parse(fs.readFileSync(path.join(root,'reference/page-manifest.json'))).pages;
 fs.mkdirSync(path.join(base,'v3/states'),{recursive:true});for(const p of page)if(p.file.startsWith('v3/states/'))fs.copyFileSync(path.join(base,'battle-v3',path.basename(p.file)),path.join(base,p.file));
 console.log('50 current reference states regenerated. These PNGs are review output, not runtime screen assets.');
})().catch(e=>{console.error(e);process.exit(1)});
