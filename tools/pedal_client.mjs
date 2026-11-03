// Drives the Downshift web pedal over its WebSocket, for checks (Node 22+: global WebSocket).
//   node tools/pedal_client.mjs latency       steps 30 80 0 60 0 100 20 0, 2.5 s apart; prints "value hostMs" lines
//   node tools/pedal_client.mjs wave SECONDS  0..80 % at 1 Hz, a value every 20 ms (CPU check)
//   node tools/pedal_client.mjs drop          presses 80 % and exits without letting go (closed-socket release)
//   node tools/pedal_client.mjs silent        presses 80 % and stays connected but quiet for 3 s (watchdog release)
//   node tools/pedal_client.mjs idle SECONDS  stays connected and pings, like an open page nobody touches
const [mode = 'latency', arg = '30'] = process.argv.slice(2);
const ws = new WebSocket('ws://127.0.0.1:8790/ws');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const send = (o) => ws.send(JSON.stringify(o));
ws.onerror = () => {
  console.error('no web pedal on 127.0.0.1:8790 (demo mode on? adb forward tcp:8790 tcp:8790?)');
  process.exit(1);
};
ws.onopen = async () => {
  let id = 0;
  const ping = setInterval(() => send({ t: 'ping', id: ++id }), 500);
  if (mode === 'latency') {
    for (const v of [30, 80, 0, 60, 0, 100, 20, 0]) {
      console.log(`${v} ${Date.now()}`);
      send({ t: 'pedal', p: v });
      await sleep(2500);
    }
  } else if (mode === 'wave') {
    const end = Date.now() + Number(arg) * 1000;
    while (Date.now() < end) {
      send({ t: 'pedal', p: Math.round(40 - 40 * Math.cos((2 * Math.PI * Date.now()) / 1000)) });
      await sleep(20);
    }
    send({ t: 'pedal', p: 0 });
  } else if (mode === 'drop') {
    send({ t: 'pedal', p: 80 });
    await sleep(300);
    process.exit(0);
  } else if (mode === 'silent') {
    send({ t: 'pedal', p: 80 });
    clearInterval(ping);
    await sleep(3000);
  } else if (mode === 'idle') {
    await sleep(Number(arg) * 1000);
  }
  clearInterval(ping);
  await sleep(200);
  ws.close();
};
