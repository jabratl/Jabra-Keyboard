const fs=require('fs');
const src=fs.readFileSync(process.argv[2]||'novel-formatter_v2_updated.html','utf8');
const sc=src.slice(src.indexOf('<script>')+8,src.indexOf('</script>'));
const a=sc.indexOf('const SCENE'); const aEnd=sc.indexOf('\n',a);
const b=sc.indexOf('const HRE'); const bEnd=sc.indexOf('let dT;');
const c=sc.indexOf('// core generator'); const cEnd=sc.indexOf('function finish');
const code=sc.slice(a,aEnd)+'\n'+sc.slice(b,bEnd)+'\n'+sc.slice(c,cEnd)+'\n;this.gen=gen;this.getRules=getRules;';
const ctx={}; new Function(code).call(ctx);
let seed=12345; const rnd=()=>{seed=(seed*1103515245+12345)&0x7fffffff;return seed/0x7fffffff;};
const pick=a=>a[Math.floor(rnd()*a.length)];
const words=['hello','world','"quoted"',"don't","'tis","rock 'n' roll",'Lin','Feng','...','--','a  b','**bold**','*it*','_it_','__bold__','***both***','snake_case_word','2*3*4','x','\u200bzero','nb\u00a0sp','the\u3000ideo','\u2026','bangla: আমি','"','\'','(\'x\')','&','<tag>','Read the latest chapters at foo.com','colour','Translated by','end.'];
const scenes=['***','---','* * *','___','~~~','# # #','•••','===','– – –'];
const heads=['Chapter 1','Chapter One: Start','Prologue','Ch. 12','Part IV','第12章 开始','Epilogue: x','chapter xyz'];
function block(){
  const r=rnd(); let runs=[];
  if(r<0.12){runs=[{t:pick(scenes),b:false,i:false,u:false}];}
  else if(r<0.25){runs=[{t:pick(heads),b:rnd()<.3,i:false,u:false}];}
  else{const n=1+Math.floor(rnd()*4);for(let k=0;k<n;k++){let t='';const m=1+Math.floor(rnd()*6);for(let j=0;j<m;j++)t+=(j?' ':'')+pick(words);if(rnd()<.2)t='  '+t+'  ';runs.push({t,b:rnd()<.3,i:rnd()<.3,u:rnd()<.2});}}
  return {runs,h:rnd()<.1?1+Math.floor(rnd()*3):0};
}
const ruleSets=['','Lin Feng => Lin Fang','colour => color\nTranslated by =>','re:\\bcolou?r\\b => $&!\nre:(\\w+)@(\\w+) => $2-$1','del: Read the latest chapters at','del-re: ^Ch\\.','# comment\nbad line\n=> x','x => $1 $$ y','re:(hello) (world) => $2 $1 [$&] $3','re:o* => 0','  spaced =>  two  ','re:[ => oops'];
const defs={indent:true,indentVal:'2',indentUnit:'em',skipFirst:true,spacing:'1',spacingUnit:'em',lh:'',fs:'',align:'none',keepB:true,keepI:true,keepU:true,headings:true,hcenter:false,scene:'center',sceneText:'* * *',collapse:true,quotes:'none',dots:false,dash:false,md:true,zw:true,autoH:'',rules:'',mode:'inline',sep:'single'};
const lines=[];const enc=encodeURIComponent;let N=1500;
for(let n=0;n<N;n++){
  const o=Object.assign({},defs);
  for(const k of Object.keys(defs)){
    if(typeof defs[k]==='boolean') o[k]=rnd()<.5;
  }
  o.indentVal=pick(['2','1.5','0','','abc','2.5em','.5']);o.indentUnit=pick(['em','px','rem','%']);
  o.spacing=pick(['1','0','1.2','','x','0.1']);o.spacingUnit=pick(['em','px','rem']);
  o.lh=pick(['','1.7','1.8','1.5;color:red','150%']);o.fs=pick(['','18px','1.2rem','x{y}']);
  o.align=pick(['none','left','justify','center','right']);o.scene=pick(['center','hr','text','remove']);o.sceneText=pick(['* * *','','  ◆ ','<b>']);
  o.quotes=pick(['none','straight','curly']);o.autoH=pick(['','1','2','3']);o.rules=pick(ruleSets);o.mode=pick(['inline','class','plain']);o.sep=pick(['single','none','blank']);
  const blocks=[];const nb=Math.floor(rnd()*7);for(let k=0;k<nb;k++)blocks.push(block());
  const res=ctx.gen(o,blocks);
  const R=ctx.getRules(o.rules);
  lines.push('CASE');
  lines.push('O\t'+Object.keys(o).map(k=>k+'='+enc(String(o[k]))).join('\t'));
  for(const b of blocks)lines.push('B\t'+b.h+'\t'+b.runs.map(r=>(r.b?1:0)+''+(r.i?1:0)+(r.u?1:0)+':'+enc(r.t)).join('\t'));
  lines.push('E\t'+enc(res.out)+'\t'+res.paras+'\t'+res.words+'\t'+R.bad);
}
fs.writeFileSync(process.argv[3]||'cases.txt',lines.join('\n')+'\n');
console.log('cases',N);
