const fs = require('fs'), vm = require('vm'), assert = require('assert');
const path = require('path');
const html = fs.readFileSync(process.argv[2] || path.join(__dirname, '../app/androidApp/build/navigation-diagram/index.html'), 'utf8');
const created = [];
function element(tag) { const classes = new Set(); const e = {tag, attrs:{}, children:[], hidden:false, style:{},listeners:{},scrollLeft:0,scrollTop:0,clientLeft:0,clientTop:0,clientWidth:800,clientHeight:600,addEventListener(k,f){this.listeners[k]=f},setPointerCapture(){},getBoundingClientRect(){return {left:0,top:0}},classList:{add(k){classes.add(k)},toggle(k,on){on?classes.add(k):classes.delete(k)}}, setAttribute(k,v){this.attrs[k]=String(v)}, appendChild(c){this.children.push(c)}}; created.push(e); return e; }
const ids = Object.fromEntries(['fit','returns','viewport','graph','transitions','reset','zoom','scale'].map(id=>[id,element(id)]));
ids.returns.checked=false;
const document = { getElementById(id){return ids[id]}, createElementNS(ns,tag){return element(tag)}, createElement:element };
vm.runInNewContext(html.match(/<script>([\s\S]*?)<\/script>/)[1], {document, Math, Map, ResizeObserver:class {observe(){}}});
const dimensions=ids.graph.attrs.viewBox.split(' ').map(Number);
const width=dimensions[2];
const paths = created.filter(e=>e.attrs['data-transition'] !== undefined);
assert(paths.length > 40);
assert(paths.filter(e=>e.attrs.d).every(e=>!e.attrs.d.match(/NaN|Infinity/)));
assert(paths.every(e=>e.attrs['marker-end']==='url(#arrow)'));
ids.zoom.oninput({target:{value:'50'}}); assert.equal(ids.graph.attrs.width,String(width/2));
ids.zoom.oninput({target:{value:'200'}}); assert.equal(ids.graph.attrs.width,String(width*2));
const node = created.find(e=>e.attrs['aria-label']==='HOME'); node.onclick();
assert(ids.transitions.children.some(e=>e.hidden));
ids.reset.onclick(); assert(ids.transitions.children.some(e=>!e.hidden));
assert.equal((html.match(/data:font\/ttf;base64/g)||[]).length,3);
console.log(`${paths.length} arrows: finite coordinates, zoom 50/200%, selection/reset, 3 embedded fonts passed`);

const v=ids.viewport;
function pointer(id,x,y){return {pointerId:id,clientX:x,clientY:y,button:0,target:v};}
v.scrollLeft=300;v.scrollTop=300;
v.listeners.pointerdown(pointer(1,100,100));v.listeners.pointermove(pointer(1,150,120));
assert.equal(v.scrollLeft,250);assert.equal(v.scrollTop,280);
v.listeners.pointerup(pointer(1,150,120));
let prevented=false;v.listeners.click({detail:1,preventDefault(){prevented=true},stopPropagation(){}});assert(prevented);
ids.zoom.oninput({target:{value:'100'}});
v.scrollLeft=300;v.scrollTop=300;
v.listeners.pointerdown(pointer(1,100,100));v.listeners.pointerdown(pointer(2,200,100));
v.listeners.pointermove(pointer(2,300,100));
assert.equal(ids.graph.attrs.width,String(width*2));assert.equal(ids.zoom.value,200);
assert.equal(v.scrollLeft,700);assert.equal(v.scrollTop,700);
v.listeners.pointercancel(pointer(2,300,100));v.listeners.pointermove(pointer(1,120,100));
assert.equal(v.scrollLeft,680);v.listeners.pointerup(pointer(1,120,100));
v.listeners.wheel({ctrlKey:true,deltaY:100,deltaMode:0,clientX:100,clientY:100,preventDefault(){}});
assert(Number(ids.graph.attrs.width)<width*2);
console.log('Drag, pinch anchor, cancel-to-drag, trackpad pinch, click suppression passed');


