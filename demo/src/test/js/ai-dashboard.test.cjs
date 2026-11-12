const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/app.js', 'utf8');
test('service errors cannot produce a local AI prediction or probability gauge', () => {
  assert.doesNotMatch(source, /getLocalAiClassification/);
  assert.doesNotMatch(source, /Math\.round\(data\.suitabilityIndex\)/);
  assert.doesNotMatch(source, /Акватория чиста/);
});
test('microphone PCM WAV contains real RIFF header and signed mono samples', () => {
  const match = source.match(/function encodePcmWav\(buffer\) \{[\s\S]*?\n\}/);
  assert.ok(match, 'PCM encoder must exist');
  const context = {Blob, DataView, ArrayBuffer, Math};
  vm.createContext(context);
  vm.runInContext(match[0] + '; this.encode = encodePcmWav;', context);
  const buffer = {numberOfChannels: 1, length: 3, sampleRate: 16000, getChannelData: () => new Float32Array([-1, 0, 1])};
  return context.encode(buffer).arrayBuffer().then(bytes => {
    const view = new DataView(bytes);
    assert.equal(Buffer.from(bytes).subarray(0, 4).toString(), 'RIFF');
    assert.equal(Buffer.from(bytes).subarray(8, 12).toString(), 'WAVE');
    assert.equal(view.getUint32(24, true), 16000);
    assert.equal(view.getUint32(40, true), 6);
    assert.equal(view.getInt16(44, true), -32768);
    assert.equal(view.getInt16(48, true), 32767);
  });
});

test('station choices use actual API IDs/names and preserve selection', () => {
  const match = source.match(/function updateStationSelect\(stations, selectorId\) \{[\s\S]*?\n\}/);
  assert.ok(match, 'station selector must be populated from API records');
  const select = {value: '42', disabled: false, options: [], replaceChildren(...options) { this.options = options; this.value = options[0]?.value || ''; }};
  const context = {document: {getElementById: () => select, createElement: () => ({value:'',textContent:''})}, String};
  vm.createContext(context); vm.runInContext(match[0]+';this.update=updateStationSelect;',context);
  context.update([{id:7,name:'Дамчикский участок'},{id:42,name:'ВКМСК'}],'station-select-audio');
  assert.deepEqual(select.options.map(o=>[o.value,o.textContent]),[['7','Дамчикский участок'],['42','ВКМСК']]);
  assert.equal(select.value,'42');
  context.update([],'station-select-audio'); assert.equal(select.disabled,true);
  const html=fs.readFileSync('src/main/resources/static/index.html','utf8');
  const audio=html.match(/<select id="station-select-audio">([\s\S]*?)<\/select>/)[1];
  assert.doesNotMatch(audio, /value="[0-9]+"/);
});
