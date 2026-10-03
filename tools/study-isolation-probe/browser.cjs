const {chromium}=require('../navigation-browser/node_modules/playwright');
const fs=require('fs');
(async()=>{const browser=await chromium.launch({headless:true});try{
 const results=[];
 for(const path of ['plain','headers','sw']){
  const context=await browser.newContext();const page=await context.newPage();await page.goto('http://127.0.0.1:4178/'+path+'/');
  await page.waitForFunction(()=>window.probeResult,null,{timeout:20000});results.push(await page.evaluate(()=>window.probeResult));await context.close();
 }
 const output=process.argv[2] || 'build/study-isolation/chromium.json';
 fs.mkdirSync(require('path').dirname(output),{recursive:true});
 fs.writeFileSync(output,JSON.stringify(results,null,2));console.log(results.map(r=>({url:r.url,isolated:r.isolated,sab:r.sab,controlled:r.controlled})));
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
