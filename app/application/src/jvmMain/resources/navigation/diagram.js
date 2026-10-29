
'use strict';
const nodes=data.nodes;
const edges=data.edges;
const svg=document.getElementById('graph'), ns='http://www.w3.org/2000/svg';
function el(tag,attrs,parent){const e=document.createElementNS(ns,tag);Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v));parent.appendChild(e);return e;}
const defs=el('defs',{},svg), marker=el('marker',{id:'arrow',viewBox:'0 0 10 10',refX:9,refY:5,markerWidth:7,markerHeight:7,orient:'auto-start-reverse'},defs);
el('path',{d:'M 0 0 L 10 5 L 0 10 z',fill:'#3A4B7C'},marker);
const metadata=data.metadata;
const labels=Object.fromEntries(metadata.map(n=>[n.id,n.label]));
const byId=new Map(metadata.map(n=>[n.id,n]));
const groupIds=[...new Set(metadata.map(n=>n.group))];
const tabNodes=new Set(edges.filter(e=>e.event.startsWith('SelectTab(')).flatMap(e=>[e.from,e.to]));
const sections=groupIds.map(id=>{
 const members=metadata.filter(n=>n.group===id).map(n=>n.id), memberSet=new Set(members);
 const links=edges.filter(e=>memberSet.has(e.from)&&memberSet.has(e.to)&&e.from!==e.to&&e.kind==='FORWARD'&&!(tabNodes.has(e.from)&&tabNodes.has(e.to)));
 const dag=new Map(members.map(n=>[n,new Set()]));
 function reaches(from,to,visited=new Set()){
  if(from===to)return true;if(visited.has(from))return false;visited.add(from);
  return [...dag.get(from)].some(n=>reaches(n,to,visited));
 }
 const priority=e=>e.event==='AnalysisCompleted'?0:e.event==='Open'?1:2;
 [...links].sort((a,b)=>priority(a)-priority(b)).forEach(e=>{if(!reaches(e.to,e.from))dag.get(e.from).add(e.to);});
 const incoming=new Map(members.map(n=>[n,0])),rank=new Map(members.map(n=>[n,0]));
 dag.forEach(targets=>targets.forEach(n=>incoming.set(n,incoming.get(n)+1)));
 const queue=members.filter(n=>incoming.get(n)===0);
 for(let i=0;i<queue.length;i++)dag.get(queue[i]).forEach(n=>{
  rank.set(n,Math.max(rank.get(n),rank.get(queue[i])+1));
  incoming.set(n,incoming.get(n)-1);if(incoming.get(n)===0)queue.push(n);
 });
 const columns=[];members.forEach(n=>{const r=rank.get(n);(columns[r]??=[]).push(n);});
 const parents=n=>[...new Set(links.filter(e=>e.to===n&&rank.get(e.from)<rank.get(n)).map(e=>e.from))];
 const children=n=>[...dag.get(n)];
 const row=new Map();columns.forEach(c=>c.forEach((n,i)=>row.set(n,i)));
 const mean=values=>values.reduce((sum,n)=>sum+row.get(n),0)/values.length;
 for(let pass=0;pass<8;pass++){
  for(const direction of [1,-1]){
   const order=direction===1?columns:[...columns].reverse();
   order.forEach(column=>{
    const score=n=>{const adjacent=direction===1?parents(n):children(n);return adjacent.length?mean(adjacent):row.get(n);};
    column.sort((a,b)=>score(a)-score(b)||a.localeCompare(b));
    column.forEach((n,i)=>row.set(n,i));
   });
  }
 }
 const exclusive=n=>children(n).filter(child=>parents(child).length===1).sort((a,b)=>row.get(a)-row.get(b)||a.localeCompare(b));
 const span=n=>Math.max(1,exclusive(n).reduce((sum,child)=>sum+span(child),0));
 const occupied=new Map(columns.map((_,i)=>[i,[]]));
 function place(n,start){
  const kids=exclusive(n),size=span(n);
  row.set(n,start+(size-1)/2);
  occupied.get(rank.get(n)).push(row.get(n));
  let cursor=start;
  kids.forEach(child=>{place(child,cursor);cursor+=span(child);});
 }
 let cursor=0;
 columns[0].forEach(n=>{place(n,cursor);cursor+=span(n)+1;});
 columns.slice(1).forEach(column=>column.filter(n=>parents(n).length!==1).forEach(n=>{
  const target=Math.max(0,Math.round(parents(n).length?mean(parents(n)):row.get(n)));
  const fits=start=>{
   const slots=[];
   function collect(node,y){const size=span(node);slots.push([rank.get(node),y+(size-1)/2]);let next=y;exclusive(node).forEach(child=>{collect(child,next);next+=span(child);});}
   collect(n,start);
   return slots.every(([col,y])=>occupied.get(col).every(taken=>Math.abs(taken-y)>=1));
  };
  let start=target;
  for(let distance=0;!fits(start);distance++){
   const above=target-distance-1,below=target+distance+1;
   start=above>=0&&fits(above)?above:below;
  }
  place(n,start);
 }));
 return {title:byId.get(members[0]).groupLabel,columns,row,width:columns.length*400+60,height:(Math.max(...row.values())+1)*130+180};
});
const gridColumns=Math.ceil(Math.sqrt(sections.length));
const columnWidths=Array.from({length:gridColumns},(_,x)=>Math.max(...sections.filter((_,i)=>i%gridColumns===x).map(s=>s.width)));
const rowHeights=Array.from({length:Math.ceil(sections.length/gridColumns)},(_,y)=>Math.max(...sections.slice(y*gridColumns,(y+1)*gridColumns).map(s=>s.height)));
const positions=new Map();
sections.forEach((section,index)=>{
 section.x=20+columnWidths.slice(0,index%gridColumns).reduce((a,b)=>a+b+40,0);
 section.y=20+rowHeights.slice(0,Math.floor(index/gridColumns)).reduce((a,b)=>a+b+40,0);
 const group=el('g',{'aria-label':section.title},svg);
 el('rect',{x:section.x,y:section.y,width:section.width,height:section.height,rx:8,fill:'#FFFDF7',stroke:'#DDD5C4','stroke-width':2},group);
 el('text',{x:section.x+24,y:section.y+36,style:'font-family:"Shippori Mincho",serif;font-size:24px'},group).textContent=section.title;
 section.columns.forEach((column,x)=>column.forEach((name,y)=>positions.set(name,{x:section.x+140+x*400,y:section.y+140+section.row.get(name)*130,column:x,group:index,top:section.y+65})));
});
const graphWidth=columnWidths.reduce((a,b)=>a+b+40,0),graphHeight=rowHeights.reduce((a,b)=>a+b+40,0)+20;
svg.setAttribute('viewBox','0 0 '+graphWidth+' '+graphHeight);svg.setAttribute('width',graphWidth);svg.setAttribute('height',graphHeight);
const edgeHalos=[];
const paths=edges.map(e=>{
 edgeHalos.push(el('path',{fill:'none',stroke:'#FFFDF7','stroke-width':7,'pointer-events':'none'},svg));
 const path=el('path',{class:'edge','data-transition':e.id,'marker-end':'url(#arrow)'},svg);
 el('title',{},path).textContent=labels[e.from]+' → '+labels[e.to]+' ('+e.event+')';return path;
});
const rows=edges.map(e=>{const row=document.createElement('tr');[labels[e.from],e.event,labels[e.to]].forEach(value=>{const cell=document.createElement('td');cell.textContent=value;row.appendChild(cell);});document.getElementById('transitions').appendChild(row);return row;});
const nodeElements=new Map(nodes.map(name=>{
 const g=el('g',{class:'node',tabindex:0,role:'button','aria-label':name},svg);
 el('rect',{x:-110,y:-25,width:220,height:64,rx:4},g);
 el('text',{y:0,'text-anchor':'middle',style:'font-family:"IBM Plex Sans JP",sans-serif;font-size:16px'},g).textContent=labels[name]||name;
 el('text',{y:23,'text-anchor':'middle',style:'font-size:10px;fill:#5C564C'},g).textContent=name;
 g.onclick=()=>select(name);g.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();select(name);}};
 return [name,g];
}));
let selected=null;
const returns=document.getElementById('returns');
function select(name){selected=name;render();}
function render(){
 nodeElements.forEach((g,name)=>{const p=positions.get(name);g.style.display=p?'':'none';if(p)g.setAttribute('transform','translate('+p.x+','+p.y+')');g.classList.toggle('selected',name===selected);});
 const seen=new Set();
 const neighbors=(name,side)=>[...new Set(edges.filter(e=>e.from!==e.to&&(returns.checked||e.kind==='FORWARD')&&e[side]===name).map(e=>side==='from'?e.to:e.from))];
 function port(name,other,side){const values=neighbors(name,side);return (values.indexOf(other)+1)*48/(values.length+1)-18;}

 edges.forEach((e,i)=>{
  const a=positions.get(e.from),b=positions.get(e.to);
  const secondary=e.kind!=='FORWARD';
  const visible=!!a&&!!b&&(returns.checked||!secondary)&&(!selected||e.from===selected||e.to===selected);
  rows[i].hidden=!visible;
  const key=e.from+'>'+e.to;
  paths[i].style.display=visible&&!seen.has(key)&&e.from!==e.to?'':'none';
  edgeHalos[i].style.display=paths[i].style.display;
  if(!visible)return;
  seen.add(key);
  const forward=b.x>a.x, sx=a.x+(forward?110:-110),tx=b.x+(forward?-110:110);
  const ay=a.y+port(e.from,e.to,'from'),by=b.y+port(e.to,e.from,'to');
  let d;
  if(e.from===e.to)d='M '+(a.x-40)+' '+(a.y-25)+' C '+(a.x-100)+' '+(a.y-90)+' '+(a.x+100)+' '+(a.y-90)+' '+(a.x+40)+' '+(a.y-25);
  else if(a.group!==b.group){
   const source=sections[a.group],target=sections[b.group],out=a.x+145,entry=b.x-145;
   const sourceLane=source.y+source.height+20,targetLane=target.y+target.height+20;
   const gutter=Math.min(source.x,target.x)-10;
   d='M '+(a.x+110)+' '+a.y+' H '+out+' V '+sourceLane+' H '+gutter+' V '+targetLane+' H '+entry+' V '+b.y+' H '+(b.x-110);
  }
  else if(b.column-a.column===1){const bend=(tx-sx)*.55;d='M '+sx+' '+ay+' C '+(sx+bend)+' '+ay+' '+(tx-bend)+' '+by+' '+tx+' '+by;}
  else {
   const lane=a.top+(i%3)*16,out=a.x+145,entry=b.x-145;
   d='M '+(a.x+110)+' '+a.y+' H '+out+' V '+lane+' H '+entry+' V '+b.y+' H '+(b.x-110);
  }
  edgeHalos[i].setAttribute('d',d);paths[i].setAttribute('d',d);paths[i].classList.toggle('active',!!selected);
 });
}
returns.onchange=render;
document.getElementById('reset').onclick=()=>select(null);
render();
const viewport=document.getElementById('viewport'), zoom=document.getElementById('zoom');
let scale=1, suppressClick=false;
function point(e){const r=viewport.getBoundingClientRect();return {x:e.clientX-r.left-viewport.clientLeft,y:e.clientY-r.top-viewport.clientTop};}
function zoomAt(value,anchor,fitToView=false){
  const next=fitToView?value:Math.max(.01,Math.min(2,value)), ratio=next/scale;
  const left=(viewport.scrollLeft+anchor.x)*ratio-anchor.x;
  const top=(viewport.scrollTop+anchor.y)*ratio-anchor.y;
  scale=next;
  svg.setAttribute('width',graphWidth*scale);svg.setAttribute('height',graphHeight*scale);
  viewport.scrollLeft=left;viewport.scrollTop=top;
  zoom.min=Math.min(20,scale*100);zoom.value=scale*100;document.getElementById('scale').textContent=Math.round(scale*100)+'%';
}
zoom.oninput=e=>zoomAt(Number(e.target.value)/100,{x:viewport.clientWidth/2,y:viewport.clientHeight/2});
function fit(){zoomAt(Math.min(viewport.clientWidth/graphWidth,viewport.clientHeight/graphHeight),{x:0,y:0},true);viewport.scrollLeft=0;viewport.scrollTop=0;}
document.getElementById('fit').onclick=fit;
let fitted=false;
new ResizeObserver(()=>{if(!fitted&&viewport.clientWidth>0&&viewport.clientHeight>0){fit();fitted=true;}}).observe(viewport);
const pointers=new Map();
let gesture=null;
function measure(){
  const p=[...pointers.values()];
  return p.length===1?{center:p[0],distance:0}:{center:{x:(p[0].x+p[1].x)/2,y:(p[0].y+p[1].y)/2},distance:Math.hypot(p[0].x-p[1].x,p[0].y-p[1].y)};
}
viewport.addEventListener('pointerdown',e=>{
  if(e.button!==0)return;
  if(!pointers.size)suppressClick=false;
  pointers.set(e.pointerId,point(e));
  e.target.setPointerCapture(e.pointerId);
  gesture=measure();
  if(pointers.size>1)suppressClick=true;
  viewport.classList.add('dragging');
});
viewport.addEventListener('pointermove',e=>{
  if(!pointers.has(e.pointerId))return;
  pointers.set(e.pointerId,point(e));const next=measure();
  const dx=next.center.x-gesture.center.x,dy=next.center.y-gesture.center.y;
  if(!suppressClick&&Math.hypot(dx,dy)<4)return;
  suppressClick=true;
  if(gesture.distance>0&&next.distance>0)zoomAt(scale*next.distance/gesture.distance,gesture.center);
  viewport.scrollLeft-=dx;viewport.scrollTop-=dy;gesture=next;
});
function endPointer(e){
  if(!pointers.delete(e.pointerId))return;
  gesture=pointers.size?measure():null;
  viewport.classList.toggle('dragging',pointers.size>0);
}
['pointerup','pointercancel','lostpointercapture'].forEach(type=>viewport.addEventListener(type,endPointer));
viewport.addEventListener('click',e=>{if(suppressClick&&e.detail!==0){e.preventDefault();e.stopPropagation();}},true);
let trackpadGesture=false, trackpadStart=1;
viewport.addEventListener('wheel',e=>{
  if(!e.ctrlKey)return;
  e.preventDefault();if(trackpadGesture)return;
  const delta=e.deltaY*(e.deltaMode===1?16:e.deltaMode===2?viewport.clientHeight:1);
  zoomAt(scale*Math.exp(-delta*.01),point(e));
},{passive:false});
viewport.addEventListener('gesturestart',e=>{e.preventDefault();trackpadGesture=true;trackpadStart=scale;},{passive:false});
viewport.addEventListener('gesturechange',e=>{e.preventDefault();zoomAt(trackpadStart*e.scale,point(e));},{passive:false});
viewport.addEventListener('gestureend',e=>{e.preventDefault();trackpadGesture=false;},{passive:false});
