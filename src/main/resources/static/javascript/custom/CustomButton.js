import Custom from './Custom.js';
import Shortcut from './Shortcut.js';

export default class CustomButton extends Custom {
    static get observedAttributes() {
        return ['onclick', 'shortcut', 'value'];
    }

    connectedCallback() {
        this.render();
        this.#updateShortcut(null, this.getAttr('shortcut'));
    }

    disconnectedCallback() {
        Shortcut.remove(this.getAttr('shortcut'));
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (oldValue === newValue) return;
        
        if (name === 'shortcut') {
            this.#updateShortcut(oldValue, newValue);
        }
        
        this.render();
    }

    #updateShortcut(oldKey, newKey) {
        if (oldKey) Shortcut.remove(oldKey);
        if (newKey) Shortcut.add(newKey, () => this.execute());
    }

    execute() {
        this.click();
    }
}

customElements.define('custom-button', CustomButton);
