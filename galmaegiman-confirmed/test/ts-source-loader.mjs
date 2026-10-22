// Scratch-only Node24 test loader; no dependencies or transpiled build needed.
export async function resolve(specifier, context, next){
  try{return await next(specifier,context);}catch(error){
    if(specifier.startsWith('.')&&specifier.endsWith('.js'))return next(specifier.slice(0,-3)+'.ts',context);
    throw error;
  }
}
