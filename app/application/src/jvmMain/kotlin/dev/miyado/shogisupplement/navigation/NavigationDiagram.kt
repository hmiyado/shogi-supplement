package dev.miyado.shogisupplement.navigation

import java.io.File

/** 実行時と同じ遷移表を読み込む。ソースの正規表現解析や別の遷移定義は持たない。 */
fun navigationDiagramHtml(): String {
    val nodes = AppDestination.entries.joinToString(",") { "\"${it.name}\"" }
    val edges = NavigationMachine.transitions.mapIndexed { index, transition ->
        val event = when (transition.event) {
            is NavigationEvent.Open -> "Open"
            NavigationEvent.Back -> "Back"
            NavigationEvent.AnalysisStarted -> "AnalysisStarted"
            NavigationEvent.AnalysisCompleted -> "AnalysisCompleted"
            NavigationEvent.AnalysisClosed -> "AnalysisClosed"
            NavigationEvent.RestoreAuthenticated -> "RestoreAuthenticated"
        }
        "{id:$index,from:\"${transition.from.name}\",to:\"${transition.to.name}\",event:\"$event\"}"
    }.joinToString(",\n")
    return """<!doctype html>
<html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>画面遷移図</title>
<style>
body{margin:24px;background:#F7F3EA;color:#211E1A;font-family:system-ui,sans-serif}
button,input{font:inherit}button{margin-right:8px;padding:6px 12px;border:1px solid #3A4B7C;background:#FFFDF7;color:#3A4B7C}
#viewport{overflow:auto;border:1px solid #DDD5C4;height:70vh;background:#FFFDF7}
svg{display:block}svg text{font-family:monospace;font-size:13px;fill:#211E1A}
.node{cursor:pointer}.node rect{fill:#E4E8F2;stroke:#3A4B7C}.edge{fill:none;stroke:#8C857B;stroke-width:1.5}
.edge.active{stroke:#3A4B7C;stroke-width:3}.dim{opacity:.08}
table{border-collapse:collapse;width:100%;margin-top:24px}th,td{text-align:left;border-bottom:1px solid #DDD5C4;padding:8px;font-family:monospace}
</style>
<h1>画面遷移図</h1>
<p>NavigationMachineの遷移表から生成。画面を選ぶと出入りする遷移だけを強調します。</p>
<p>各プラットフォームへの接続状況は、この図だけでは保証しません。解析イベントは表示中の要求IDが一致する場合だけ反映します。</p>
<p><button id="reset">全画面</button><label>拡大率 <input id="zoom" type="range" min="50" max="200" value="100"> <output id="scale">100%</output></label></p>
<div id="viewport"><svg id="graph" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1500 1500" width="1500" height="1500" role="group" aria-label="画面遷移図"></svg></div>
<table><thead><tr><th>遷移元</th><th>操作</th><th>遷移先</th></tr></thead><tbody id="transitions"></tbody></table>
<script>
'use strict';
const nodes=[$nodes];
const edges=[$edges];
const svg=document.getElementById('graph'), ns='http://www.w3.org/2000/svg';
function el(tag,attrs,parent){const e=document.createElementNS(ns,tag);Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v));parent.appendChild(e);return e;}
const defs=el('defs',{},svg), marker=el('marker',{id:'arrow',viewBox:'0 0 10 10',refX:9,refY:5,markerWidth:7,markerHeight:7,orient:'auto-start-reverse'},defs);
el('path',{d:'M 0 0 L 10 5 L 0 10 z',fill:'#3A4B7C'},marker);
const positions=new Map(nodes.map((name,i)=>{const angle=2*Math.PI*i/nodes.length-Math.PI/2;return [name,{x:750+560*Math.cos(angle),y:750+560*Math.sin(angle)}];}));
const paths=edges.map(e=>{const a=positions.get(e.from),b=positions.get(e.to),dx=b.x-a.x,dy=b.y-a.y,len=Math.hypot(dx,dy),ux=dx/len,uy=dy/len;
const start=Math.min(110/Math.max(Math.abs(ux),.001),25/Math.max(Math.abs(uy),.001));
const end=Math.min(110/Math.max(Math.abs(ux),.001),25/Math.max(Math.abs(uy),.001));
const parallel=edges.filter(other=>other.from===e.from&&other.to===e.to);
const bend=100+70*(parallel.findIndex(other=>other.id===e.id)-(parallel.length-1)/2);
const path=el('path',{class:'edge','data-transition':e.id,d:'M '+(a.x+ux*start)+' '+(a.y+uy*start)+' Q '+(750-uy*bend)+' '+(750+ux*bend)+' '+(b.x-ux*end)+' '+(b.y-uy*end),'marker-end':'url(#arrow)'},svg);
el('title',{},path).textContent=e.from+' → '+e.to+' ('+e.event+')';return path;});
const rows=edges.map(e=>{const row=document.createElement('tr');[e.from,e.event,e.to].forEach(value=>{const cell=document.createElement('td');cell.textContent=value;row.appendChild(cell);});document.getElementById('transitions').appendChild(row);return row;});
function select(name){edges.forEach((e,i)=>{const active=!name||e.from===name||e.to===name;paths[i].classList.toggle('dim',!active);paths[i].classList.toggle('active',!!name&&active);rows[i].hidden=!active;});}
nodes.forEach(name=>{const p=positions.get(name),g=el('g',{class:'node',tabindex:0,role:'button','aria-label':name},svg);el('rect',{x:p.x-110,y:p.y-25,width:220,height:50,rx:4},g);el('text',{x:p.x,y:p.y+5,'text-anchor':'middle'},g).textContent=name;g.onclick=()=>select(name);g.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();select(name);}};});
document.getElementById('reset').onclick=()=>select(null);
document.getElementById('zoom').oninput=e=>{const size=1500*Number(e.target.value)/100;svg.setAttribute('width',size);svg.setAttribute('height',size);document.getElementById('scale').textContent=e.target.value+'%';};
</script></html>"""
}

fun main(args: Array<String>) {
    require(args.size == 1) { "Specify the output HTML path" }
    val output = File(args.single())
    output.parentFile.mkdirs()
    output.writeText(navigationDiagramHtml())
}
