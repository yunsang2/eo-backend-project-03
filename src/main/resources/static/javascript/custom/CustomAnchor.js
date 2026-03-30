import Custom from './Custom.js';
import Shortcut from './Shortcut.js';

export default class CustomAnchor extends Custom {
    static get observedAttributes() {
        return ['href', 'shortcut', 'value', 'hint'];
    }

    constructor() {
        super();
        this._onClick = () => this.execute();
    }

    connectedCallback() {
        this.render();
        this.#updateShortcut(null, this.getAttr('shortcut'));

        this.addEventListener('click', this._onClick);
    }

    disconnectedCallback() {
        Shortcut.remove(this.getAttr('shortcut'));
        this.removeEventListener('click', this._onClick);
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
        const href = this.getAttr('href');

        if (href) {
            if (href.startsWith('popup:')) {
                const popupName = href.replaceAll('popup:', '').trim();
                openPopup(popupName);

                return;
            }

            const targetUrl = new URL(href, location.origin).href;
            if (location.href !== targetUrl) {
                location.href = href;
            }
        }
    }

    render() {
        const shortcutElement = this.querySelector('pre.shortcut');

        if (shortcutElement) {
            const shortcut = this.getAttr('shortcut');
            shortcutElement.textContent = shortcut;
        }
    }
}

customElements.define('custom-a', CustomAnchor);