const nodeGroups=created.filter(e=>e.attrs.class==='node');
assert.equal(nodeGroups.length,new Set(nodeGroups.map(e=>e.attrs['aria-label'])).size);assert(nodeGroups.every(e=>e.style.display!=='none'));
for(const label of ['対局・学習','設定・引き継ぎ','Web検討','Webマイページ']) assert(created.some(e=>e.attrs['aria-label']===label));
ids.returns.checked=true;ids.returns.onchange();
assert(ids.transitions.children.every(e=>!e.hidden));
assert(paths.every(e=>e.attrs.d&&!e.attrs.d.match(/NaN|Infinity/)));
ids.fit.onclick();assert(Number(ids.graph.attrs.width)<=800+1e-6);
console.log('All groups, unique nodes, transitions and fit-to-view passed');

const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
const layout=script.slice(script.indexOf('const groupIds='),script.indexOf('const paths=edges.map'));
const fixture=[
 {id:'A',group:'one',groupLabel:'One'}, {id:'B',group:'one',groupLabel:'One'},
 {id:'ISOLATED',group:'one',groupLabel:'One'}, {id:'NEW',group:'new',groupLabel:'New group'},
 ...Array.from({length:30},(_,i)=>({id:'N'+i,group:'large',groupLabel:'Large group'}))
];
const context={metadata:fixture,byId:new Map(fixture.map(n=>[n.id,n])),edges:[{from:'A',to:'B',event:'Open',kind:'FORWARD'},{from:'B',to:'A',event:'Open',kind:'FORWARD'}],svg:element('svg'),el(tag,attrs,parent){const e=element(tag);Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v));parent.appendChild(e);return e;}};
vm.runInNewContext(layout+';globalThis.result={positions,sections,graphWidth,graphHeight}',context);
const result=context.result;
assert.equal(result.positions.size,fixture.length);
for(const [name,p] of result.positions){
 const box=result.sections[p.group];
 assert(p.x-110>=box.x&&p.x+110<=box.x+box.width,name);
 assert(p.y-25>=box.y&&p.y+39<=box.y+box.height,name);
 assert(Number.isFinite(p.x)&&Number.isFinite(p.y));
}
for(let i=0;i<result.sections.length;i++)for(let j=i+1;j<result.sections.length;j++){
 const a=result.sections[i],b=result.sections[j];
 assert(a.x+a.width<=b.x||b.x+b.width<=a.x||a.y+a.height<=b.y||b.y+b.height<=a.y);
}
console.log('Automatic layout: cycle, isolated/new nodes, new group, growing frame bounds passed');

const rectangles=nodeGroups.map(g=>{const [x,y]=g.attrs.transform.match(/-?[\d.]+/g).map(Number);return {name:g.attrs['aria-label'],x:x-110,y:y-25,w:220,h:64};});
for(const path of paths){
 const d=path.attrs.d;if(d.includes('C'))continue;
 const commands=[...d.matchAll(/([MHV])\s*(-?[\d.]+)(?:\s+(-?[\d.]+))?/g)];
 let x,y;
 for(const [,command,first,second] of commands){
  const nx=command==='V'?x:Number(first),ny=command==='H'?y:Number(command==='V'?first:second);
  assert(nx>0&&nx<dimensions[2]&&ny>0&&ny<dimensions[3], 'Arrow leaves viewBox');
  if(command!=='M')for(const r of rectangles){
   const crosses=x===nx?x>r.x&&x<r.x+r.w&&Math.max(y,ny)>r.y&&Math.min(y,ny)<r.y+r.h:
     y>r.y&&y<r.y+r.h&&Math.max(x,nx)>r.x&&Math.min(x,nx)<r.x+r.w;
   assert(!crosses,'Arrow '+path.attrs['data-transition']+' crosses '+r.name);
  }
  x=nx;y=ny;
 }
}
const xOf=name=>rectangles.find(r=>r.name===name).x;
assert(xOf('GAME_LIST')<xOf('ANALYZING'));assert(xOf('ANALYZING')<xOf('REPORT'));
console.log('Flow order and card avoidance for every orthogonal arrow passed');

v.clientWidth=100;v.clientHeight=80;ids.fit.onclick();assert(Number(ids.graph.attrs.width)<=100+1e-6);assert(Number(ids.graph.attrs.height)<=80+1e-6);
console.log("Fit-to-view below 20 percent passed");
