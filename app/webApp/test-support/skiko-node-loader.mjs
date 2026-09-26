import { registerHooks } from 'node:module';

// Skiko's web runtime disables Node file loading. Keep browser artifacts intact
// and supply the real WASM instance only in the Node test process.
registerHooks({
    load(url, context, nextLoad) {
        const result = nextLoad(url, context);
        if (!url.startsWith('file:') || !url.endsWith('/skiko.mjs')) return result;

        const source = typeof result.source === 'string'
            ? result.source
            : new TextDecoder().decode(result.source);
        const entry = 'loadSkikoWASM()';
        if (source.split(entry).length !== 2) {
            throw new Error('Skiko initialization changed; review the Node test loader');
        }
        return {
            ...result,
            source: `import { readFileSync as readSkikoWasm } from 'node:fs';\n` +
                source.replace(entry, `loadSkikoWASM({
                    instantiateWasm(imports, receiveInstance) {
                        const bytes = readSkikoWasm(new URL('./skiko.wasm', import.meta.url));
                        const module = new WebAssembly.Module(bytes);
                        receiveInstance(new WebAssembly.Instance(module, imports), module);
                    }
                })`),
        };
    },
});
