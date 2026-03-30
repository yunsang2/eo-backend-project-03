import Custom from './Custom.js';

export default class CustomHint extends Custom {
    static get observedAttributes() {
        return ['title', 'type', 'shortcut', 'desc'];
    }

    connectedCallback() {
        this.hide();
    
        window.addEventListener('mouseover', (e) => {
            const target = e.target.closest('custom-a, custom-button');
            
            if (!target || target.getAttribute('hint') === 'off') return;

            const shortcut = target.getAttribute('shortcut');
            let value = target.getAttribute('value');
            if (!value) {
                const shortcutElement = target.querySelector('pre.shortcut');
                if (shortcutElement) {
                    value = target.textContent.replace(shortcutElement.textContent, '').trim();
                } else {
                    value = target.textContent.trim();
                }
            }

            const type = target.tagName === 'CUSTOM-A' ? 'Anchor' : 'Button';
            const desc = target.tagName === 'CUSTOM-A' ? target.getAttribute('href') : target.getAttribute('onclick');

            this.innerHTML = ''; 
            this.setAttribute('title', value);
            this.setAttribute('type', type);

            if (shortcut) this.setAttribute('shortcut', shortcut);
            else this.removeAttribute('shortcut');

            if (desc) this.setAttribute('desc', desc);
            else this.removeAttribute('desc');

            this.render();
            this.show(target);
        });

        window.addEventListener('mouseout', (e) => {
            if (e.target.closest('custom-a, custom-button')) {
                this.hide();
            }
        });
    }

    show(parent) {
        const parentRect = parent.getBoundingClientRect();
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        const hintWidth = this.offsetWidth;
        const hintHeight = this.offsetHeight;

        let top, left;
        const margin = 10;

        if (parentRect.top + parentRect.height / 2 < viewportHeight / 2) {
            top = parentRect.bottom + margin;
        } else {
            top = parentRect.top - hintHeight - margin;
        }

        if (parentRect.left + hintWidth > viewportWidth) {
            left = viewportWidth - hintWidth - margin;
        } else {
            left = parentRect.left + margin;
        }

        this.style.top = top + 'px';
        this.style.left = left + 'px';
        this.style.opacity = 1;
    }

    hide() {
        this.style.opacity = 0;
    }

    render() {
        const title = this.getAttr('title');
        const type = this.getAttr('type');
        const shortcut = this.getAttr('shortcut');
        const desc = this.getAttr('desc');

        if (title) {
            const p = document.createElement('p');

            const titleElement = document.createElement('h1');
            titleElement.textContent = title;
            p.appendChild(titleElement);

            if (type) {
                const typeElement = document.createElement('span');
                typeElement.classList.add('tag', 'type');
                typeElement.textContent = type;
                titleElement.appendChild(typeElement);
                p.appendChild(typeElement);
            }

            if (shortcut) {
                const shortcutElement = document.createElement('span');
                shortcutElement.classList.add('tag', 'shortcut');
                shortcutElement.textContent = shortcut;
                p.appendChild(shortcutElement);
            }

            this.appendChild(p);
        }

        if (desc) {
            const descElement = document.createElement('p');
            descElement.classList.add('desc');
            descElement.textContent = desc;
            
            this.appendChild(descElement);
        }
    }
}

customElements.define('custom-hint', CustomHint);

document.addEventListener('DOMContentLoaded', () => {
    if (!document.body.querySelector('custom-hint')) {
        const hintElement = document.createElement('custom-hint');
        document.body.appendChild(hintElement);
    }
});