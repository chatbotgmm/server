// Local Node-only test runner: production imports retain .js for tsc output.
export async function resolve(specifier,context,next){
  try{return await next(specifier,context);}catch(e){
    if(e.code==='ERR_MODULE_NOT_FOUND'&&specifier.startsWith('.')&&specifier.endsWith('.js'))return next(specifier.slice(0,-3)+'.ts',context);
    throw e;
  }
}
