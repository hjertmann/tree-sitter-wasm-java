import { EventEmitter } from 'events';
import { readFile } from 'fs/promises';

class DataLoader extends EventEmitter {
    constructor(config) {
        super();
        this.config = config;
        this.cache = new Map();
    }

    async load(key) {
        if (this.cache.has(key)) {
            return this.cache.get(key);
        }
        const data = await readFile(key, 'utf8');
        this.cache.set(key, data);
        this.emit('loaded', key);
        return data;
    }

    clear() {
        this.cache.clear();
        this.emit('cleared');
    }
}

function createLoader(config) {
    return new DataLoader(config);
}

const parseJson = (str) => {
    try {
        return JSON.parse(str);
    } catch (e) {
        return null;
    }
};

export { DataLoader, createLoader, parseJson };
