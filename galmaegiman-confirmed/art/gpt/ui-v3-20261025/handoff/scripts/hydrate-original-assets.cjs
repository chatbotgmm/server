#!/usr/bin/env node
// Optional reference-render preparation, not app asset installation.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.resolve(__dirname,'..'),assetRoot=process.argv[2]&&path.resolve(process.argv[2]);
if(!assetRoot||!fs.statSync(assetRoot).isDirectory()){console.error('Usage: node scripts/hydrate-original-assets.cjs <extracted-original-asset-pack>');process.exit(2)}
const manifest=JSON.parse(fs.readFileSync(path.join(root,'assets/asset-references.json'))),need=new Set(manifest.assets.map(x=>x.sha256)),found=new Map();
function walk(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isSymbolicLink())continue;if(e.isDirectory())walk(p);else if(/\.(png|ttf)$/i.test(e.name)){const hash=crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');if(need.has(hash)&&!found.has(hash))found.set(hash,p)}}}
walk(assetRoot);const missing=manifest.assets.filter(x=>!found.has(x.sha256));if(missing.length){console.error(JSON.stringify({error:'Missing unchanged original assets; no substitute generated',missing:missing.map(x=>({sha256:x.sha256,aliases:x.aliases}))},null,2));process.exit(1)}
const destination=path.join(root,'editable/workspace');let copies=0;
for(const a of manifest.assets)for(const relative of a.hydrate_targets){const target=path.resolve(destination,relative);if(!target.startsWith(destination+path.sep))throw Error('Unsafe destination');fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(found.get(a.sha256),target);copies++;}
for(const dir of ['outputs/ui-mockups-20261025/v2/assets','outputs/ui-mockups-20261025/v3/assets','outputs/ui-mockups-20261025/battle-v2','outputs/ui-mockups-20261025/v3/states'])fs.mkdirSync(path.join(destination,dir),{recursive:true});
console.log(JSON.stringify({unique_assets_verified:found.size,paths_prepared:copies,destination,production_files_changed:false}));
