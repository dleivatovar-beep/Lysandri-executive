import json,re,statistics,time,unicodedata,uuid,sys
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError
repo=Path(__file__).resolve().parents[2]
cases=json.loads(Path(__file__).with_name('preguntas libres.json').read_text(encoding='utf-8'))
output=repo/'backend/evaluacion/resultados/preguntas finales.json'
output.parent.mkdir(exist_ok=True)
def norm(s): return ''.join(c for c in unicodedata.normalize('NFD',s.lower()) if unicodedata.category(c)!='Mn')
def numbers(s): return re.findall(r'(?<![\w])\d+(?:[.,]\d+)?',s)
def send(payload):
 req=Request('http://127.0.0.1:3000/api/v1/chat/rag',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
 with urlopen(req,timeout=100) as r:return json.load(r)
results=[]
sessions={}
subset=set(map(int,sys.argv[1].split(','))) if len(sys.argv)>1 else None
for case in cases:
 if subset and case['id'] not in subset:continue
 group=case.get('grupo')
 payload={'pregunta':case['pregunta']}
 if group and group in sessions:payload.update(sessions[group])
 start=time.perf_counter()
 try:
  answer=send(payload)
  if group:sessions[group]={'sesionId':answer['sesionId'],'sessionToken':answer['sessionToken']} if answer.get('sessionToken') else {**sessions.get(group,{}),'sesionId':answer['sesionId']}
  text=answer['respuesta']
  expected=case['esperado']
  missing=[]
  low=norm(text)
  if 'importe' in expected and not any(abs(float(s.replace(',','.'))-float(expected['importe']))<0.001 for s in numbers(text)):missing.append('importe')
  if expected.get('moneda','').lower() not in low:missing.append('moneda')
  if 'horas' in expected and not re.search(r'\b'+re.escape(expected['horas'])+r'\s*horas?\b',low):missing.append('horas')
  for importe in expected.get('importes',[]):
   if not any(abs(float(n.replace(',','.'))-float(importe))<0.001 for n in numbers(text)):missing.append('importe '+importe)
  if expected.get('economico'):
   comprobacion = r'(?:mas economico(?: es)?|mas barato(?: es)?)\s+(?:el curso\s+)?' + re.escape(norm(expected['economico']))
   if not re.search(comprobacion,low):missing.append('curso mas economico')
  for grupoPalabras in expected.get('palabras',[]):
   if not any(norm(palabra) in low for palabra in grupoPalabras):missing.append(' / '.join(grupoPalabras))
  for subject in expected.get('temas',[]):
   words=[w for w in norm(subject).split() if len(w)>3]
   if words and not any(w in low for w in words):missing.append(subject)
  for name in expected.get('cursos',[]):
   if norm(name) not in low:missing.append(name)
  if case['tipo'] not in ('sin informacion','limites') and not answer.get('fuentes'):missing.append('fuentes')
  if case['tipo']=='sin informacion' and 'todavia no tengo ese dato' not in low:
   if 'fecha' not in low and 'inicio' not in low:missing.append('fecha sin confirmar')
   if 'docente' not in low and 'profesor' not in low:missing.append('docente sin confirmar')
  result=dict(id=case['id'],tipo=case['tipo'],pregunta=case['pregunta'],respuesta=text,
              segundos=round(time.perf_counter()-start,3),tokens=answer['tokensUsados'],
              fuentes=answer.get('fuentes',[]),faltantes=missing,revision='PENDIENTE')
 except Exception as e:
  result=dict(id=case['id'],tipo=case['tipo'],pregunta=case['pregunta'],segundos=round(time.perf_counter()-start,3),error=str(e),faltantes=['error'],revision='NO EJECUTADA')
 results.append(result)
 output.write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({k:result.get(k) for k in ('id','tipo','segundos','faltantes','respuesta','error')},ensure_ascii=False),flush=True)
print('TERMINADO',len(results),flush=True)
