const fs=require('fs'),vm=require('vm'),assert=require('assert');
const file='/Users/junjie01.hu/.codex/visualizations/2026/10/20/01a15809-f068-7263-80a5-8bc3d8938608/vault-cell-contributions.html';
const html=fs.readFileSync(file,'utf8'),script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
class Element {
 constructor(){this.style={};this.children=[];this.events={};this.dataset={};this.attrs={};this.maps={};}
 querySelector(s){if(!this.maps[s])throw Error('Unknown row selector '+s);return this.maps[s];}
 set innerHTML(s){for(const k of ['.vc-icon','strong','.vc-identity small','.vc-number','.vc-value>span','.vc-value>small','.vc-track>span'])this.maps[k]=new Element();}
 replaceChildren(){this.children=[];}
 append(c){this.children.push(c);}
 setAttribute(k,v){this.attrs[k]=v;}
 addEventListener(k,f){this.events[k]=f;}
 click(){assert(!this.disabled,'disabled control');this.events.click();}
}
const root=new Element(),els={};
for(const s of ['[data-total]','[data-count]','[data-types]','[data-used]','[data-free]','[data-rows]','[data-page]','[data-prev]','[data-next]','[data-selected-name]','[data-selected-rule]','[data-single]','[data-exact]','[data-open]'])els[s]=new Element();
const nav=['overview','details'].map(v=>{let e=new Element();e.dataset.view=v;return e}),panels=['overview','details'].map(v=>{let e=new Element();e.dataset.panel=v;return e});
root.querySelector=s=>{if(!els[s])throw Error('Unknown root selector '+s);return els[s];};root.querySelectorAll=s=>s==='[data-view]'?nav:s==='[data-panel]'?panels:(()=>{throw Error(s)})();
const saves=[];
const context={document:{getElementById:id=>{assert.equal(id,'vault-contribution-preview');return root;},createElement:()=>new Element()},window:{openai:{setWidgetState:state=>{saves.push(state);return Promise.resolve();}},addEventListener:()=>{}},console};
vm.runInNewContext(script,context);
assert.equal(els['[data-types]'].textContent,'12 种');assert.equal(els['[data-rows]'].children.length,6);assert(els['[data-selected-rule]'].textContent.includes('原容量 + 1'));
assert.equal(els['[data-single]'].textContent,'17,592,186,044,416 B');assert(els['[data-prev]'].disabled);
els['[data-next]'].click();assert.equal(els['[data-page]'].textContent,'2 / 2');assert(els['[data-next]'].disabled);
els['[data-rows]'].children[1].click();assert.equal(els['[data-selected-name]'].textContent,'量子物品存储元件');
nav[0].click();assert.equal(panels[0].hidden,false);assert.equal(panels[1].hidden,true);
els['[data-open]'].click();assert.equal(panels[1].hidden,false);els['[data-prev]'].click();assert.equal(els['[data-page]'].textContent,'1 / 2');
assert(saves.length>=5);assert(!html.includes('__VAULT_ASSETS__'));assert(!html.includes('fetch('));
console.log(JSON.stringify({passed:true,checks:['JS syntax and all DOM selectors','exact adjusted capacity','six-row pagination and boundaries','row selection','overview/detail navigation','state persistence'],scope:'Static DOM harness; no browser or game client launched'}));
