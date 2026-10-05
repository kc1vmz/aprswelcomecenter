/* Shared serial configuration controls. Discovery runs on the application server. */
(() => {
  const rates = [1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400];
  for (const form of document.querySelectorAll('form[data-serial-port-url]')) {
    const input = form.elements.serialDevice, baud = form.elements.baudRate;
    const status = form.querySelector('[data-serial-status]');
    let ports = [], error = null, loaded = false, generation = 0;
    function render() {
      const selected = input.value;
      input.replaceChildren();
      const devices = [...new Set(ports.map(port => port.device))];
      if (selected && !devices.includes(selected)) devices.push(selected);
      devices.sort((a,b) => a.localeCompare(b, undefined, {numeric:true}));
      for (const device of ['', ...devices]) {
        const option = document.createElement('option');
        option.value = device; option.textContent = device;
        input.append(option);
      }
      input.value = selected;
      const current = ports.find(p => p.device === input.value);
      status.textContent = error || (!loaded ? 'Loading ports from the application server…'
        : input.value && !current?.detected ? 'Not currently detected.'
        : ports.some(p => p.detected) ? ''
        : 'No serial ports detected.');
      status.hidden = !status.textContent;
    }
    async function load() {
      const request = ++generation;
      loaded = false; error = null; render();
      try {
        const response = await fetch(form.dataset.serialPortUrl, {cache: 'no-store'});
        if (!response.ok || response.redirected) throw new Error();
        const result = await response.json();
        if (request !== generation) return;
        ports = result.ports; error = result.error ? 'Unable to discover serial ports.' : null;
      } catch {
        if (request !== generation) return;
        error = 'Unable to discover serial ports.';
      } finally {
        if (request === generation) { loaded = true; render(); }
      }
    }
    function prepare(value, device = '') {
      ports = []; loaded = false; error = null;
      input.replaceChildren();
      const saved = document.createElement('option'); saved.value = device; saved.textContent = device;
      input.append(saved); input.value = device;
      const selected = Number(value || 9600);
      baud.replaceChildren();
      for (const rate of [...new Set([...rates, selected])].sort((a,b) => a-b)) {
        const option = document.createElement('option'); option.value = String(rate);
        option.textContent = String(rate) + (rates.includes(rate) ? '' : ' (saved value)');
        baud.append(option);
      }
      baud.value = String(selected);
    }
    input.addEventListener('change', render);
    form.serialPortPicker = {prepare, load};
    prepare(baud.dataset.savedValue || baud.value, input.value);
    if (form.dataset.serialAutoload === 'true') load();
  }
})();
