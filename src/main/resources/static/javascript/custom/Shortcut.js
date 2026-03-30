export default class Shortcut {
    static #registry = new Map();
    static #initialized = false;

    static get registry() {
        return this.#registry;
    }

    static #normalize(shortcutStr) {
        return shortcutStr
            .toLowerCase()
            .split('+')
            .map(k => k.trim())
            .filter(k => k !== '')
            .sort()
            .join(' + ');
    }

    static init() {
        if (this.#initialized) return;

        window.addEventListener('keydown', (e) => {
            if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName) ||
                    document.activeElement.isContentEditable) return;

            const keys = [];
            if (e.ctrlKey) keys.push('ctrl');
            if (e.shiftKey) keys.push('shift');
            if (e.altKey) keys.push('alt');
            if (e.metaKey) keys.push('meta');

            const mainKey = e.key.toLowerCase();

            if (!['Control', 'Shift', 'Alt', 'Meta'].includes(mainKey)) {
                keys.push(mainKey);
            }

            const pressedShortcut = keys.sort().join(' + ');

            if (this.#registry.has(pressedShortcut)) {
                e.preventDefault();
                const callback = this.registry.get(pressedShortcut);
                callback(e);
            }
        });
        
        this.#initialized = true;
    }

    static add(key, callback) {
        this.init();
        const normalizedKey = this.#normalize(key);
        this.#registry.set(normalizedKey, callback);
    }

    static remove(key) {
        const normalizedKey = this.#normalize(key);
        this.#registry.delete(normalizedKey);
    }
}